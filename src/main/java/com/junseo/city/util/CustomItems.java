package com.junseo.city.util;

import com.junseo.city.vehicle.CarType;
import com.junseo.city.weapon.GunType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * 시티 전용 아이템 만들기/알아보기.
 * 총·수갑·열쇠 같은 "쓰는" 아이템은 바닐라에서 아무 효과가 없는 당근 낚싯대를 바탕으로 만들고,
 * item_model 로 겉모양만 바꿉니다. (나중에 리소스팩을 쓰면 진짜 총 모양으로 바꿀 수 있어요.)
 */
public final class CustomItems {
    public static final String AMMO = "ammo";
    public static final String HANDCUFFS = "handcuffs";
    public static final String BANDAGE = "bandage";
    public static final String CAR_KEY = "car_key";
    public static final String PACKAGE = "package";

    private CustomItems() {
    }

    public static ItemStack gun(GunType type) {
        ItemStack stack = usable(type.id(), "<gold>" + type.displayName(), type.model());
        stack.editMeta(meta -> {
            if (type == GunType.TASER) {
                meta.lore(Text.lore("경찰 전용. 맞은 사람은 3초 동안 못 움직여요.",
                        "", "<yellow>우클릭</yellow> 발사"));
            } else {
                meta.lore(Text.lore(
                        "데미지 <white>" + type.damage() + (type.pellets() > 1 ? " x " + type.pellets() : "") + "</white>",
                        "탄창 <white>" + type.magazine() + "발</white>  사거리 <white>" + (int) type.range() + "칸</white>",
                        "",
                        "<yellow>우클릭</yellow> 발사   <yellow>F</yellow> 재장전",
                        "총알은 총포상에서 살 수 있어요."));
            }
            meta.getPersistentDataContainer().set(Keys.GUN_ID, PersistentDataType.STRING, UUID.randomUUID().toString());
            meta.getPersistentDataContainer().set(Keys.GUN_AMMO, PersistentDataType.INTEGER, type.magazine());
        });
        return stack;
    }

    public static ItemStack ammo(int amount) {
        ItemStack stack = new ItemStack(Material.IRON_NUGGET, Math.max(1, Math.min(64, amount)));
        stack.editMeta(meta -> {
            meta.itemName(Text.plain("<white>총알"));
            meta.lore(Text.lore("모든 총에 쓸 수 있어요."));
            meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, AMMO);
        });
        return stack;
    }

    public static ItemStack handcuffs() {
        ItemStack stack = usable(HANDCUFFS, "<aqua>수갑", "minecraft:iron_chain");
        stack.editMeta(meta -> meta.lore(Text.lore("경찰 전용. 수배자를 <yellow>때리면</yellow> 체포해요.")));
        return stack;
    }

    public static ItemStack bandage() {
        // 당근 낚싯대는 내구도가 있어서 겹칠 수 없으니, 붕대는 종이를 바탕으로 만듭니다.
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(meta -> {
            meta.itemName(Text.plain("<red>붕대"));
            meta.lore(Text.lore("<yellow>우클릭</yellow>하면 체력 3칸 회복"));
            meta.setMaxStackSize(16);
            meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, BANDAGE);
        });
        return stack;
    }

    public static ItemStack carKey(CarType type, UUID owner, String ownerName) {
        ItemStack stack = usable(CAR_KEY, "<green>" + type.displayName() + " 열쇠", "minecraft:tripwire_hook");
        stack.editMeta(meta -> {
            meta.lore(Text.lore(
                    "주인: <white>" + Text.esc(ownerName) + "</white>",
                    "",
                    "<yellow>땅에 우클릭</yellow> 차 꺼내기",
                    "<yellow>웅크리고 우클릭</yellow> 차 넣기",
                    "<yellow>차에 우클릭</yellow> 타기   <yellow>Shift</yellow> 내리기"));
            var pdc = meta.getPersistentDataContainer();
            pdc.set(Keys.KEY_ID, PersistentDataType.STRING, UUID.randomUUID().toString());
            pdc.set(Keys.CAR_TYPE, PersistentDataType.STRING, type.id());
            pdc.set(Keys.OWNER, PersistentDataType.STRING, owner.toString());
        });
        return stack;
    }

    public static ItemStack deliveryPackage() {
        ItemStack stack = usable(PACKAGE, "<#c58c4b>택배 상자", "minecraft:barrel");
        stack.editMeta(meta -> meta.lore(Text.lore("배달지에 가져다주세요!")));
        return stack;
    }

    /** 경찰 직업 장비로 표시 (직업을 바꾸면 회수). */
    public static ItemStack markPoliceGear(ItemStack stack) {
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.POLICE, PersistentDataType.BYTE, (byte) 1));
        return stack;
    }

    public static boolean isPoliceGear(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(Keys.POLICE);
    }

    /** 시티 아이템 종류 id. 일반 아이템이면 null. */
    public static String id(ItemStack stack) {
        return string(stack, Keys.ITEM);
    }

    public static boolean is(ItemStack stack, String id) {
        return id.equals(id(stack));
    }

    public static GunType gunType(ItemStack stack) {
        return GunType.byId(id(stack));
    }

    public static String string(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static UUID uuid(ItemStack stack, NamespacedKey key) {
        String raw = string(stack, key);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ItemStack usable(String id, String name, String model) {
        ItemStack stack = new ItemStack(Material.CARROT_ON_A_STICK);
        stack.editMeta(meta -> {
            meta.itemName(Text.plain(name));
            meta.setItemModel(NamespacedKey.fromString(model));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, id);
        });
        return stack;
    }
}
