package com.junseo.city.hud;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 화면 오른쪽 상태창 (이름, 현금, 은행, 직업). 리소스팩 HUD 가 생기면 바뀔 예정. */
public final class HudService {
    private static final int MAX_LINES = 12;

    private final JunseoCity plugin;
    private final Map<UUID, Objective> boards = new ConcurrentHashMap<>();

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

    public void forget(UUID uuid) {
        boards.remove(uuid);
    }

    /** 플레이어 스레드에서 1초마다 호출. */
    public void update(Player player) {
        Objective objective = boards.get(player.getUniqueId());
        CharacterData data = plugin.characters().get(player);
        if (objective == null || data == null) {
            return;
        }
        List<Component> lines = lines(player, data);
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

    private List<Component> lines(Player player, CharacterData data) {
        var s = plugin.settings();
        List<Component> lines = new ArrayList<>();
        lines.add(Text.mm("<dark_gray>──────────────"));
        lines.add(Text.mm("<white>" + Text.esc(data.name())));
        lines.add(Text.mm("<gray>현금 <green>" + s.money(data.cash())));
        lines.add(Text.mm("<gray>은행 <aqua>" + s.money(data.bank())));
        lines.add(Text.mm("<gray>직업 <white>" + data.job().displayName()));
        if (data.isJailed()) {
            lines.add(Text.mm("<gray>수감 <red>" + data.jailSeconds() + "초 남음"));
        }
        String mission = plugin.delivery().hudLine(player);
        if (mission != null) {
            lines.add(Text.mm(mission));
        }
        lines.add(Text.mm("<dark_gray>─────────────── "));
        lines.add(Text.mm("<yellow>G</yellow> <gray>스마트폰"));
        return lines;
    }
}
