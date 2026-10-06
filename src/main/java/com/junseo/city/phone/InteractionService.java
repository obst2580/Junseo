package com.junseo.city.phone;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.ui.AmountPicker;
import com.junseo.city.ui.Screen;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 사람을 바라보고 빈손으로 우클릭하면 뜨는 상호작용 메뉴 (FiveM 의 Alt 조준 메뉴에 해당).
 * 1단계: 신분증 보여주기, 현금 건네기. 2단계에서 경찰(수갑·수색·벌금)·의료국(치료) 버튼이 추가됩니다.
 */
public final class InteractionService implements Listener {
    private static final double REACH = 5;

    private final JunseoCity plugin;
    private final AmountPicker picker;

    public InteractionService(JunseoCity plugin) {
        this.plugin = plugin;
        this.picker = new AmountPicker(plugin.ui(), amount -> plugin.settings().money(amount));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.getInventory().getItemInMainHand().isEmpty()) {
            return;
        }
        if (plugin.characters().get(player) == null || plugin.characters().get(target) == null) {
            return;
        }
        event.setCancelled(true);
        open(player, target);
    }

    private boolean near(Player player, Player target) {
        return target.isOnline() && target.getWorld() == player.getWorld()
                && target.getLocation().distanceSquared(player.getLocation()) <= REACH * REACH;
    }

    public void open(Player player, Player target) {
        Screen s = new Screen("<white><bold>상대방").columns(2).buttonWidth(130);
        s.line("<gray>이 사람에게 무엇을 할까요?");
        s.button("<white>신분증 보여주기", "내 이름과 주민번호를 보여줘요", () -> showId(player, target));
        s.button("<green>현금 건네기", "내 현금을 직접 건네줘요", () -> giveCash(player, target));
        CharacterData me = plugin.characters().get(player);
        if (me != null && me.job() == com.junseo.city.logic.Job.POLICE) {
            s.button("<blue>면허 확인", "운전면허가 있는지 조회해요", () -> checkLicense(player, target));
            s.button("<red>무면허 벌금", plugin.settings().money(NO_LICENSE_FINE) + " (면허가 없을 때만)", () -> fineNoLicense(player, target));
        }
        s.exit("닫기", null);
        plugin.ui().show(player, s);
    }

    private void showId(Player player, Player target) {
        CharacterData me = plugin.characters().get(player);
        if (me == null || !near(player, target)) {
            Text.send(player, "<gray>너무 멀어요.");
            return;
        }
        Text.send(player, "<gray>신분증을 보여줬어요.");
        Sched.entity(target, () -> {
            Screen s = new Screen("<white><bold>신분증").columns(1);
            s.line("<gray>상대방이 신분증을 보여줬어요.");
            s.line("<gray>이름: <white><bold>" + Text.esc(me.name()));
            s.line("<gray>주민번호: <white>" + me.citizenId());
            s.line("<gray>운전면허: " + (me.hasLicense() ? "<green>있음" : "<red>없음"));
            s.exit("확인", null);
            plugin.ui().show(target, s);
        });
    }

    /** 무면허 운전 벌금 기본값 (config.yml prices.fine_no_license) */
    static final long NO_LICENSE_FINE = 1_200;

    private void checkLicense(Player police, Player target) {
        CharacterData them = plugin.characters().get(target);
        if (them == null || !near(police, target)) {
            Text.send(police, "<gray>너무 멀어요.");
            return;
        }
        Text.send(police, "<blue>[조회] <white>" + Text.esc(them.name()) + " <gray>(" + them.citizenId() + ") 운전면허 "
                + (them.hasLicense() ? "<green>있음" : "<red>없음"));
    }

    private void fineNoLicense(Player police, Player target) {
        CharacterData them = plugin.characters().get(target);
        if (them == null || !near(police, target)) {
            Text.send(police, "<gray>너무 멀어요.");
            return;
        }
        if (them.hasLicense()) {
            Text.send(police, "<yellow>이 사람은 운전면허가 있어요.");
            return;
        }
        long fine = plugin.settings().price("fine_no_license", NO_LICENSE_FINE);
        CharacterData.PayResult result = them.pay(fine);
        if (result == CharacterData.PayResult.INSUFFICIENT) {
            Text.send(police, "<yellow>돈이 모자라 벌금을 받지 못했어요. 체포를 고려하세요.");
            return;
        }
        plugin.characters().logMoney(them, -fine, result == CharacterData.PayResult.BANK ? "bank" : "cash", "무면허 운전 벌금", null);
        Text.send(police, "<green>무면허 운전 벌금 " + plugin.settings().money(fine) + "을 부과했어요.");
        Sched.entity(target, () -> Text.send(target, "<red>무면허 운전으로 벌금 " + plugin.settings().money(fine) + "을 냈어요."));
    }

    private void giveCash(Player player, Player target) {
        CharacterData me = plugin.characters().get(player);
        if (me == null) {
            return;
        }
        picker.open(player, "<green>현금 건네기", "내 현금에서 직접 건네줘요", me.cash(), amount -> {
            CharacterData them = plugin.characters().get(target);
            if (them == null || !near(player, target)) {
                Text.send(player, "<gray>상대방이 너무 멀어요.");
                return;
            }
            if (!CharacterData.transferCash(me, them, amount)) {
                Text.send(player, "<red>현금이 부족해요.");
                return;
            }
            plugin.characters().logMoney(me, -amount, "cash", "현금 건넴", them.citizenId());
            plugin.characters().logMoney(them, amount, "cash", "현금 받음", me.citizenId());
            Text.send(player, "<green>" + plugin.settings().money(amount) + "을 건넸어요.");
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.4f);
            Sched.entity(target, () -> {
                Text.send(target, "<green>누군가 현금 " + plugin.settings().money(amount) + "을 건네줬어요.");
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            });
        }, () -> open(player, target));
    }
}
