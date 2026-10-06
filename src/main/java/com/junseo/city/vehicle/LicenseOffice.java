package com.junseo.city.vehicle;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.ui.Screen;
import com.junseo.city.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** 시청 민원실 (NPC): 운전면허 발급. 시험은 없고 수수료만 냅니다. */
public final class LicenseOffice {
    /** 기본 수수료 (config.yml prices.license) */
    public static final long LICENSE_FEE = 40;

    private final JunseoCity plugin;

    public LicenseOffice(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return;
        }
        Screen s = new Screen("<white><bold>시청 민원실").columns(1).buttonWidth(260);
        s.line("<gray>이름: <white>" + Text.esc(data.name()) + " <gray>(" + data.citizenId() + ")");
        s.line("<gray>운전면허: " + (data.hasLicense() ? "<green>있음" : "<red>없음"));
        if (!data.hasLicense()) {
            long fee = plugin.settings().price("license", LICENSE_FEE);
            s.button("<green>운전면허 발급 <gray>(" + plugin.settings().money(fee) + ")", () -> {
                if (GarageApp.pay(plugin, player, data, fee, "운전면허 발급")) {
                    data.setLicense(true);
                    Text.send(player, "<green>운전면허가 나왔어요! 신분증에 표시돼요.");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                }
                open(player);
            });
        }
        s.exit("닫기", null);
        plugin.ui().show(player, s);
    }
}
