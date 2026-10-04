package com.junseo.city.command;

import com.junseo.city.JunseoCity;
import com.junseo.city.economy.Economy;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.Money;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.npc.NpcType;
import com.junseo.city.place.Place;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Text;
import com.junseo.city.vehicle.CarType;
import com.junseo.city.weapon.GunType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /시티관리 ... 관리자(OP) 전용. */
public final class AdminCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("place", "npc", "money", "job", "wanted", "jail", "release",
            "give", "reload", "save");
    private static final List<String> ITEMS = List.of("pistol", "shotgun", "rifle", "taser", "ammo", "handcuffs",
            "bandage", "key_compact", "key_sedan", "key_sports", "key_police");

    private final JunseoCity plugin;

    public AdminCommand(JunseoCity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "place" -> place(sender, rest);
            case "npc" -> npc(sender, rest);
            case "money" -> money(sender, rest);
            case "job" -> job(sender, rest);
            case "wanted" -> wanted(sender, rest);
            case "jail" -> jail(sender, rest);
            case "release" -> release(sender, rest);
            case "give" -> give(sender, rest);
            case "reload" -> {
                plugin.reloadSettings();
                Text.send(sender, "<green>config.yml 을 다시 불러왔어요.");
            }
            case "save" -> {
                plugin.data().saveAll();
                plugin.places().save();
                Text.send(sender, "<green>저장했어요.");
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.mm("<gray>────── <red><bold>시티 관리자 명령어</bold></red> ──────"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 place set <종류> [이름]</yellow> <gray>지금 위치(또는 바라보는 블록)를 장소로 지정"));
        sender.sendMessage(Text.mm("<gray>   서 있는 곳: spawn hospital jail jail_exit delivery_point"));
        sender.sendMessage(Text.mm("<gray>   바라보는 블록: atm store_safe bank_vault"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 place remove</yellow> <gray>가장 가까운 장소 삭제   <yellow>place list"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 npc <종류></yellow> <gray>내 자리에 NPC 세우기 (지울 때는 크리에이티브로 때리기)"));
        sender.sendMessage(Text.mm("<gray>   종류: convenience gunstore cardealer mineral jobcenter depot banker"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 money <이름> <set|add|take> <금액> [cash|bank]"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 job <이름> <citizen|police|delivery|miner>"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 wanted <이름> <0~5>   jail <이름> <초>   release <이름>"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 give <아이템> [개수] [이름]</yellow> <gray>" + String.join(" ", ITEMS)));
        sender.sendMessage(Text.mm("<yellow>/시티관리 reload</yellow> <gray>설정 다시 읽기   <yellow>save</yellow> <gray>저장"));
    }

    private void place(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "<yellow>/시티관리 place <set|remove|list>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                PlaceType type = args.length > 1 ? PlaceType.byId(args[1]) : null;
                if (type == null) {
                    Text.send(sender, "<red>장소 종류를 적어 주세요: " + placeIds());
                    return;
                }
                String name = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
                Location loc;
                if (type.block()) {
                    Block target = player.getTargetBlockExact(6);
                    if (target == null) {
                        Text.send(sender, "<red>장소로 쓸 블록을 바라보고 다시 입력하세요. (6칸 이내)");
                        return;
                    }
                    loc = target.getLocation();
                } else {
                    loc = player.getLocation();
                }
                Place place = plugin.places().add(type, loc, name);
                Text.send(sender, "<green>장소 지정 완료: <white>" + type.displayName() + "</white> #" + place.id()
                        + (name.isEmpty() ? "" : " (" + Text.esc(name) + ")"));
            }
            case "remove" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                Place removed = plugin.places().removeNearest(player.getLocation(), 8);
                Text.send(sender, removed == null ? "<red>8칸 안에 장소가 없어요."
                        : "<green>삭제: " + removed.type().displayName() + " #" + removed.id());
            }
            case "list" -> {
                PlaceType filter = args.length > 1 ? PlaceType.byId(args[1]) : null;
                List<Place> list = filter == null ? plugin.places().all() : plugin.places().of(filter);
                if (list.isEmpty()) {
                    Text.send(sender, "<gray>지정된 장소가 없어요.");
                }
                for (Place p : list) {
                    sender.sendMessage(Text.mm("<gray>#" + p.id() + " <white>" + p.type().id() + "</white> "
                            + Text.esc(p.label()) + " <dark_gray>(" + p.world() + " " + (int) p.x() + ", " + (int) p.y()
                            + ", " + (int) p.z() + ")"));
                }
                List<String> missing = new ArrayList<>();
                for (PlaceType t : List.of(PlaceType.SPAWN, PlaceType.HOSPITAL, PlaceType.JAIL, PlaceType.JAIL_EXIT)) {
                    if (plugin.places().of(t).isEmpty()) {
                        missing.add(t.id());
                    }
                }
                if (!missing.isEmpty()) {
                    Text.send(sender, "<yellow>아직 안 정한 필수 장소: " + String.join(", ", missing)
                            + " <gray>(없으면 월드 스폰을 써요)");
                }
            }
            default -> Text.send(sender, "<yellow>/시티관리 place <set|remove|list>");
        }
    }

    private static String placeIds() {
        return String.join(" ", Arrays.stream(PlaceType.values()).map(PlaceType::id).toList());
    }

    private void npc(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        NpcType type = args.length > 0 ? NpcType.byId(args[0]) : null;
        if (type == null) {
            Text.send(sender, "<red>NPC 종류: " + String.join(" ", Arrays.stream(NpcType.values()).map(NpcType::id).toList()));
            return;
        }
        Location loc = player.getLocation();
        loc.setPitch(0);
        loc.setYaw(loc.getYaw() + 180); // 나를 바라보게
        player.teleport(player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(-1.5)));
        plugin.npcs().spawn(type, loc);
        Text.send(sender, "<green>" + type.displayName() + " NPC를 세웠어요.");
    }

    private void money(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.send(sender, "<yellow>/시티관리 money <이름> <set|add|take> <금액> [cash|bank]");
            return;
        }
        Player target = requireOnline(sender, args[0]);
        if (target == null) {
            return;
        }
        long amount = args[2].equals("0") ? 0 : Money.parse(args[2]);
        if (amount < 0) {
            Text.send(sender, "<red>금액이 이상해요.");
            return;
        }
        boolean bank = args.length > 3 && args[3].equalsIgnoreCase("bank");
        PlayerData data = plugin.data().get(target);
        long current = bank ? data.bank() : data.cash();
        long next = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> amount;
            case "add" -> current + amount;
            case "take" -> Math.max(0, current - amount);
            default -> -1;
        };
        if (next < 0) {
            Text.send(sender, "<red>set / add / take 중 하나를 적어 주세요.");
            return;
        }
        if (bank) {
            data.setBank(next);
        } else {
            data.setCash(next);
        }
        Text.send(sender, "<green>" + target.getName() + " " + (bank ? "은행" : "현금") + " → " + plugin.settings().money(next));
    }

    private void job(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "<yellow>/시티관리 job <이름> <citizen|police|delivery|miner>");
            return;
        }
        Player target = requireOnline(sender, args[0]);
        Job job = Job.parse(args[1]);
        if (target == null) {
            return;
        }
        if (job == null) {
            Text.send(sender, "<red>직업: citizen police delivery miner");
            return;
        }
        plugin.jobs().setJob(target, job);
        Text.send(sender, "<green>" + target.getName() + " → " + job.displayName());
    }

    private void wanted(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "<yellow>/시티관리 wanted <이름> <0~5>");
            return;
        }
        Player target = requireOnline(sender, args[0]);
        if (target == null) {
            return;
        }
        int stars;
        try {
            stars = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            Text.send(sender, "<red>0~5 사이 숫자를 적어 주세요.");
            return;
        }
        plugin.wanted().set(target, stars);
        Text.send(sender, "<green>" + target.getName() + " 수배 → " + plugin.data().get(target).wanted() + "★");
    }

    private void jail(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "<yellow>/시티관리 jail <이름> <초>");
            return;
        }
        Player target = requireOnline(sender, args[0]);
        if (target == null) {
            return;
        }
        try {
            int seconds = Math.max(1, Integer.parseInt(args[1]));
            plugin.jail().jail(target, seconds, "관리자에 의해 수감되었어요.");
            Text.send(sender, "<green>" + target.getName() + " 수감 " + seconds + "초");
        } catch (NumberFormatException e) {
            Text.send(sender, "<red>초를 숫자로 적어 주세요.");
        }
    }

    private void release(CommandSender sender, String[] args) {
        Player target = args.length > 0 ? requireOnline(sender, args[0]) : null;
        if (target == null) {
            return;
        }
        if (!plugin.data().get(target).isJailed()) {
            Text.send(sender, "<gray>감옥에 있지 않아요.");
            return;
        }
        plugin.jail().release(target);
        Text.send(sender, "<green>" + target.getName() + " 석방");
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length == 0) {
            Text.send(sender, "<yellow>아이템: " + String.join(" ", ITEMS));
            return;
        }
        int amount = 1;
        if (args.length > 1) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                Text.send(sender, "<red>개수를 숫자로 적어 주세요.");
                return;
            }
        }
        Player target = args.length > 2 ? requireOnline(sender, args[2]) : requirePlayer(sender);
        if (target == null) {
            return;
        }
        String id = args[0].toLowerCase(Locale.ROOT);
        for (int i = 0; i < (id.equals("ammo") ? 1 : amount); i++) {
            ItemStack stack = create(id, amount, target);
            if (stack == null) {
                Text.send(sender, "<red>모르는 아이템이에요: " + Text.esc(id));
                return;
            }
            Economy.give(target, stack);
        }
        Text.send(sender, "<green>" + target.getName() + "에게 " + id + " 지급");
    }

    private static ItemStack create(String id, int amount, Player owner) {
        GunType gun = GunType.byId(id);
        if (gun != null) {
            return CustomItems.gun(gun);
        }
        if (id.startsWith("key_")) {
            CarType car = CarType.byId(id.substring(4));
            return car == null ? null : CustomItems.carKey(car, owner.getUniqueId(), owner.getName());
        }
        return switch (id) {
            case "ammo" -> CustomItems.ammo(amount);
            case "handcuffs" -> CustomItems.handcuffs();
            case "bandage" -> CustomItems.bandage();
            default -> null;
        };
    }

    private static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Text.send(sender, "<red>게임 안에서 써 주세요.");
        return null;
    }

    private static Player requireOnline(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            Text.send(sender, "<red>" + Text.esc(name) + " 님은 접속해 있지 않아요.");
        }
        return target;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "place" -> filter(List.of("set", "remove", "list"), args[1]);
                case "npc" -> filter(Arrays.stream(NpcType.values()).map(NpcType::id).toList(), args[1]);
                case "give" -> filter(ITEMS, args[1]);
                case "money", "job", "wanted", "jail", "release" -> null;
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "place" -> args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("list")
                        ? filter(Arrays.stream(PlaceType.values()).map(PlaceType::id).toList(), args[2]) : List.of();
                case "money" -> filter(List.of("set", "add", "take"), args[2]);
                case "job" -> filter(Arrays.stream(Job.values()).map(Job::key).toList(), args[2]);
                case "wanted" -> List.of("0", "1", "2", "3", "4", "5");
                default -> List.of();
            };
        }
        if (args.length == 5 && sub.equals("money")) {
            return filter(List.of("cash", "bank"), args[4]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(p)).toList();
    }
}
