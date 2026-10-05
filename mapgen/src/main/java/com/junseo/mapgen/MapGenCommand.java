package com.junseo.mapgen;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /mapgen create            도시 월드 만들기(또는 불러오기) + 공항으로 이동
 * /mapgen tp &lt;거점|구역&gt;   그곳으로 이동
 * /mapgen where             지금 있는 구역과 가까운 거점
 * /mapgen list              거점·구역 목록
 */
final class MapGenCommand implements BasicCommand {
    private final MapGenPlugin plugin;

    MapGenCommand(MapGenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "junseomapgen.admin";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(sender);
            case "tp" -> teleport(sender, args.length > 1 ? args[1] : null);
            case "where" -> where(sender);
            case "list" -> list(sender);
            default -> help(sender);
        }
    }

    private void help(CommandSender sender) {
        say(sender, NamedTextColor.GOLD, "준서 시티 도시 바탕 생성기");
        say(sender, NamedTextColor.YELLOW, "/mapgen create  도시 월드 만들기 (처음 한 번), 공항으로 이동. 기본 월드가 이미 도시면 공항으로 이동만");
        say(sender, NamedTextColor.YELLOW, "/mapgen tp <거점|구역>  그곳으로 이동 (예: plaza, yeouido)");
        say(sender, NamedTextColor.YELLOW, "/mapgen where  지금 있는 구역");
        say(sender, NamedTextColor.YELLOW, "/mapgen list  거점·구역 목록");
    }

    private void create(CommandSender sender) {
        say(sender, NamedTextColor.GRAY, "도시 월드를 준비하는 중... (처음에는 조금 걸려요)");
        World world = plugin.openCityWorld();
        say(sender, NamedTextColor.GREEN, "도시 월드 준비 완료: " + world.getName());
        say(sender, NamedTextColor.GRAY, "미리 생성: /chunky world " + world.getName()
                + " → /chunky shape rectangle → /chunky center 0 0 → /chunky radius " + plugin.chunkyRadius() + " → /chunky start");
        if (sender instanceof Player player) {
            player.teleportAsync(CityChunkGenerator.spawn(world, plugin.terrain()));
        }
    }

    private void teleport(CommandSender sender, String target) {
        if (!(sender instanceof Player player)) {
            say(sender, NamedTextColor.RED, "게임 안에서 써 주세요.");
            return;
        }
        if (target == null) {
            say(sender, NamedTextColor.RED, "/mapgen tp <거점|구역>  (목록: /mapgen list)");
            return;
        }
        CityTerrain terrain = plugin.terrain();
        double[] pos = null;
        for (Layout.Hub h : terrain.layout().hubs()) {
            if (h.id().equalsIgnoreCase(target) || h.name().startsWith(target)) {
                pos = new double[]{h.x() + 2, h.z()};
                break;
            }
        }
        if (pos == null) {
            pos = terrain.districtCenter(target);
        }
        if (pos == null) {
            say(sender, NamedTextColor.RED, "그런 거점이나 구역이 없어요: " + target);
            return;
        }
        World world = plugin.openCityWorld();
        int x = (int) Math.floor(pos[0]), z = (int) Math.floor(pos[1]);
        player.teleportAsync(new Location(world, x + 0.5, terrain.standY(x, z), z + 0.5));
        say(sender, NamedTextColor.GREEN, "이동: " + target + " (" + x + ", " + z + ")");
    }

    private void where(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            say(sender, NamedTextColor.RED, "게임 안에서 써 주세요.");
            return;
        }
        CityTerrain terrain = plugin.terrain();
        Location loc = player.getLocation();
        int x = loc.getBlockX(), z = loc.getBlockZ();
        String district = terrain.districtName(x, z);
        Layout.Hub nearest = null;
        double best = Double.MAX_VALUE;
        for (Layout.Hub h : terrain.layout().hubs()) {
            double d = Math.hypot(h.x() - x, h.z() - z);
            if (d < best) {
                best = d;
                nearest = h;
            }
        }
        say(sender, NamedTextColor.GOLD, "위치 (" + x + ", " + z + ") — " + (district == null ? "구역 밖" : district));
        if (nearest != null) {
            say(sender, NamedTextColor.GRAY, "가장 가까운 거점: " + nearest.name() + " (" + Math.round(best) + "m)");
        }
    }

    private void list(CommandSender sender) {
        CityTerrain terrain = plugin.terrain();
        say(sender, NamedTextColor.GOLD, "거점");
        for (Layout.Hub h : terrain.layout().hubs()) {
            say(sender, NamedTextColor.YELLOW, " " + h.id() + " — " + h.name() + " (" + Math.round(h.x()) + ", " + Math.round(h.z()) + ")");
        }
        say(sender, NamedTextColor.GOLD, "구역");
        for (Layout.District d : terrain.layout().districts()) {
            say(sender, NamedTextColor.YELLOW, " " + d.id() + " — " + d.name() + " (" + d.openPhase() + "차 오픈)");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            return filter(List.of("create", "tp", "where", "list"), args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            List<String> all = new ArrayList<>();
            plugin.terrain().layout().hubs().forEach(h -> all.add(h.id()));
            all.addAll(plugin.terrain().districtIds());
            return filter(all, args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    private static void say(CommandSender sender, NamedTextColor color, String text) {
        sender.sendMessage(Component.text(text, color));
    }
}
