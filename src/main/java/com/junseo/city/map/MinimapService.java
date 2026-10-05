package com.junseo.city.map;

import com.junseo.city.JunseoCity;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지도 아이템: 왼손에 들면 미니맵, F키로 오른손에 옮기면 전체 지도.
 * 도시 그림은 설계도(layout.json)로 서버가 켜질 때 한 번 만들어 둡니다.
 */
public final class MinimapService implements Listener {
    public static final String ITEM_ID = "minimap";

    /** 전체 지도 확대 단계 (우클릭으로 바꿈) */
    public enum Zoom {
        CITY("도시 전체", 40),
        DISTRICT("구역", 8),
        STREET("동네", 2);

        final String label;
        final double blocksPerPixel;

        Zoom(String label, double blocksPerPixel) {
            this.label = label;
            this.blocksPerPixel = blocksPerPixel;
        }

        Zoom next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final JunseoCity plugin;
    private final MinimapRenderer renderer;
    private final Map<UUID, Zoom> zooms = new ConcurrentHashMap<>();
    private volatile CityTerrain terrain;
    private volatile RadarRaster fine;
    private volatile RadarRaster mid;
    private volatile RadarRaster coarse;
    private volatile MapView view;

    public MinimapService(JunseoCity plugin) {
        this.plugin = plugin;
        this.renderer = new MinimapRenderer(this);
    }

    /** 설계도를 읽고, 도시 그림을 따로 만들고, 지도 아이템이 쓸 MapView 를 준비합니다 */
    public void start() {
        try {
            terrain = loadTerrain();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("지도 설계도(layout.json)를 읽지 못해서 미니맵을 끕니다: " + e.getMessage());
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            long started = System.currentTimeMillis();
            RadarRaster f = RadarRaster.build(terrain, 2);
            mid = f.pooled(4);
            coarse = f.pooled(20);
            fine = f;
            plugin.getLogger().info("미니맵 도시 그림 준비 완료 (" + (System.currentTimeMillis() - started) + "ms)");
        });
        Sched.global(this::setupView);
    }

    private void setupView() {
        File file = new File(plugin.getDataFolder(), "minimap.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        int id = yml.getInt("map-id", -1);
        MapView v = id >= 0 ? Bukkit.getMap(id) : null;
        if (v == null) {
            World world = Bukkit.getWorlds().get(0);
            v = Bukkit.createMap(world);
            yml.set("map-id", v.getId());
            try {
                yml.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("minimap.yml 저장 실패: " + e.getMessage());
            }
        }
        for (MapRenderer r : new ArrayList<>(v.getRenderers())) {
            v.removeRenderer(r);
        }
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.setLocked(true);
        v.addRenderer(renderer);
        view = v;
        // 이미 접속해 있는 사람에게도 지도를 챙겨 줌
        for (Player player : Bukkit.getOnlinePlayers()) {
            Sched.entity(player, () -> {
                if (plugin.characters().has(player)) {
                    ensure(player);
                }
            });
        }
    }

    private CityTerrain loadTerrain() {
        File file = new File(plugin.getDataFolder(), "layout.json");
        if (!file.exists()) {
            plugin.saveResource("layout.json", false);
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return CityTerrain.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ 렌더러가 쓰는 것

    RadarRaster fine() {
        return fine;
    }

    RadarRaster mid() {
        return mid;
    }

    RadarRaster coarse() {
        return coarse;
    }

    CityTerrain terrain() {
        return terrain;
    }

    Layout layout() {
        return terrain.layout();
    }

    Zoom zoom(Player player) {
        return zooms.getOrDefault(player.getUniqueId(), Zoom.CITY);
    }

    Location gpsTarget(Player player) {
        return plugin.gps().target(player);
    }

    // ------------------------------------------------------------------ 아이템

    public static boolean isMinimap(ItemStack stack) {
        return CustomItems.is(stack, ITEM_ID);
    }

    public ItemStack item() {
        ItemStack stack = new ItemStack(Material.FILLED_MAP);
        stack.editMeta(MapMeta.class, meta -> {
            meta.setMapView(view);
            meta.itemName(Text.plain("<aqua>지도"));
            meta.lore(Text.lore(
                    "<yellow>왼손</yellow>에 들면 미니맵",
                    "<yellow>F키</yellow>로 오른손에 옮기면 전체 지도",
                    "전체 지도에서 <yellow>우클릭</yellow>: 도시 → 구역 → 동네"));
            meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, ITEM_ID);
        });
        return stack;
    }

    /** 지도가 없으면 줍니다. 왼손이 비어 있으면 왼손에 */
    public void ensure(Player player) {
        if (view == null || CustomItems.has(player, ITEM_ID)) {
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.isEmpty()) {
            player.getInventory().setItemInOffHand(item());
        } else {
            player.getInventory().addItem(item());
        }
    }

    public void forget(UUID uuid) {
        zooms.remove(uuid);
        renderer.forget(uuid);
    }

    // ------------------------------------------------------------------ 이벤트

    /** 전체 지도를 들고 우클릭하면 확대 단계를 바꿈 */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isMinimap(event.getItem())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Zoom next = zoom(player).next();
        zooms.put(player.getUniqueId(), next);
        player.sendActionBar(Text.mm("<aqua>지도: <white>" + next.label));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isMinimap(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Text.mm("<gray>지도는 버릴 수 없어요"));
        }
    }

    /** 상자·지도 제작대 등에 넣거나 복사하지 못하게 */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryType top = event.getView().getTopInventory().getType();
        if (top == InventoryType.CRAFTING || top == InventoryType.CREATIVE) {
            return;
        }
        boolean involved = isMinimap(event.getCurrentItem()) || isMinimap(event.getCursor());
        if (!involved && event.getClick() == ClickType.NUMBER_KEY && event.getWhoClicked() instanceof Player p) {
            involved = isMinimap(p.getInventory().getItem(event.getHotbarButton()));
        }
        if (!involved && event.getClick() == ClickType.SWAP_OFFHAND && event.getWhoClicked() instanceof Player p) {
            involved = isMinimap(p.getInventory().getItemInOffHand());
        }
        if (involved) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryType top = event.getView().getTopInventory().getType();
        if (top != InventoryType.CRAFTING && top != InventoryType.CREATIVE && isMinimap(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    /** 액자에 걸지 못하게 */
    @EventHandler(ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame
                && isMinimap(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }
}
