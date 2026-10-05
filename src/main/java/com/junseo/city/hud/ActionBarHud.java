package com.junseo.city.hud;

import com.junseo.city.map.GlyphWriter;
import com.junseo.city.map.MapGlyphs;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 화면 아래 액션바 한 줄을 여러 기능이 같이 쓰게 합니다.
 * <ul>
 *   <li>미니맵 (리소스팩이 있을 때): 핫바 옆에 늘 떠 있음</li>
 *   <li>메시지: 잠깐 보이는 알림 ("장전 중..." 등)</li>
 *   <li>상태: 계속 갱신되는 줄 (운전 중 속도 등). 메시지가 있으면 메시지가 먼저</li>
 * </ul>
 * 액션바는 3초 뒤 사라지므로, 미니맵이 있으면 1.5초마다 다시 보냅니다.
 * 기능 코드는 player.sendActionBar 대신 여기를 씁니다.
 */
public final class ActionBarHud {
    private static final Key MAP_FONT = Key.key(MapGlyphs.FONT);
    private static final long MESSAGE_MS = 3000;
    private static final long STATUS_MS = 700;
    private static final long KEEPALIVE_MS = 1500;

    private record Line(Component text, float width, long untilMs) {
        boolean active(long now) {
            return now < untilMs;
        }
    }

    private static final class State {
        volatile String radar;
        volatile Line message;
        volatile Line status;
        volatile long lastSentMs;
        volatile Object lastSent;
    }

    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private volatile HudLayout.Side side = HudLayout.Side.LEFT;

    public void side(HudLayout.Side side) {
        this.side = side;
    }

    /** 잠깐 보이는 알림 (3초) */
    public void message(Player player, Component text) {
        message(player, text, MESSAGE_MS);
    }

    public void message(Player player, Component text, long millis) {
        State s = state(player);
        s.message = new Line(text, width(text), System.currentTimeMillis() + millis);
        send(player, s, true);
    }

    /** 계속 갱신하는 줄 (부르는 쪽이 0.7초 안에 다시 불러야 계속 보임) */
    public void status(Player player, Component text) {
        State s = state(player);
        s.status = new Line(text, width(text), System.currentTimeMillis() + STATUS_MS);
        send(player, s, false);
    }

    /** 미니맵 글자열 (null 이면 미니맵 끔) */
    public void radar(Player player, String radar) {
        State s = state(player);
        boolean changed = radar == null ? s.radar != null : !radar.equals(s.radar);
        s.radar = radar;
        if (changed) {
            send(player, s, false);
        }
    }

    public boolean hasRadar(Player player) {
        State s = states.get(player.getUniqueId());
        return s != null && s.radar != null;
    }

    /** 자주(0.1초마다) 불러 주세요: 메시지가 끝났거나 미니맵이 사라지기 전에 다시 보냄 */
    public void tick(Player player) {
        State s = states.get(player.getUniqueId());
        if (s != null) {
            send(player, s, false);
        }
    }

    public void forget(UUID uuid) {
        states.remove(uuid);
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), u -> new State());
    }

    private void send(Player player, State s, boolean force) {
        long now = System.currentTimeMillis();
        Line message = s.message, status = s.status;
        Line line = message != null && message.active(now) ? message : status != null && status.active(now) ? status : null;
        String radar = s.radar;
        if (radar == null) {
            // 리소스팩이 없으면 예전처럼 글자만
            boolean keepalive = line == status && now - s.lastSentMs >= KEEPALIVE_MS;
            if (line != null && (force || keepalive || !line.text().equals(s.lastSent))) {
                s.lastSent = line.text();
                s.lastSentMs = now;
                player.sendActionBar(line.text());
            }
            return;
        }
        Object key = java.util.Arrays.asList(radar, line == null ? null : line.text());
        if (!force && key.equals(s.lastSent) && now - s.lastSentMs < KEEPALIVE_MS) {
            return;
        }
        s.lastSent = key;
        s.lastSentMs = now;
        player.sendActionBar(compose(radar, line == null ? null : line.text(), line == null ? 0 : line.width(), side));
    }

    /**
     * 미니맵 + 메시지 한 줄.
     * 액션바는 (글자열 폭 / 2) 만큼 왼쪽에서 시작하므로, 미니맵 앞에 그만큼 띄우기를 넣어 늘 같은 자리에 오게 합니다.
     * 미니맵 부분의 폭은 0 이라서 메시지는 원래처럼 가운데에 나옵니다.
     */
    static Component compose(String radar, Component text, float textWidth, HudLayout.Side side) {
        int lead = HudLayout.radarLead(textWidth, side);
        GlyphWriter before = new GlyphWriter();
        before.move(lead);
        GlyphWriter after = new GlyphWriter();
        after.move(-lead);
        Component block = Component.text(before + radar + after)
                .font(MAP_FONT)
                .shadowColor(ShadowColor.none());
        return text == null ? block : Component.textOfChildren(block, text);
    }

    /** 기본 글꼴로 쓴 글자열의 폭 (굵게 반영) */
    static float width(Component component) {
        return width(component, false);
    }

    private static float width(Component c, boolean parentBold) {
        TextDecoration.State state = c.style().decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.TRUE || (state == TextDecoration.State.NOT_SET && parentBold);
        float w;
        if (c instanceof TextComponent t) {
            w = TextWidth.of(t.content(), bold);
        } else {
            w = TextWidth.of(PlainTextComponentSerializer.plainText().serialize(c.children(java.util.List.of())), bold);
        }
        for (Component child : c.children()) {
            w += width(child, bold);
        }
        return w;
    }
}
