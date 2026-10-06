package com.junseo.city.vehicle;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.CarPhysics;
import com.junseo.city.ui.Screen;
import com.junseo.city.util.Text;
import com.junseo.city.vehicle.model.CarModels;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 차 대리점 (NPC): 차종 → 색 → 사기. 산 차는 가장 가까운 공영 차고에 들어가고, 폰 「차고」에서 꺼냅니다.
 * 운전면허는 사는 데 필요 없지만 없으면 알려 줍니다.
 */
public final class DealerApp {
    private final JunseoCity plugin;

    public DealerApp(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Screen s = new Screen("<gold><bold>차 대리점").columns(1).buttonWidth(260);
        s.line("<gray>차를 고르세요. 산 차는 가장 가까운 공영 차고에 넣어 드려요.");
        for (CarType type : CarType.values()) {
            if (!type.forSale()) {
                continue;
            }
            long price = plugin.settings().price("car_" + type.id(), type.price());
            s.button("<white>" + type.displayName() + " <gold>" + plugin.settings().money(price),
                    "최고 속도 " + CarPhysics.kmh(type.maxSpeed()) + "km/h", () -> colors(player, type, price));
        }
        s.exit("닫기", null);
        plugin.ui().show(player, s);
    }

    private void colors(Player player, CarType type, long price) {
        Screen s = new Screen("<gold><bold>" + type.displayName()).columns(3).buttonWidth(86);
        s.line("<gray>색을 고르세요. <gold>" + plugin.settings().money(price));
        for (CarModels.Paint paint : CarModels.Paint.values()) {
            String hex = String.format("#%06X", paint.rgb & 0xFFFFFF);
            s.button("<" + hex + ">■</" + hex + "> <white>" + paint.label, () -> confirm(player, type, paint, price));
        }
        s.exit("◀ 차 고르기", () -> open(player));
        plugin.ui().show(player, s);
    }

    private void confirm(Player player, CarType type, CarModels.Paint paint, long price) {
        Screen s = new Screen("<gold><bold>사기").columns(1).buttonWidth(260);
        s.line("<white>" + paint.label + " " + type.displayName() + " <gold>" + plugin.settings().money(price));
        CharacterData data = plugin.characters().get(player);
        if (data != null && !data.hasLicense()) {
            s.line("<yellow>운전면허가 없어요. 시청 민원실에서 먼저 받으세요. (무면허 운전은 단속 대상)");
        }
        s.button("<green>살게요", () -> buy(player, type, paint, price));
        s.exit("◀ 색 고르기", () -> colors(player, type, price));
        plugin.ui().show(player, s);
    }

    private void buy(Player player, CarType type, CarModels.Paint paint, long price) {
        CharacterData data = plugin.characters().get(player);
        if (data == null || !GarageApp.pay(plugin, player, data, price, "차 구매 " + type.displayName())) {
            return;
        }
        GarageService.Garage g = plugin.garages().nearest(player.getLocation(), Double.MAX_VALUE);
        String garage = g == null ? "공영 차고" : g.name();
        VehicleRecord r = plugin.vehicles().create(player.getUniqueId(), type, paint, garage);
        Text.send(player, "<green>" + r.label() + " 샀어요! <gray>번호판 <white>" + r.plate() + "<gray> · " + Text.esc(garage) + "에 있어요.");
        Text.send(player, "<gray>공영 차고(지도의 P)에서 폰 → 차고로 꺼내세요.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        if (g != null) {
            plugin.gps().start(player, new Location(player.getWorld(), g.cx(), player.getLocation().getY(), g.cz()), garage);
        }
        plugin.ui().close(player);
    }
}
