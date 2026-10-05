package com.junseo.city.phone;

import com.junseo.city.JunseoCity;
import com.junseo.city.data.CharacterRepository;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Compass;
import com.junseo.city.logic.Job;
import com.junseo.city.npc.NpcType;
import com.junseo.city.place.Place;
import com.junseo.city.place.PlaceType;
import com.junseo.city.ui.AmountPicker;
import com.junseo.city.ui.Screen;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import com.junseo.city.vehicle.Car;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * 스마트폰. G키(바닐라 「빠른 동작」) 또는 폰 아이템 우클릭으로 열고, 모든 기능을 클릭으로 씁니다.
 * G키 홈 화면은 데이터팩에 들어 있고, 그 버튼(junseocity:app/이름)이 여기로 들어옵니다.
 */
public final class PhoneService implements Listener {
    private static final double TRANSFER_NEARBY = 30;

    private final JunseoCity plugin;
    private final AmountPicker picker;

    public PhoneService(JunseoCity plugin) {
        this.plugin = plugin;
        this.picker = new AmountPicker(plugin.ui(), amount -> plugin.settings().money(amount));
        plugin.ui().onStaticAction("app", this::openApp);
        plugin.ui().onStaticAction("admintp", this::adminTeleport);
    }

    // ------------------------------------------------------------------ 열기

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !CustomItems.is(event.getPlayer().getInventory().getItemInMainHand(), CustomItems.PHONE)) {
            return;
        }
        event.setCancelled(true);
        openHome(event.getPlayer());
    }

    /** 폰을 쓸 수 있는 상태인지 확인하고, 아니면 이유를 알려줍니다. */
    private CharacterData usable(Player player) {
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            if (plugin.creation().isCreating(player)) {
                plugin.creation().resume(player);
            }
            return null;
        }
        if (!CustomItems.has(player, CustomItems.PHONE)) {
            Text.send(player, "<red>스마트폰이 없어요. 편의점에서 다시 살 수 있어요.");
            return null;
        }
        return data;
    }

    public void openHome(Player player) {
        if (usable(player) == null) {
            return;
        }
        Screen s = new Screen("<gold><bold>스마트폰").columns(3).buttonWidth(90);
        s.line("<gray>앱을 눌러 실행하세요");
        s.button("<aqua>은행", "잔액 · 송금 · 거래내역", () -> openApp(player, "bank"));
        s.button("<white>내 정보", "이름 · 주민번호 · 직업", () -> openApp(player, "profile"));
        s.button("<red>긴급 신고", "112 경찰 · 119 구조", () -> openApp(player, "emergency"));
        s.button("<yellow>직업", "내 직업 · 직업 바꾸기", () -> openApp(player, "job"));
        s.button("<green>알바", "택배 배달 미션", () -> openApp(player, "parttime"));
        s.button("<gray>차고", "내 차 위치 · 회수", () -> openApp(player, "garage"));
        s.button("<dark_aqua>지도", "장소로 길 안내", () -> openApp(player, "map"));
        s.button("<light_purple>관리자 호출", "신고 · 도움 요청", () -> openApp(player, "admin"));
        plugin.ui().show(player, s);
    }

    private void openApp(Player player, String app) {
        CharacterData data = usable(player);
        if (data == null) {
            return;
        }
        switch (app) {
            case "bank" -> bank(player, null);
            case "profile" -> profile(player);
            case "emergency" -> emergency(player);
            case "job" -> job(player, null);
            case "parttime" -> partTime(player);
            case "garage" -> garage(player);
            case "map" -> map(player);
            case "admin" -> adminCall(player);
            default -> openHome(player);
        }
    }

    private Screen app(String title) {
        return new Screen(title).columns(2).buttonWidth(130);
    }

    private void home(Screen s, Player player) {
        s.exit("◀ 홈", () -> openHome(player));
    }

    private String money(long amount) {
        return plugin.settings().money(amount);
    }

    // ------------------------------------------------------------------ 은행

    public void bank(Player player, String notice) {
        CharacterData me = plugin.characters().get(player);
        if (me == null) {
            return;
        }
        boolean atm = plugin.economy().canUseBank(player);
        Screen s = app("<aqua><bold>은행");
        s.line("<white>" + Text.esc(me.name()) + " <gray>님의 계좌");
        s.line("<gray>현금 <green>" + money(me.cash()) + "</green>   은행 <aqua>" + money(me.bank()));
        if (notice != null) {
            s.line(notice);
        }
        s.button("<aqua>송금", "근처 사람에게 은행 송금", () -> transferPick(player));
        s.button("<gray>거래내역", () -> history(player));
        if (atm) {
            s.button("<green>입금 (현금→은행)", () -> picker.open(player, "<green>입금", "현금을 은행에 넣어요",
                    me.cash(), amount -> bank(player, me.deposit(amount)
                            ? "<green>" + money(amount) + " 입금 완료!" : "<red>현금이 부족해요."),
                    () -> bank(player, null)));
            s.button("<yellow>출금 (은행→현금)", () -> picker.open(player, "<yellow>출금", "은행에서 현금을 꺼내요",
                    me.bank(), amount -> bank(player, me.withdraw(amount)
                            ? "<green>" + money(amount) + " 출금 완료!" : "<red>잔액이 부족해요."),
                    () -> bank(player, null)));
        } else {
            s.line("<dark_gray>입금·출금은 ATM이나 은행원 근처에서 할 수 있어요.");
        }
        home(s, player);
        plugin.ui().show(player, s);
    }

    private void transferPick(Player player) {
        Screen s = app("<aqua><bold>송금 · 받는 사람").columns(2);
        List<Player> nearby = new ArrayList<>();
        for (Entity e : player.getNearbyEntities(TRANSFER_NEARBY, TRANSFER_NEARBY, TRANSFER_NEARBY)) {
            if (e instanceof Player p && plugin.characters().get(p) != null) {
                nearby.add(p);
            }
        }
        nearby.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(player.getLocation())));
        if (nearby.isEmpty()) {
            s.line("<gray>근처(" + (int) TRANSFER_NEARBY + "칸)에 받을 사람이 없어요.");
            s.line("<dark_gray>멀리 있는 사람에게 보내기는 연락처 기능과 함께 추가될 예정이에요.");
        } else {
            s.line("<gray>받을 사람을 고르세요");
        }
        for (Player target : nearby.subList(0, Math.min(12, nearby.size()))) {
            CharacterData data = plugin.characters().get(target);
            if (data == null) {
                continue;
            }
            s.button("<white>" + Text.esc(data.name()), () -> transferAmount(player, target));
        }
        s.exit("◀ 은행", () -> bank(player, null));
        plugin.ui().show(player, s);
    }

    private void transferAmount(Player player, Player target) {
        CharacterData me = plugin.characters().get(player);
        CharacterData them = plugin.characters().get(target);
        if (me == null || them == null || !target.isOnline()) {
            bank(player, "<red>받는 사람이 떠났어요.");
            return;
        }
        picker.open(player, "<aqua>송금 → " + Text.esc(them.name()), "은행 잔액에서 보내요", me.bank(),
                amount -> transferConfirm(player, target, amount), () -> transferPick(player));
    }

    private void transferConfirm(Player player, Player target, long amount) {
        CharacterData them = plugin.characters().get(target);
        if (them == null) {
            bank(player, "<red>받는 사람이 떠났어요.");
            return;
        }
        Screen s = app("<aqua><bold>송금 확인");
        s.line("<white>" + Text.esc(them.name()) + "</white><gray> 님에게 <gold><bold>" + money(amount) + "</bold></gold> 을 보낼까요?");
        s.button("<green><bold>보내기", () -> transfer(player, target, amount));
        s.button("<red>취소", () -> bank(player, null));
        s.exit("◀ 금액 다시", () -> transferAmount(player, target));
        plugin.ui().show(player, s);
    }

    private void transfer(Player player, Player target, long amount) {
        CharacterData me = plugin.characters().get(player);
        CharacterData them = plugin.characters().get(target);
        if (me == null || them == null || !target.isOnline()) {
            bank(player, "<red>받는 사람이 떠났어요.");
            return;
        }
        if (!CharacterData.transferBank(me, them, amount)) {
            bank(player, "<red>은행 잔액이 부족해요.");
            return;
        }
        plugin.characters().logMoney(me, -amount, "bank", "송금", them.citizenId());
        plugin.characters().logMoney(them, amount, "bank", "입금(송금)", me.citizenId());
        bank(player, "<green>" + Text.esc(them.name()) + " 님에게 " + money(amount) + " 송금 완료!");
        Sched.entity(target, () -> {
            Text.send(target, "<aqua>[은행] <white>" + Text.esc(me.name()) + "</white> 님이 <green>" + money(amount) + "</green>을 보냈어요.");
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        });
    }

    private void history(Player player) {
        CharacterData me = plugin.characters().get(player);
        if (me == null) {
            return;
        }
        Screen waiting = app("<gray>거래내역 불러오는 중...");
        waiting.exit("◀ 은행", () -> bank(player, null));
        plugin.ui().show(player, waiting);
        plugin.characters().recentMoney(me, 10).whenComplete((list, error) -> Sched.entity(player, () -> {
            Screen s = app("<gray><bold>거래내역 (최근 10건)");
            if (error != null || list == null) {
                s.line("<red>불러오지 못했어요.");
            } else if (list.isEmpty()) {
                s.line("<gray>아직 기록이 없어요.");
            } else {
                SimpleDateFormat fmt = new SimpleDateFormat("MM/dd HH:mm");
                fmt.setTimeZone(TimeZone.getTimeZone(plugin.settings().timezone));
                for (CharacterRepository.MoneyEntry e : list) {
                    String sign = e.amount() >= 0 ? "<green>+" : "<red>";
                    s.line("<dark_gray>" + fmt.format(new Date(e.timestamp())) + " " + sign + money(e.amount())
                            + " <gray>" + Text.esc(e.reason()));
                }
            }
            s.exit("◀ 은행", () -> bank(player, null));
            plugin.ui().show(player, s);
        }));
    }

    // ------------------------------------------------------------------ 내 정보

    private void profile(Player player) {
        CharacterData me = plugin.characters().get(player);
        Screen s = app("<white><bold>내 정보");
        s.line("<gray>이름: <white>" + Text.esc(me.name()));
        s.line("<gray>주민번호: <white>" + me.citizenId());
        s.line("<gray>직업: <white>" + me.job().displayName());
        s.line("<gray>현금 <green>" + money(me.cash()) + "</green>   은행 <aqua>" + money(me.bank()));
        if (me.isJailed()) {
            s.line("<red>수감 중: " + me.jailSeconds() + "초 남음");
        }
        boolean hasId = CustomItems.has(player, CustomItems.ID_CARD);
        if (!hasId) {
            long fee = 5_000;
            s.button("<yellow>신분증 재발급 (" + money(fee) + ")", () -> {
                if (me.pay(fee) == CharacterData.PayResult.INSUFFICIENT) {
                    Text.send(player, "<red>돈이 부족해요.");
                } else {
                    com.junseo.city.economy.Economy.give(player, CustomItems.idCard(me));
                    Text.send(player, "<green>신분증을 다시 받았어요.");
                }
                profile(player);
            });
        }
        home(s, player);
        plugin.ui().show(player, s);
    }

    // ------------------------------------------------------------------ 긴급 신고

    private void emergency(Player player) {
        Screen s = app("<red><bold>긴급 신고");
        s.line("<gray>신고하면 내 위치가 함께 전달돼요. 장난 신고는 처벌받아요.");
        s.line("<gray>근무 중: 경찰 <white>" + plugin.dispatch().onDuty(DispatchService.Line.POLICE)
                + "명</white> · 의료국 <white>" + plugin.dispatch().onDuty(DispatchService.Line.EMS) + "명");
        s.button("<blue><bold>112 경찰", () -> reasons(player, DispatchService.Line.POLICE,
                List.of("강도", "폭행", "총소리", "차량 절도", "교통사고", "수상한 사람")));
        s.button("<red><bold>119 구조", () -> reasons(player, DispatchService.Line.EMS,
                List.of("쓰러진 사람", "부상자", "교통사고", "화재", "나 다쳤어요")));
        home(s, player);
        plugin.ui().show(player, s);
    }

    private void reasons(Player player, DispatchService.Line line, List<String> reasons) {
        Screen s = app("<red><bold>" + line.number() + " · 무슨 일인가요?");
        for (String reason : reasons) {
            s.button("<white>" + reason, () -> {
                int sent = plugin.dispatch().reportByPlayer(player, line, reason);
                Screen done = app("<red><bold>" + line.number() + " 신고");
                if (sent < 0) {
                    done.line("<yellow>방금 신고했어요. 잠시 뒤에 다시 할 수 있어요.");
                } else if (sent == 0) {
                    done.line("<yellow>신고는 접수됐지만 지금 근무 중인 " + line.label() + "이(가) 없어요.");
                } else {
                    done.line("<green>신고 접수! " + line.label() + " " + sent + "명에게 전달됐어요.");
                    done.line("<gray>출동하면 알려 드릴게요.");
                }
                home(done, player);
                plugin.ui().show(player, done);
            });
        }
        s.exit("◀ 뒤로", () -> emergency(player));
        plugin.ui().show(player, s);
    }

    // ------------------------------------------------------------------ 직업

    public void job(Player player, String notice) {
        CharacterData me = plugin.characters().get(player);
        Screen s = app("<yellow><bold>직업");
        s.line("<gray>지금 직업: <white>" + me.job().displayName() + "</white>  <dark_gray>월급 "
                + money(plugin.settings().salary(me.job())) + " / " + plugin.settings().paycheckMinutes + "분");
        if (notice != null) {
            s.line(notice);
        }
        if (me.job().government()) {
            s.line("<gray>공무직은 그만두면 다시 채용되어야 해요.");
            s.button("<red>사직하기", () -> confirmQuit(player));
        } else {
            s.line("<gray>바로 시작할 수 있는 직업:");
            for (Job job : Job.values()) {
                if (job.government() || job == me.job()) {
                    continue;
                }
                s.button("<white>" + job.displayName(), job.description(), () -> {
                    plugin.jobs().setJob(player, job, 0);
                    job(player, "<green>" + job.displayName() + "(으)로 일하기 시작했어요!");
                });
            }
            s.line("<dark_gray>경찰·의료국은 채용 공고를 보고 지원하면 관리자가 채용해요.");
        }
        home(s, player);
        plugin.ui().show(player, s);
    }

    private void confirmQuit(Player player) {
        Screen s = app("<red><bold>사직 확인");
        s.line("<white>정말 그만둘까요? 장비는 반납돼요.");
        s.button("<red><bold>사직", () -> {
            plugin.jobs().setJob(player, Job.CITIZEN, 0);
            job(player, "<gray>사직했어요. 이제 시민이에요.");
        });
        s.button("<gray>취소", () -> job(player, null));
        s.exit("◀ 뒤로", () -> job(player, null));
        plugin.ui().show(player, s);
    }

    // ------------------------------------------------------------------ 알바

    private void partTime(Player player) {
        CharacterData me = plugin.characters().get(player);
        Screen s = app("<green><bold>알바 · 택배");
        if (me.job() != Job.DELIVERY) {
            s.line("<gray>택배 배달은 <white>택배기사</white>만 할 수 있어요.");
            if (!me.job().government()) {
                s.button("<white>택배기사로 일하기", () -> {
                    plugin.jobs().setJob(player, Job.DELIVERY, 0);
                    partTime(player);
                });
            }
        } else if (plugin.delivery().hasMission(player)) {
            s.line("<white>배달 중이에요. 화면 위 화살표를 따라가세요.");
            s.button("<red>배달 취소", () -> {
                plugin.delivery().cancel(player, "<yellow>배달을 취소했어요.");
                partTime(player);
            });
        } else {
            s.line("<gray>택배 상자를 받아 배달지까지 가져다주면 돈을 받아요. 빨리 가면 보너스!");
            s.button("<green><bold>배달 시작", () -> {
                plugin.delivery().start(player);
                plugin.ui().close(player);
            });
        }
        home(s, player);
        plugin.ui().show(player, s);
    }

    // ------------------------------------------------------------------ 차고

    private void garage(Player player) {
        Screen s = app("<gray><bold>차고");
        List<Car> cars = plugin.cars().ownedBy(player.getUniqueId());
        if (cars.isEmpty()) {
            s.line("<gray>꺼내 놓은 차가 없어요.");
        }
        Location me = player.getLocation();
        for (Car car : cars) {
            Location at = car.lastKnownLocation();
            String where = at == null || at.getWorld() != me.getWorld() ? "다른 지역"
                    : Math.round(at.distance(me)) + "m " + Compass.arrow(me.getYaw(), at.getX() - me.getX(), at.getZ() - me.getZ());
            s.line("<white>" + car.type().displayName() + " <gray>" + where);
            s.button("<white>" + car.type().displayName() + " 길 안내", () -> {
                if (at != null) {
                    plugin.gps().start(player, at, "내 " + car.type().displayName());
                }
                plugin.ui().close(player);
            });
            s.button("<red>" + car.type().displayName() + " 차고에 넣기", () -> {
                plugin.cars().despawn(car);
                garage(player);
            });
        }
        s.line("<dark_gray>차 열쇠를 들고 땅을 우클릭하면 차가 나와요.");
        home(s, player);
        plugin.ui().show(player, s);
    }

    // ------------------------------------------------------------------ 지도

    private void map(Player player) {
        Screen s = app("<dark_aqua><bold>지도").columns(2);
        Location me = player.getLocation();
        s.button("<gold>큰 지도 보기 <gray>(F키)", "도시 전체 지도를 엽니다", () -> plugin.map().openMap(player));
        if (plugin.gps().active(player)) {
            s.button("<red>길 안내 끄기", () -> {
                plugin.gps().stop(player);
                map(player);
            });
        }
        addPlace(s, player, me, "<white>병원", PlaceType.HOSPITAL, null);
        addPlace(s, player, me, "<white>시티 광장", PlaceType.SPAWN, null);
        addPlace(s, player, me, "<aqua>가장 가까운 ATM", PlaceType.ATM, null);
        for (NpcType type : NpcType.values()) {
            addPlace(s, player, me, "<yellow>" + type.displayName(), PlaceType.NPC, type.id());
        }
        home(s, player);
        plugin.ui().show(player, s);
    }

    private void addPlace(Screen s, Player player, Location me, String label, PlaceType type, String name) {
        Place best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Place p : plugin.places().of(type)) {
            if (name != null && !name.equals(p.name())) {
                continue;
            }
            double d = p.distanceSquared(me);
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        if (best == null) {
            return;
        }
        Place target = best;
        String distance = bestDistance == Double.MAX_VALUE ? "" : " <gray>" + Math.round(Math.sqrt(bestDistance)) + "m";
        s.button(label + distance, () -> {
            Location loc = target.toLocation();
            if (loc != null) {
                plugin.gps().start(player, loc, Text.plainText(label));
            }
            plugin.ui().close(player);
        });
    }

    // ------------------------------------------------------------------ 관리자 호출

    private void adminCall(Player player) {
        Screen s = app("<light_purple><bold>관리자 호출");
        s.line("<gray>무슨 일인가요? 위치가 함께 전달돼요.");
        for (String reason : List.of("끼었어요 / 버그", "규칙 위반 신고", "도움이 필요해요", "기타")) {
            s.button("<white>" + reason, () -> {
                int admins = notifyAdmins(player, reason);
                Screen done = app("<light_purple><bold>관리자 호출");
                done.line(admins > 0 ? "<green>관리자 " + admins + "명에게 전달됐어요." : "<yellow>지금 접속한 관리자가 없어요. 디스코드에 남겨 주세요.");
                home(done, player);
                plugin.ui().show(player, done);
            });
        }
        home(s, player);
        plugin.ui().show(player, s);
    }

    private int notifyAdmins(Player reporter, String reason) {
        CharacterData data = plugin.characters().get(reporter);
        String name = data == null ? reporter.getName() : data.name() + "(" + reporter.getName() + ")";
        Location loc = reporter.getLocation();
        plugin.getLogger().info("[관리자 호출] " + name + " - " + reason + " @ " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        int count = 0;
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!admin.hasPermission("junseocity.admin")) {
                continue;
            }
            count++;
            Component button = Text.mm("<green><bold>[이동]")
                    .clickEvent(ClickEvent.custom(Key.key("junseocity", "admintp/" + reporter.getUniqueId()), null));
            Sched.entity(admin, () -> {
                admin.sendMessage(Text.mm("<light_purple><bold>[관리자 호출]</bold></light_purple> <white>" + Text.esc(name)
                        + "</white><gray>: " + Text.esc(reason) + " ").append(button));
                admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            });
        }
        return count;
    }

    private void adminTeleport(Player admin, String uuid) {
        if (!admin.hasPermission("junseocity.admin")) {
            return;
        }
        try {
            Player target = Bukkit.getPlayer(java.util.UUID.fromString(uuid));
            if (target == null) {
                Text.send(admin, "<gray>그 사람은 접속해 있지 않아요.");
                return;
            }
            Sched.entity(target, () -> {
                Location loc = target.getLocation();
                Sched.entity(admin, () -> admin.teleportAsync(loc));
            });
        } catch (IllegalArgumentException ignored) {
            // 잘못된 버튼
        }
    }
}
