package com.junseo.city.command;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.logic.WantedRules;
import com.junseo.city.shop.JobMenu;
import com.junseo.city.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /직업, /택배, /수배자, /112, /차, /도움말 */
public final class PlayerCommands implements TabExecutor {
    private final JunseoCity plugin;

    public PlayerCommands(JunseoCity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("cityhelp")) {
            help(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("게임 안에서만 쓸 수 있어요.");
            return true;
        }
        switch (command.getName()) {
            case "job" -> job(player);
            case "delivery" -> delivery(player, args);
            case "wanted" -> wanted(player);
            case "call112" -> call112(player, args);
            case "car" -> car(player, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void job(Player player) {
        if (!plugin.settings().jobChangeAnywhere && !player.hasPermission("junseocity.admin")) {
            Text.send(player, "<yellow>직업은 직업 소개소 NPC에서 바꿀 수 있어요. 지금 직업: <white>"
                    + plugin.data().get(player).job().displayName());
            return;
        }
        new JobMenu(plugin).open(player);
    }

    private void delivery(Player player, String[] args) {
        String sub = args.length == 0 ? "start" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("cancel") || sub.equals("취소")) {
            if (plugin.delivery().hasMission(player)) {
                plugin.delivery().cancel(player, "<yellow>배달을 취소했어요.");
            } else {
                Text.send(player, "<gray>진행 중인 배달이 없어요.");
            }
            return;
        }
        plugin.delivery().start(player);
    }

    private void wanted(Player player) {
        boolean any = false;
        Location me = player.getLocation();
        for (Player other : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.data().get(other);
            if (data.wanted() <= 0) {
                continue;
            }
            if (!any) {
                player.sendMessage(Text.mm("<gray>───── <red><bold>수배자 목록</bold></red> ─────"));
                any = true;
            }
            Location loc = other.getLocation();
            String where = loc.getWorld() == me.getWorld()
                    ? Math.round(loc.distance(me)) + "m (" + loc.getBlockX() + ", " + loc.getBlockZ() + ")"
                    : "다른 월드";
            player.sendMessage(Text.mm("  <white>" + other.getName() + " <red>" + WantedRules.stars(data.wanted())
                    + " <gray>" + where));
        }
        if (!any) {
            Text.send(player, "<green>지금은 수배자가 없어요. 평화로운 시티!");
        }
    }

    private void call112(Player player, String[] args) {
        String message = args.length == 0 ? "도와주세요!" : String.join(" ", args);
        Location loc = player.getLocation();
        int police = 0;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (plugin.data().get(other).job() == Job.POLICE) {
                police++;
                Text.send(other, "<aqua>[112 신고] <white>" + player.getName() + "</white>: " + Text.esc(message)
                        + " <gray>(위치 " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
            }
        }
        if (police == 0) {
            Text.send(player, "<yellow>지금 근무 중인 경찰이 없어요.");
        } else {
            Text.send(player, "<green>경찰 " + police + "명에게 신고가 접수됐어요.");
        }
    }

    private void car(Player player, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("recall") || args[0].equals("회수"))) {
            int count = plugin.cars().countOwned(player.getUniqueId());
            plugin.cars().despawnOwned(player.getUniqueId(), null);
            Text.send(player, count > 0 ? "<green>내 차 " + count + "대를 차고에 넣었어요." : "<gray>꺼내 놓은 차가 없어요.");
            return;
        }
        player.sendMessage(Text.mm("<gray>───── <green><bold>자동차</bold></green> ─────"));
        player.sendMessage(Text.mm("  <white>자동차 대리점</white>에서 차를 사면 <white>열쇠</white>를 받아요."));
        player.sendMessage(Text.mm("  열쇠를 들고 <yellow>땅 우클릭</yellow> = 차 꺼내기, <yellow>웅크리고 우클릭</yellow> = 차 넣기"));
        player.sendMessage(Text.mm("  <yellow>차 우클릭</yellow> = 타기 · <yellow>W/S</yellow> 가속/후진 · <yellow>A/D</yellow> 핸들"));
        player.sendMessage(Text.mm("  <yellow>스페이스</yellow> 브레이크 · <yellow>Ctrl</yellow> 경적 · <yellow>Shift</yellow> 내리기 · <yellow>F5</yellow> 시점"));
        player.sendMessage(Text.mm("  <gray>남의 차를 타면 차량 절도! · /차 회수 = 내 차 모두 넣기"));
    }

    private void help(CommandSender sender) {
        String name = Text.esc(plugin.settings().serverName);
        sender.sendMessage(Text.mm("<gray>────────── <gold><bold>" + name + " 도움말</bold></gold> ──────────"));
        sender.sendMessage(Text.mm("<yellow>/돈</yellow> <gray>현금·은행 확인   <yellow>/은행</yellow> <gray>ATM 메뉴   <yellow>/송금 이름 금액"));
        sender.sendMessage(Text.mm("<yellow>/직업</yellow> <gray>직업 고르기 (시민·경찰·택배기사·광부)"));
        sender.sendMessage(Text.mm("<yellow>/택배</yellow> <gray>택배 배달 시작 (택배기사)   <yellow>/택배 취소"));
        sender.sendMessage(Text.mm("<yellow>/수배자</yellow> <gray>수배자 목록   <yellow>/112 내용</yellow> <gray>경찰에 신고"));
        sender.sendMessage(Text.mm("<yellow>/차</yellow> <gray>자동차 조작법"));
        sender.sendMessage(Text.mm("<white>총</white><gray>: 우클릭 발사, F 재장전 · <white>붕대</white><gray>: 우클릭 회복"));
        sender.sendMessage(Text.mm("<white>강도</white><gray>: 총을 들고 금고 우클릭 → 버티면 돈! (수배가 올라요)"));
        sender.sendMessage(Text.mm("<white>수배</white><gray>: 범죄를 하면 ★, 경찰을 따돌리고 숨으면 줄어요. 잡히면 감옥!"));
        sender.sendMessage(Text.mm("<gray>NPC: 편의점 · 총포상 · 자동차 대리점 · 광물 거래소 · 직업 소개소 · 택배 물류센터 · 은행원"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return switch (command.getName()) {
                case "delivery" -> List.of("시작", "취소");
                case "car" -> List.of("회수");
                default -> List.of();
            };
        }
        return List.of();
    }
}
