package com.junseo.city.hud;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.logic.WantedRules;
import com.junseo.city.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 화면 오른쪽 사이드바 (현금, 은행, 직업, 수배). */
public final class HudService {
    private static final int MAX_LINES = 12;

    private final JunseoCity plugin;
    private final Map<UUID, Objective> boards = new HashMap<>();

    public HudService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public void show(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("city", Criteria.DUMMY,
                Text.mm("<gold><bold>" + Text.esc(plugin.settings().serverName)));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        player.setScoreboard(board);
        boards.put(player.getUniqueId(), objective);
        update(player);
    }

    public void remove(Player player) {
        if (boards.remove(player.getUniqueId()) != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(Player player) {
        Objective objective = boards.get(player.getUniqueId());
        if (objective == null) {
            return;
        }
        List<Component> lines = lines(player);
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = "line" + i;
            if (i < lines.size()) {
                Score score = objective.getScore(entry);
                score.setScore(MAX_LINES - i);
                score.customName(lines.get(i));
            } else {
                objective.getScoreboard().resetScores(entry);
            }
        }
    }

    private List<Component> lines(Player player) {
        PlayerData data = plugin.data().get(player);
        var s = plugin.settings();
        List<Component> lines = new ArrayList<>();
        lines.add(Text.mm("<dark_gray>──────────────"));
        lines.add(Text.mm("<gray>현금 <green>" + s.money(data.cash())));
        lines.add(Text.mm("<gray>은행 <aqua>" + s.money(data.bank())));
        lines.add(Text.mm("<gray>직업 <white>" + data.job().displayName()));
        if (data.wanted() > 0) {
            lines.add(Text.mm("<gray>수배 <red>" + WantedRules.stars(data.wanted())));
        } else {
            lines.add(Text.mm("<gray>수배 <dark_gray>" + WantedRules.stars(0)));
        }
        if (data.isJailed()) {
            lines.add(Text.mm("<gray>수감 <red>" + data.jailSeconds() + "초 남음"));
        }
        String mission = plugin.delivery().hudLine(player);
        if (mission != null) {
            lines.add(Text.mm(mission));
        }
        lines.add(Text.mm("<dark_gray>─────────────── "));
        lines.add(Text.mm("<yellow>/도움말"));
        return lines;
    }
}
