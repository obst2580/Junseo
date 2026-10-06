package com.junseo.city.vehicle;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Compass;
import com.junseo.city.ui.Screen;
import com.junseo.city.util.Text;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 폰 「차고」 앱: 내 차 목록과 차마다 할 일.
 * <ul>
 *   <li>차고에 있음: 공영 차고(지도 P) 근처에서 꺼내기, 수리</li>
 *   <li>나와 있음: 길 안내, 차가 차고 안에 있으면 넣기, 어디서나 견인 부르기(마지막 차고로)</li>
 *   <li>압류됨: 견인비를 내고 찾기 (차고로 옮겨짐)</li>
 * </ul>
 */
public final class GarageApp {

    private final JunseoCity plugin;

    public GarageApp(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** 수리비: 기본값 + 망가진 1% 마다 (config.yml prices.car_repair_base / car_repair_percent) */
    long repairCost(int health) {
        return plugin.settings().price("car_repair_base", 20) + (100 - health) * plugin.settings().price("car_repair_percent", 8);
    }

    /** 견인비 (prices.car_tow) */
    long towFee() {
        return plugin.settings().price("car_tow", 40);
    }

    /** 압류 차 찾는 값 (prices.car_impound) */
    long impoundFee() {
        return plugin.settings().price("car_impound", 200);
    }

    private String money(long amount) {
        return plugin.settings().money(amount);
    }

    private static String stateText(VehicleRecord r) {
        return switch (r.state()) {
            case GARAGE -> "<green>차고</green>";
            case OUT -> "<yellow>나와 있음</yellow>";
            case IMPOUND -> "<red>압류</red>";
        };
    }

    public void open(Player player, Runnable home) {
        Screen s = new Screen("<gray><bold>차고").columns(1).buttonWidth(260);
        Location me = player.getLocation();
        GarageService.Garage here = plugin.garages().nearest(me, GarageService.NEAR);
        s.line(here != null ? "<green>지금 있는 곳: " + Text.esc(here.name())
                : "<gray>공영 차고(지도의 <white>P</white>) 근처에서 차를 꺼낼 수 있어요.");
        List<VehicleRecord> mine = plugin.vehicles().ownedBy(player.getUniqueId());
        if (mine.isEmpty()) {
            s.line("<gray>가진 차가 없어요. 강남 차 대리점에서 살 수 있어요.");
        }
        for (VehicleRecord r : mine) {
            s.button("<white>" + r.label() + " <gray>" + r.plate() + " · " + stateText(r) + " <gray>· " + r.health() + "%",
                    "눌러서 꺼내기·넣기·견인·수리", () -> vehicle(player, r, home));
        }
        for (Car car : plugin.cars().ownedBy(player.getUniqueId())) {
            if (car.record() != null) {
                continue;
            }
            Location at = car.lastKnownLocation();
            String where = at == null || at.getWorld() != me.getWorld() ? "다른 지역"
                    : Math.round(at.distance(me)) + "m " + Compass.arrow(me.getYaw(), at.getX() - me.getX(), at.getZ() - me.getZ());
            s.button("<aqua>" + car.type().displayName() + " <gray>(업무용) " + where, "길 안내", () -> {
                if (at != null) {
                    plugin.gps().start(player, at, car.type().displayName());
                }
                plugin.ui().close(player);
            });
        }
        if (here == null) {
            s.button("<gold>가장 가까운 공영 차고 안내", () -> guideToGarage(player));
        }
        s.exit("◀ 홈", home);
        plugin.ui().show(player, s);
    }

    private void guideToGarage(Player player) {
        GarageService.Garage g = plugin.garages().nearest(player.getLocation(), Double.MAX_VALUE);
        if (g == null) {
            Text.send(player, "<gray>차고 정보를 준비 중이에요. 잠시 뒤에 다시 해 주세요.");
            return;
        }
        Location at = new Location(player.getWorld(), g.cx(), player.getLocation().getY(), g.cz());
        plugin.gps().start(player, at, g.name());
        plugin.ui().close(player);
    }

    private void vehicle(Player player, VehicleRecord r, Runnable home) {
        Screen s = new Screen("<gray><bold>" + r.label()).columns(1).buttonWidth(260);
        s.line("<gray>번호판 <white><bold>" + r.plate() + "</bold></white> · " + stateText(r) + " <gray>· 차 상태 " + r.health() + "%");
        Location me = player.getLocation();
        switch (r.state()) {
            case GARAGE -> {
                s.line("<gray>보관한 곳: <white>" + Text.esc(r.garage() == null ? "공영 차고" : r.garage()));
                GarageService.Garage here = plugin.garages().nearest(me, GarageService.NEAR);
                if (here != null) {
                    s.button("<green>여기서 꺼내기 <gray>(" + Text.esc(here.name()) + ")", () -> takeOut(player, r, here));
                } else {
                    s.button("<gold>가장 가까운 공영 차고 안내", () -> guideToGarage(player));
                }
                if (r.health() < 100) {
                    long cost = repairCost(r.health());
                    s.button("<aqua>수리 <gray>(" + money(cost) + ")", "정비소에서 고쳐 줘요", () -> {
                        if (pay(player, cost, "차 수리")) {
                            r.setHealth(100);
                            plugin.vehicles().save(r);
                            Text.send(player, "<green>차를 다 고쳤어요.");
                        }
                        vehicle(player, r, home);
                    });
                }
            }
            case OUT -> {
                Car car = plugin.cars().byRecord(r);
                if (car == null) {
                    r.setState(VehicleRecord.State.GARAGE, null);
                    plugin.vehicles().save(r);
                    vehicle(player, r, home);
                    return;
                }
                Location at = car.lastKnownLocation();
                if (at != null && at.getWorld() == me.getWorld()) {
                    s.line("<gray>위치: <white>" + Math.round(at.distance(me)) + "m "
                            + Compass.arrow(me.getYaw(), at.getX() - me.getX(), at.getZ() - me.getZ()));
                }
                s.button("<white>길 안내", () -> {
                    if (at != null) {
                        plugin.gps().start(player, at, "내 " + r.label());
                    }
                    plugin.ui().close(player);
                });
                GarageService.Garage g = at == null ? null : plugin.garages().nearest(at, 3);
                if (g != null) {
                    s.button("<green>차고에 넣기 <gray>(" + Text.esc(g.name()) + ")", () -> {
                        plugin.cars().store(car, g.name());
                        Text.send(player, "<green>" + Text.esc(g.name()) + "에 넣었어요.");
                        open(player, home);
                    });
                } else {
                    s.line("<dark_gray>차를 공영 차고 안에 세우면 넣을 수 있어요.");
                }
                s.button("<yellow>견인 부르기 <gray>(" + money(towFee()) + ", 마지막 차고로)", () -> {
                    if (pay(player, towFee(), "차 견인")) {
                        plugin.cars().tow(r);
                        Text.send(player, "<green>견인차가 차를 " + Text.esc(r.garage() == null ? "차고" : r.garage()) + "(으)로 옮겼어요.");
                    }
                    open(player, home);
                });
            }
            case IMPOUND -> {
                s.line("<red>경찰에 견인됐어요. 견인비를 내면 차고로 옮겨 드려요.");
                s.button("<yellow>견인소에서 찾기 <gray>(" + money(impoundFee()) + ")", () -> {
                    if (pay(player, impoundFee(), "압류 차 찾기")) {
                        r.setState(VehicleRecord.State.GARAGE, null);
                        plugin.vehicles().save(r);
                        Text.send(player, "<green>차를 찾았어요. " + Text.esc(r.garage() == null ? "차고" : r.garage()) + "에 있어요.");
                    }
                    open(player, home);
                });
            }
        }
        s.exit("◀ 차고", () -> open(player, home));
        plugin.ui().show(player, s);
    }

    private void takeOut(Player player, VehicleRecord r, GarageService.Garage g) {
        if (r.state() != VehicleRecord.State.GARAGE) {
            return;
        }
        Location spot = plugin.garages().freeSpot(g, player.getWorld(), plugin.cars().carLocations());
        if (spot == null) {
            Text.send(player, "<yellow>빈 주차 칸이 없어요. 다른 공영 차고를 이용해 주세요.");
            return;
        }
        r.setState(VehicleRecord.State.GARAGE, g.name());
        plugin.cars().spawnOwned(r, spot);
        plugin.gps().start(player, spot, "내 " + r.label());
        Text.send(player, "<green>" + r.label() + " 나왔어요! <gray>(" + r.plate() + ") 차를 우클릭해서 타세요.");
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.2f);
        plugin.ui().close(player);
    }

    /** 현금 먼저, 모자라면 카드 */
    private boolean pay(Player player, long amount, String reason) {
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return false;
        }
        return pay(plugin, player, data, amount, reason);
    }

    /** 현금 먼저, 모자라면 카드로 내고 돈 기록을 남김. 모자라면 알림 */
    static boolean pay(JunseoCity plugin, Player player, CharacterData data, long amount, String reason) {
        CharacterData.PayResult result = data.pay(amount);
        if (result == CharacterData.PayResult.INSUFFICIENT) {
            Text.send(player, "<red>돈이 부족해요. <gray>(" + plugin.settings().money(amount) + " 필요)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        plugin.characters().logMoney(data, -amount, result == CharacterData.PayResult.BANK ? "bank" : "cash", reason, null);
        return true;
    }
}
