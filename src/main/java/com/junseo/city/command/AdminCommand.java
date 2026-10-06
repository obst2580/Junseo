package com.junseo.city.command;

import com.junseo.city.JunseoCity;
import com.junseo.city.economy.Economy;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Job;
import com.junseo.city.logic.Money;
import com.junseo.city.npc.NpcType;
import com.junseo.city.place.Place;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import com.junseo.city.vehicle.CarType;
import com.junseo.city.weapon.GunType;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /시티관리 (= /cityadmin) — 관리자 전용 명령어.
 * 플레이어 기능은 모두 스마트폰·클릭으로 하고, 명령어는 관리자 설정용으로만 남깁니다.
 */
public final class AdminCommand implements BasicCommand {
    private static final List<String> SUBS = List.of("place", "npc", "info", "money", "job", "jail", "release",
            "give", "reload", "save");
    private static final List<String> ITEMS = List.of("phone", "idcard", "pistol", "shotgun", "rifle", "taser", "ammo",
            "handcuffs", "bandage", "key_police", "key_ambulance", "key_taxi", "key_delivery", "key_garbage", "key_cashvan",
            "car_compact", "car_sedan", "car_large", "car_suv", "car_sports");

    private final JunseoCity plugin;

    public AdminCommand(JunseoCity plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "junseocity.admin";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            help(sender);
            return;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "place" -> place(sender, rest);
            case "npc" -> npc(sender, rest);
            case "info" -> info(sender, rest);
            case "money" -> money(sender, rest);
            case "job" -> job(sender, rest);
            case "jail" -> jail(sender, rest);
            case "release" -> release(sender, rest);
            case "give" -> give(sender, rest);
            case "reload" -> {
                plugin.reloadSettings();
                Text.send(sender, "<green>config.yml 을 다시 불러왔어요. (database 설정은 재시작해야 적용)");
            }
            case "save" -> {
                plugin.characters().saveDirty();
                plugin.places().save();
                Text.send(sender, "<green>저장을 요청했어요.");
            }
            default -> help(sender);
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.mm("<gray>────── <red><bold>시티 관리자 명령어</bold></red> ──────"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 place set <종류> [이름]</yellow> <gray>장소 지정 (place list / place remove)"));
        sender.sendMessage(Text.mm("<gray>   서 있는 곳: spawn hospital jail jail_exit delivery_point"));
        sender.sendMessage(Text.mm("<gray>   바라보는 블록: atm store_safe bank_vault"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 npc <종류></yellow> <gray>NPC 세우기 (지울 때는 크리에이티브로 때리기)"));
        sender.sendMessage(Text.mm("<gray>   종류: convenience gunstore cardealer mineral jobcenter depot banker"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 info <닉네임></yellow> <gray>캐릭터 정보"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 money <닉네임> <set|add|take> <금액> [cash|bank]"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 job <닉네임> <citizen|police|ems|delivery|miner> [계급]</yellow> <gray>(공무직 채용)"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 jail <닉네임> <초>   release <닉네임>"));
        sender.sendMessage(Text.mm("<yellow>/시티관리 give <아이템> [개수] [닉네임]</yellow> <gray>" + String.join(" ", ITEMS)));
        sender.sendMessage(Text.mm("<yellow>/시티관리 reload</yellow> <gray>설정 다시 읽기   <yellow>save</yellow> <gray>저장"));
    }

    // ------------------------------------------------------------------ 장소 / NPC

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
                if (type == null || type == PlaceType.NPC) {
                    Text.send(sender, "<red>장소 종류를 적어 주세요: spawn hospital jail jail_exit delivery_point atm store_safe bank_vault");
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
        plugin.npcs().spawn(type, loc);
        player.teleportAsync(player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(-1.5)));
        Text.send(sender, "<green>" + type.displayName() + " NPC를 세웠어요. (폰 지도에도 표시돼요)");
    }

    // ------------------------------------------------------------------ 캐릭터

    private void info(CommandSender sender, String[] args) {
        Player target = args.length > 0 ? requireOnline(sender, args[0]) : null;
        CharacterData data = target == null ? null : plugin.characters().get(target);
        if (target == null) {
            return;
        }
        if (data == null) {
            Text.send(sender, "<gray>" + target.getName() + " 님은 아직 캐릭터를 만들지 않았어요.");
            return;
        }
        Text.send(sender, "<white>" + Text.esc(data.name()) + " <gray>(" + target.getName() + ") 주민번호 <white>" + data.citizenId()
                + "</white> 직업 <white>" + data.job().displayName() + " " + data.jobGrade() + "</white> 현금 <green>"
                + plugin.settings().money(data.cash()) + "</green> 은행 <aqua>" + plugin.settings().money(data.bank()));
    }

    private void money(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Text.send(sender, "<yellow>/시티관리 money <닉네임> <set|add|take> <금액> [cash|bank]");
            return;
        }
        Player target = requireOnline(sender, args[0]);
        CharacterData data = target == null ? null : requireCharacter(sender, target);
        if (data == null) {
            return;
        }
        long amount = args[2].equals("0") ? 0 : Money.parse(args[2]);
        if (amount < 0) {
            Text.send(sender, "<red>금액이 이상해요.");
            return;
        }
        boolean bank = args.length > 3 && args[3].equalsIgnoreCase("bank");
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
        plugin.characters().logMoney(data, next - current, bank ? "bank" : "cash", "관리자 조정", sender.getName());
        Text.send(sender, "<green>" + Text.esc(data.name()) + " " + (bank ? "은행" : "현금") + " → " + plugin.settings().money(next));
    }

    private void job(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "<yellow>/시티관리 job <닉네임> <citizen|police|ems|delivery|miner> [계급]");
            return;
        }
        Player target = requireOnline(sender, args[0]);
        if (target == null || requireCharacter(sender, target) == null) {
            return;
        }
        Job job = Job.parse(args[1]);
        if (job == null) {
            Text.send(sender, "<red>직업: citizen police ems delivery miner");
            return;
        }
        int grade = 0;
        if (args.length > 2) {
            try {
                grade = Math.max(0, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                Text.send(sender, "<red>계급은 숫자로 적어 주세요.");
                return;
            }
        }
        int finalGrade = grade;
        Sched.entity(target, () -> {
            plugin.jobs().setJob(target, job, finalGrade);
            Text.send(target, "<green>" + job.displayName() + "(으)로 채용됐어요!");
        });
        Text.send(sender, "<green>" + target.getName() + " → " + job.displayName() + " (계급 " + grade + ")");
    }

    private void jail(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Text.send(sender, "<yellow>/시티관리 jail <닉네임> <초>");
            return;
        }
        Player target = requireOnline(sender, args[0]);
        if (target == null || requireCharacter(sender, target) == null) {
            return;
        }
        try {
            int seconds = Math.max(1, Integer.parseInt(args[1]));
            Sched.entity(target, () -> plugin.jail().jail(target, seconds, "관리자에 의해 수감되었어요."));
            Text.send(sender, "<green>" + target.getName() + " 수감 " + seconds + "초");
        } catch (NumberFormatException e) {
            Text.send(sender, "<red>초를 숫자로 적어 주세요.");
        }
    }

    private void release(CommandSender sender, String[] args) {
        Player target = args.length > 0 ? requireOnline(sender, args[0]) : null;
        CharacterData data = target == null ? null : requireCharacter(sender, target);
        if (data == null) {
            return;
        }
        if (!data.isJailed()) {
            Text.send(sender, "<gray>감옥에 있지 않아요.");
            return;
        }
        Sched.entity(target, () -> plugin.jail().release(target));
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
        int count = amount;
        if (id.startsWith("car_")) {
            // 일반 차: 열쇠가 아니라 그 사람 차로 등록하고 가장 가까운 공영 차고에 넣음
            CarType type = CarType.byId(id.substring(4));
            if (type == null || !type.forSale()) {
                Text.send(sender, "<red>일반 차가 아니에요: " + Text.esc(id));
                return;
            }
            Sched.entity(target, () -> {
                var g = plugin.garages().nearest(target.getLocation(), Double.MAX_VALUE);
                String garage = g == null ? "공영 차고" : g.name();
                var r = plugin.vehicles().create(target.getUniqueId(), type, com.junseo.city.vehicle.model.CarModels.Paint.WHITE, garage);
                Text.send(target, "<green>" + r.label() + " 받았어요 (" + r.plate() + "). " + Text.esc(garage) + "에서 폰 → 차고로 꺼내세요.");
                Text.send(sender, "<green>" + target.getName() + "에게 " + r.label() + " 지급");
            });
            return;
        }
        Sched.entity(target, () -> {
            CharacterData data = plugin.characters().get(target);
            for (int i = 0; i < (id.equals("ammo") ? 1 : count); i++) {
                ItemStack stack = create(id, count, target, data);
                if (stack == null) {
                    Text.send(sender, "<red>줄 수 없는 아이템이에요: " + Text.esc(id));
                    return;
                }
                Economy.give(target, stack);
            }
            Text.send(sender, "<green>" + target.getName() + "에게 " + id + " 지급");
        });
    }

    private static ItemStack create(String id, int amount, Player owner, CharacterData data) {
        GunType gun = GunType.byId(id);
        if (gun != null) {
            return CustomItems.gun(gun);
        }
        if (id.startsWith("key_")) {
            CarType car = CarType.byId(id.substring(4));
            return car == null || car.forSale() ? null : CustomItems.carKey(car, owner.getUniqueId(), owner.getName());
        }
        return switch (id) {
            case "phone" -> CustomItems.phone();
            case "idcard" -> data == null ? null : CustomItems.idCard(data);
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
            Text.send(sender, "<red>" + Text.esc(name) + " 님은 접속해 있지 않아요. (마인크래프트 닉네임으로 적어 주세요)");
        }
        return target;
    }

    private CharacterData requireCharacter(CommandSender sender, Player target) {
        CharacterData data = plugin.characters().get(target);
        if (data == null) {
            Text.send(sender, "<gray>" + target.getName() + " 님은 아직 캐릭터를 만들지 않았어요.");
        }
        return data;
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            return filter(SUBS, args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "place" -> filter(List.of("set", "remove", "list"), args[1]);
                case "npc" -> filter(Arrays.stream(NpcType.values()).map(NpcType::id).toList(), args[1]);
                case "give" -> filter(ITEMS, args[1]);
                case "info", "money", "job", "jail", "release" -> filter(onlineNames(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "place" -> args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("list")
                        ? filter(Arrays.stream(PlaceType.values()).filter(t -> t != PlaceType.NPC).map(PlaceType::id).toList(), args[2])
                        : List.of();
                case "money" -> filter(List.of("set", "add", "take"), args[2]);
                case "job" -> filter(Arrays.stream(Job.values()).map(Job::key).toList(), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("give")) {
            return filter(onlineNames(), args[3]);
        }
        if (args.length == 5 && sub.equals("money")) {
            return filter(List.of("cash", "bank"), args[4]);
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
