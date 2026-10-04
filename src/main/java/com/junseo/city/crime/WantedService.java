package com.junseo.city.crime;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.Crime;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.logic.WantedRules;
import com.junseo.city.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 수배 별 (GTA 의 Wanted Level).
 * 범죄를 저지르면 별이 오르고, 경찰(AI 또는 경찰 플레이어)에게서 멀리 도망쳐 숨어 있으면 별이 줄어듭니다.
 */
public final class WantedService {
    /** 경찰이 이 거리 안에 있으면 "들킨 상태"라서 별이 줄지 않습니다. */
    public static final double SIGHT_RANGE = 40;
    private static final long ASSAULT_COOLDOWN_MS = 10_000;

    private final JunseoCity plugin;
    private final Map<UUID, Long> lastAssault = new HashMap<>();
    private final Map<UUID, Long> lastCrime = new HashMap<>();

    public WantedService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public void commit(Player criminal, Crime crime) {
        PlayerData data = plugin.data().get(criminal);
        if (data.isJailed()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (crime == Crime.ASSAULT || crime == Crime.COP_ASSAULT) {
            Long last = lastAssault.get(criminal.getUniqueId());
            if (last != null && now - last < ASSAULT_COOLDOWN_MS) {
                data.setWanted(data.wanted(), now);
                return;
            }
            lastAssault.put(criminal.getUniqueId(), now);
        }
        lastCrime.put(criminal.getUniqueId(), now);
        int before = data.wanted();
        int after = WantedRules.add(before, crime.stars());
        data.setWanted(after, now);
        criminal.sendActionBar(Text.mm("<red><bold>" + crime.displayName() + "!</bold> 수배 " + WantedRules.stars(after)));
        if (after > before) {
            Text.send(criminal, "<red>범죄: " + crime.displayName() + " → 수배 " + WantedRules.stars(after)
                    + " <gray>(경찰을 따돌리고 숨으면 줄어들어요)");
            criminal.playSound(criminal.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
            alertPolice(criminal, "<red>[수배] " + criminal.getName() + " - " + crime.displayName() + " "
                    + WantedRules.stars(after));
        }
        updateGlow(criminal);
    }

    public void set(Player player, int stars) {
        PlayerData data = plugin.data().get(player);
        data.setWanted(stars, System.currentTimeMillis());
        if (data.wanted() == 0) {
            plugin.police().dismiss(player);
        }
        updateGlow(player);
    }

    public void clear(Player player) {
        set(player, 0);
    }

    public void updateGlow(Player player) {
        PlayerData data = plugin.data().get(player);
        player.setGlowing(data.wanted() > 0 && data.wanted() >= plugin.settings().glowAt);
    }

    /** 최근 10초 안에 범죄를 저질렀는지. */
    public boolean recentlyCommitted(Player player) {
        Long last = lastCrime.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < 10_000;
    }

    /** 근처에 경찰(AI 또는 경찰 플레이어)이 있는지. */
    public boolean isSeen(Player player) {
        if (plugin.police().hasCopNear(player, SIGHT_RANGE)) {
            return true;
        }
        Location loc = player.getLocation();
        for (Player other : player.getWorld().getPlayers()) {
            if (other != player && plugin.data().get(other).job() == Job.POLICE
                    && other.getLocation().distanceSquared(loc) <= SIGHT_RANGE * SIGHT_RANGE) {
                return true;
            }
        }
        return false;
    }

    /** 1초마다: 별 감소, AI 경찰 관리. */
    public void tickSecond() {
        long now = System.currentTimeMillis();
        long decayMs = plugin.settings().wantedDecaySeconds * 1000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.data().get(player);
            int stars = data.wanted();
            if (stars <= 0 || data.isJailed() || player.isDead()) {
                plugin.police().dismiss(player);
                continue;
            }
            boolean seen = isSeen(player) || recentlyCommitted(player);
            plugin.police().update(player, stars, seen);
            if (seen) {
                data.setWanted(stars, now);
                continue;
            }
            if (WantedRules.shouldDecay(stars, now - data.wantedChangedAt(), decayMs)) {
                data.setWanted(stars - 1, now);
                if (data.wanted() == 0) {
                    Text.send(player, "<green>경찰을 완전히 따돌렸어요! 수배 해제.");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.8f);
                    plugin.police().dismiss(player);
                } else {
                    Text.send(player, "<yellow>수배가 줄었어요: " + WantedRules.stars(data.wanted()));
                }
                updateGlow(player);
            } else {
                long left = (decayMs - (now - data.wantedChangedAt())) / 1000;
                player.sendActionBar(Text.mm("<yellow>숨어 있는 중... <gray>" + left + "초 뒤 수배 감소 "
                        + "<red>" + WantedRules.stars(stars)));
            }
        }
    }

    public void alertPolice(Player about, String message) {
        Location loc = about.getLocation();
        String where = " <gray>(위치 " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (plugin.data().get(other).job() == Job.POLICE) {
                Text.send(other, message + where);
                other.playSound(other.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
            }
        }
    }

    public void forget(Player player) {
        lastAssault.remove(player.getUniqueId());
        lastCrime.remove(player.getUniqueId());
    }
}
