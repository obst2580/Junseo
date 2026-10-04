package com.junseo.city.phone;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Compass;
import com.junseo.city.logic.Job;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 112(경찰) · 119(의료국) 신고 접수와 출동.
 * 시민 신고(폰)와 자동 신고(강도, 차량 절도 등)가 경찰·의료국 폰으로 알림이 갑니다.
 * 알림의 [응답]을 누르면 신고 위치로 길 안내가 시작됩니다.
 */
public final class DispatchService {

    public enum Line {
        POLICE("112", "경찰", Job.POLICE),
        EMS("119", "의료국", Job.EMS);

        final String number;
        final String label;
        final Job job;

        Line(String number, String label, Job job) {
            this.number = number;
            this.label = label;
            this.job = job;
        }

        public String number() {
            return number;
        }

        public String label() {
            return label;
        }
    }

    public record Call(int id, Line line, String reason, String reporter, UUID reporterId, Location location,
                       long time, boolean automatic) {
    }

    private static final long PLAYER_COOLDOWN_MS = 30_000;
    private static final long AUTO_COOLDOWN_MS = 30_000;
    private static final int KEEP = 50;

    private final JunseoCity plugin;
    private final AtomicInteger ids = new AtomicInteger();
    private final Deque<Call> recent = new ConcurrentLinkedDeque<>();
    private final Map<UUID, Long> lastPlayerCall = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAuto = new ConcurrentHashMap<>();

    public DispatchService(JunseoCity plugin) {
        this.plugin = plugin;
        plugin.ui().onStaticAction("dispatch", (player, rest) -> {
            try {
                respond(player, Integer.parseInt(rest));
            } catch (NumberFormatException ignored) {
                // 잘못된 버튼
            }
        });
    }

    /** 지금 접속해 있는 그 직업 사람 수. (2단계에서 「출근 중」으로 바뀝니다) */
    public int onDuty(Job job) {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            CharacterData data = plugin.characters().get(p);
            if (data != null && data.job() == job) {
                n++;
            }
        }
        return n;
    }

    public int onDuty(Line line) {
        return onDuty(line.job);
    }

    /** 시민이 폰으로 신고. 너무 자주 하면 거절하고 -1. 받은 사람 수를 돌려줍니다. */
    public int reportByPlayer(Player reporter, Line line, String reason) {
        long now = System.currentTimeMillis();
        Long last = lastPlayerCall.get(reporter.getUniqueId());
        if (last != null && now - last < PLAYER_COOLDOWN_MS) {
            return -1;
        }
        lastPlayerCall.put(reporter.getUniqueId(), now);
        CharacterData data = plugin.characters().get(reporter);
        String name = data == null ? reporter.getName() : data.name();
        return send(new Call(ids.incrementAndGet(), line, reason, name, reporter.getUniqueId(),
                reporter.getLocation(), now, false));
    }

    /** 자동 신고 (강도, 차량 절도 등). 같은 동네에서 같은 종류는 30초에 한 번만. */
    public void automatic(Line line, String type, String reason, Location location) {
        long now = System.currentTimeMillis();
        String key = type + ":" + location.getWorld().getName() + ":" + (location.getBlockX() >> 6) + ":" + (location.getBlockZ() >> 6);
        Long last = lastAuto.get(key);
        if (last != null && now - last < AUTO_COOLDOWN_MS) {
            return;
        }
        lastAuto.put(key, now);
        send(new Call(ids.incrementAndGet(), line, reason, "자동 신고", null, location.clone(), now, true));
    }

    private int send(Call call) {
        recent.addFirst(call);
        while (recent.size() > KEEP) {
            recent.pollLast();
        }
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            CharacterData data = plugin.characters().get(p);
            if (data == null || data.job() != call.line().job) {
                continue;
            }
            count++;
            Sched.entity(p, () -> notifyResponder(p, call));
        }
        return count;
    }

    private void notifyResponder(Player p, Call call) {
        Location loc = p.getLocation();
        String where;
        if (loc.getWorld() == call.location().getWorld()) {
            double dx = call.location().getX() - loc.getX();
            double dz = call.location().getZ() - loc.getZ();
            where = Math.round(Math.sqrt(dx * dx + dz * dz)) + "m " + Compass.arrow(loc.getYaw(), dx, dz);
        } else {
            where = "다른 지역";
        }
        Component button = Text.mm("<green><bold>[응답]")
                .clickEvent(ClickEvent.custom(Key.key("junseocity", "dispatch/" + call.id()), null))
                .hoverEvent(HoverEvent.showText(Text.mm("<gray>클릭하면 신고 위치로 길 안내")));
        p.sendMessage(Text.mm("<red><bold>[" + call.line().number() + " #" + call.id() + "]</bold></red> <white>"
                + Text.esc(call.reason()) + " <gray>· " + Text.esc(call.reporter()) + " · " + where + " ").append(button));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, call.line() == Line.POLICE ? 1.5f : 1.0f);
    }

    public void respond(Player responder, int id) {
        Call call = find(id);
        if (call == null) {
            Text.send(responder, "<gray>이미 끝난 신고예요.");
            return;
        }
        CharacterData data = plugin.characters().get(responder);
        if (data == null || data.job() != call.line().job) {
            Text.send(responder, "<red>" + call.line().label() + "만 응답할 수 있어요.");
            return;
        }
        plugin.gps().start(responder, call.location(), call.line().number() + " #" + call.id() + " " + call.reason());
        Text.send(responder, "<green>출동! 보스바의 화살표를 따라가세요.");
        if (call.reporterId() != null) {
            Player reporter = Bukkit.getPlayer(call.reporterId());
            if (reporter != null) {
                Sched.entity(reporter, () -> Text.send(reporter, "<aqua>" + call.line().label() + "이(가) 출동했어요! 조금만 기다려 주세요."));
            }
        }
    }

    public Call find(int id) {
        for (Call call : recent) {
            if (call.id() == id) {
                return call;
            }
        }
        return null;
    }

    public List<Call> recent(Line line, int limit) {
        List<Call> out = new ArrayList<>();
        for (Call call : recent) {
            if (call.line() == line) {
                out.add(call);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    public void forget(UUID uuid) {
        lastPlayerCall.remove(uuid);
    }
}
