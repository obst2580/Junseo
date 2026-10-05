package com.junseo.city.map;

import com.junseo.city.JunseoCity;
import com.junseo.city.Settings;
import com.junseo.city.hud.ActionBarHud;
import com.junseo.city.hud.HudLayout;
import com.junseo.city.ui.Screen;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import com.junseo.citymap.buildings.CityBuildings;
import com.junseo.citymap.buildings.Placement;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.layout.LayoutFile;
import com.junseo.citymap.terrain.CityTerrain;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.ShadowColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 게임 속 지도.
 * <ul>
 *   <li><b>미니맵</b>: 화면 아래(핫바 옆)에 늘 떠 있는 GTA 레이더. 보는 방향이 위, 차를 타면 더 넓게</li>
 *   <li><b>큰 지도</b>: F키. 북쪽이 위, 확대·이동, 마우스를 올리면 이름, 누르면 길 안내</li>
 * </ul>
 * 둘 다 리소스팩의 글꼴로 그립니다 (모드 없이). 리소스팩이 없는 사람에게는 미니맵 대신 아무것도 안 그리고,
 * F키는 장소 목록(길 안내)으로 바뀝니다.
 */
public final class MapService implements Listener {
    private static final Key MAP_FONT = Key.key(MapGlyphs.FONT);
    private static final String ACTION = "map";

    /** 큰 지도 확대 단계: 0 = 도시 전체 */
    private static final String[] ZOOM_NAMES = {"도시 전체", "구역", "동네"};
    private static final double[] ZOOM_BPP = {0, 6, 3};

    private record RadarKey(long x, long z, int yaw, double bpp, long target) {
    }

    private record Drawn(RadarKey key, long at) {
    }

    /** 열어 둔 큰 지도 (클릭한 칸을 월드 좌표로 되돌릴 때 씀) */
    private record Open(long token, int zoom, double cx, double cz, Viewport view, List<BigMap.Place> places) {
    }

    private final JunseoCity plugin;
    private final ActionBarHud bar;
    private final AtomicLong tokens = new AtomicLong();
    private final Map<UUID, Drawn> drawn = new ConcurrentHashMap<>();
    private final Map<UUID, Open> open = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> tickers = new ConcurrentHashMap<>();
    private volatile CityTerrain terrain;
    private volatile CityBuildings buildings;
    private volatile RadarRaster walk;
    private volatile RadarRaster mid;
    private volatile List<RadarHud.Marker> markers = List.of();
    private volatile List<BigMap.Place> places = List.of();

    public MapService(JunseoCity plugin, ActionBarHud bar) {
        this.plugin = plugin;
        this.bar = bar;
    }

    /** 설계도를 읽고 도시 그림을 따로 만듭니다 (몇 초) */
    public void start() {
        bar.side("right".equalsIgnoreCase(plugin.settings().radar.side()) ? HudLayout.Side.RIGHT : HudLayout.Side.LEFT);
        plugin.ui().onStaticAction(ACTION, this::onMapClick);
        try {
            terrain = loadTerrain();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("지도 설계도(layout.json)를 읽지 못해서 미니맵·큰 지도를 끕니다: " + e.getMessage());
            return;
        }
        List<RadarHud.Marker> m = new ArrayList<>();
        List<BigMap.Place> p = new ArrayList<>();
        for (Layout.Hub h : terrain.layout().hubs()) {
            int icon = MapIcons.forHub(h.id());
            if (icon >= 0) {
                m.add(new RadarHud.Marker(h.x(), h.z(), icon));
                p.add(new BigMap.Place(h.id(), BigMap.shortName(h.name()), icon, h.x(), h.z()));
            }
        }
        markers = List.copyOf(m);
        places = List.copyOf(p);
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            long started = System.currentTimeMillis();
            CityBuildings b = CityBuildings.plan(terrain);
            RadarRaster fine = RadarRaster.build(terrain, b, 2);
            buildings = b;
            // 공영 차고(공영주차장·주차타워·렌터카): 차를 꺼내는 곳이라 지도에 P 로
            List<RadarHud.Marker> gm = new ArrayList<>(markers);
            List<BigMap.Place> gp = new ArrayList<>(places);
            for (com.junseo.citymap.buildings.Placement g : b.garages()) {
                double[] bb = g.bounds();
                double gx = (bb[0] + bb[2]) / 2, gz = (bb[1] + bb[3]) / 2;
                gm.add(new RadarHud.Marker(gx, gz, MapIcons.GARAGE));
                gp.add(new BigMap.Place("garage-" + gp.size(), BigMap.shortName(g.name), MapIcons.GARAGE, gx, gz));
            }
            markers = List.copyOf(gm);
            places = List.copyOf(gp);
            walk = fine.pooled(2).closeBuildingGaps();
            mid = fine.pooled(4).closeBuildingGaps();
            plugin.getLogger().info("지도 그림 준비 완료 (건물 " + b.placements().size() + "채, "
                    + (System.currentTimeMillis() - started) + "ms)");
        });
    }

    /** plugins/JunseoCity/layout.json (없거나 플러그인 안의 것보다 옛 버전이면 새로 꺼냄) */
    private CityTerrain loadTerrain() {
        File file = new File(plugin.getDataFolder(), "layout.json");
        try {
            String done = LayoutFile.ensureCurrent(file.toPath(), () -> plugin.getResource("layout.json"));
            if (done != null) {
                plugin.getLogger().info(done);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return CityTerrain.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ 미니맵

    /** 캐릭터가 준비되면: 미니맵을 0.1초마다 확인하는 개인 타이머. 플레이어 스레드에서 호출 */
    public void startHud(Player player) {
        removeLegacyItems(player);
        ScheduledTask task = Sched.entityRepeat(player, 2, 2, t -> tick(player));
        ScheduledTask old = task == null ? null : tickers.put(player.getUniqueId(), task);
        if (old != null) {
            old.cancel();
        }
    }

    public void forget(UUID uuid) {
        ScheduledTask task = tickers.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        drawn.remove(uuid);
        open.remove(uuid);
        bar.forget(uuid);
    }

    private void tick(Player player) {
        RadarRaster w = walk, m = mid;
        Settings.RadarSettings cfg = plugin.settings().radar;
        Location loc = player.getLocation();
        boolean show = w != null && m != null && plugin.pack().ready(player) && cfg.shows(loc.getWorld().getName());
        if (!show) {
            if (bar.hasRadar(player)) {
                bar.radar(player, null);
                drawn.remove(player.getUniqueId());
            }
            bar.tick(player);
            return;
        }
        double bpp = player.isInsideVehicle() ? cfg.driveScale() : cfg.walkScale();
        Location target = plugin.gps().target(player);
        if (target != null && target.getWorld() != loc.getWorld()) {
            target = null;
        }
        RadarKey key = new RadarKey(
                (long) Math.floor(loc.getX() * 2 / bpp), (long) Math.floor(loc.getZ() * 2 / bpp),
                Math.floorMod(Math.round(loc.getYaw() / 4f), 90), bpp,
                target == null ? Long.MIN_VALUE : (long) target.getBlockX() * 100_003L + target.getBlockZ());
        long now = System.currentTimeMillis();
        Drawn last = drawn.get(player.getUniqueId());
        if (last == null || (!last.key().equals(key) && now - last.at() >= cfg.updateMs())) {
            drawn.put(player.getUniqueId(), new Drawn(key, now));
            RadarHud.Marker waypoint = target == null ? null : new RadarHud.Marker(target.getX(), target.getZ(), MapIcons.WAYPOINT);
            String radar = RadarHud.build(bpp >= 8 ? m : w, loc.getX(), loc.getZ(), loc.getYaw(), bpp, markers, waypoint);
            bar.radar(player, radar);
        }
        bar.tick(player);
    }

    /** 예전 버전의 손에 드는 "지도" 아이템을 치웁니다 */
    private static void removeLegacyItems(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && CustomItems.is(stack, "minimap")) {
                inv.setItem(i, null);
            }
        }
    }

    // ------------------------------------------------------------------ 큰 지도 (F키)

    /** F키: 총을 들었으면 재장전(GunService 가 먼저 처리), 아니면 큰 지도. Shift+F 는 원래대로 손 바꾸기 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() || !plugin.characters().has(player)) {
            return;
        }
        event.setCancelled(true);
        openMap(player);
    }

    public void openMap(Player player) {
        if (terrain == null || mid == null) {
            Text.send(player, "<gray>지도를 준비하는 중이에요. 잠시 뒤에 다시 눌러 주세요.");
            return;
        }
        if (!plugin.pack().ready(player)) {
            placeList(player);
            return;
        }
        Open last = open.get(player.getUniqueId());
        show(player, last == null ? 0 : last.zoom(), last == null ? Double.NaN : last.cx(), last == null ? Double.NaN : last.cz());
    }

    private void show(Player player, int zoom, double cx, double cz) {
        Location loc = player.getLocation();
        double[] min = terrain.layout().borderMin(), max = terrain.layout().borderMax();
        double bpp = zoom == 0 ? Math.max((max[0] - min[0]) / BigMap.W, (max[1] - min[1]) / BigMap.H) : ZOOM_BPP[zoom];
        if (Double.isNaN(cx)) {
            boolean inCity = terrain.insideBorder(loc.getX(), loc.getZ());
            cx = inCity ? loc.getX() : (min[0] + max[0]) / 2;
            cz = inCity ? loc.getZ() : (min[1] + max[1]) / 2;
        }
        if (zoom == 0) {
            cx = (min[0] + max[0]) / 2;
            cz = (min[1] + max[1]) / 2;
        } else {
            double hw = BigMap.W / 2.0 * bpp, hh = BigMap.H / 2.0 * bpp;
            cx = Math.max(min[0] + hw, Math.min(max[0] - hw, cx));
            cz = Math.max(min[1] + hh, Math.min(max[1] - hh, cz));
        }
        RadarRaster raster = zoom == 0 ? mid : walk;
        Viewport view = BigMap.view(cx, cz, bpp);
        Location target = plugin.gps().target(player);
        double[] waypoint = target == null || target.getWorld() != loc.getWorld() ? null : new double[]{target.getX(), target.getZ()};
        double[] me = new double[]{loc.getX(), loc.getZ(), loc.getYaw()};
        BigMap.Picture picture = BigMap.draw(raster, view, places, waypoint, me);

        long token = tokens.incrementAndGet();
        Open state = new Open(token, zoom, cx, cz, view, places);
        open.put(player.getUniqueId(), state);

        String title = "<gold><bold>지도</bold> <gray>· " + ZOOM_NAMES[zoom]
                + (target != null ? "  <white>· 길 안내 중" : "");
        final double fcx = cx, fcz = cz;
        double panX = BigMap.W / 2.0 * bpp, panZ = BigMap.H / 2.0 * bpp;
        Screen screen = new Screen(title)
                .body(mapComponent(picture, state), BigMap.W + 16)
                .body(legend(), 300)
                .columns(9)
                .noExitButton();
        screen.button("<white>＋", "확대", 30, () -> show(player, Math.min(ZOOM_BPP.length - 1, zoom + 1), zoom == 0 ? Double.NaN : fcx, fcz));
        screen.button("<white>－", "축소", 30, () -> show(player, Math.max(0, zoom - 1), fcx, fcz));
        screen.button("◀", "서쪽으로", 22, () -> show(player, zoom, fcx - panX, fcz));
        screen.button("▲", "북쪽으로", 22, () -> show(player, zoom, fcx, fcz - panZ));
        screen.button("▼", "남쪽으로", 22, () -> show(player, zoom, fcx, fcz + panZ));
        screen.button("▶", "동쪽으로", 22, () -> show(player, zoom, fcx + panX, fcz));
        screen.button("내 위치", "내 위치를 가운데로", 50, () -> show(player, Math.max(1, zoom), Double.NaN, Double.NaN));
        screen.button(target != null ? "<red>안내 끄기" : "<dark_gray>안내 끄기", "길 안내를 끕니다", 56, () -> {
            plugin.gps().stop(player);
            show(player, zoom, fcx, fcz);
        });
        screen.button("닫기", null, 40, () -> plugin.ui().close(player));
        plugin.ui().show(player, screen);
    }

    /** 지도 그림 + 마우스 영역 (구역 칸·장소 아이콘에 이름과 클릭 동작) */
    private Component mapComponent(BigMap.Picture picture, Open state) {
        List<Component> parts = new ArrayList<>();
        for (BigMap.Segment seg : picture.segments()) {
            Component c = Component.text(seg.text());
            if (seg.zone() >= 0) {
                c = c.hoverEvent(HoverEvent.showText(zoneHover(state, seg.zone())))
                        .clickEvent(ClickEvent.custom(Key.key("junseocity", ACTION + "/" + state.token() + "/z/" + seg.zone())));
            } else if (seg.place() >= 0) {
                BigMap.Place p = state.places().get(seg.place());
                c = c.hoverEvent(HoverEvent.showText(Text.mm("<white><bold>" + Text.esc(p.name()) + "</bold>\n<gray>누르면 여기로 길 안내")))
                        .clickEvent(ClickEvent.custom(Key.key("junseocity", ACTION + "/" + state.token() + "/p/" + seg.place())));
            }
            parts.add(c);
        }
        // 지도가 그려지는 높이만큼 빈 줄 (대화창이 그 높이를 차지하게)
        int lines = (BigMap.TOP_OFFSET + BigMap.H + 8) / 9;
        Component map = Component.textOfChildren(parts.toArray(Component[]::new))
                .font(MAP_FONT)
                .shadowColor(ShadowColor.none());
        return Component.textOfChildren(map, Component.text("\n".repeat(lines - 1) + " "));
    }

    private Component zoneHover(Open state, int zone) {
        double[] s = BigMap.zoneCenter(zone);
        double[] w = state.view().toWorld(s[0], s[1]);
        String name = areaName(w[0], w[1]);
        BigMap.Place near = placeInZone(state, zone);
        String text = "<white><bold>" + Text.esc(name) + "</bold>";
        if (near != null) {
            text += "\n<gray>근처: <white>" + Text.esc(near.name());
        }
        return Text.mm(text + "\n<gray>누르면 여기로 길 안내");
    }

    private String areaName(double x, double z) {
        String district = terrain.districtName((int) Math.floor(x), (int) Math.floor(z));
        CityBuildings b = buildings;
        Placement building = b == null ? null : b.at(x, z);
        if (district != null) {
            // 이름 있는 큰 건물이면 함께 (다세대·상가처럼 흔한 건물은 빼고)
            if (building != null && !building.kind.equals("house") && !building.kind.equals("shop")) {
                return district + " · " + building.name;
            }
            return district;
        }
        byte kind = mid.at(x, z);
        return switch (kind) {
            case RadarRaster.WATER -> "물가";
            case RadarRaster.OUTSIDE -> "바다";
            case RadarRaster.MOUNTAIN_LOW, RadarRaster.MOUNTAIN_HIGH -> "산";
            default -> "도시 바깥";
        };
    }

    /** 이 구역 칸 안에 있는 장소 (여럿이면 칸 가운데에 가까운 곳) */
    private static BigMap.Place placeInZone(Open state, int zone) {
        double[] c = BigMap.zoneCenter(zone);
        BigMap.Place best = null;
        double bestD = Double.MAX_VALUE;
        for (BigMap.Place p : state.places()) {
            double[] s = state.view().toScreen(p.x(), p.z());
            if (Math.abs(s[0] - c[0]) <= BigMap.ZONE / 2.0 && Math.abs(s[1] - c[1]) <= BigMap.ZONE / 2.0) {
                double d = Math.hypot(s[0] - c[0], s[1] - c[1]);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    private Component legend() {
        return Text.mm("<gray>").append(Component.textOfChildren(
                icon(MapIcons.PLAYER_UP), Text.mm(" <white>나  "),
                icon(MapIcons.WAYPOINT), Text.mm(" <white>목적지  "),
                icon(MapIcons.HOSPITAL), Text.mm(" <white>병원  "),
                icon(MapIcons.POLICE), Text.mm(" <white>경찰서  "),
                icon(MapIcons.AIRPORT), Text.mm(" <white>공항\n"),
                Text.mm("<gray>아이콘이나 지도에 마우스를 올리면 이름, 누르면 길 안내 · ESC 닫기")));
    }

    private static Component icon(int icon) {
        return Component.text(new String(Character.toChars(MapGlyphs.legend(icon))))
                .font(MAP_FONT)
                .shadowColor(ShadowColor.none());
    }

    /** 큰 지도 위를 눌렀을 때 ("토큰/z/칸번호" 또는 "토큰/p/장소번호") */
    private void onMapClick(Player player, String rest) {
        String[] parts = rest.split("/");
        Open state = open.get(player.getUniqueId());
        if (parts.length != 3 || state == null || !parts[0].equals(String.valueOf(state.token()))) {
            if (state != null) {
                show(player, state.zoom(), state.cx(), state.cz()); // 예전 지도의 클릭: 지금 지도로 다시
            }
            return;
        }
        int index;
        try {
            index = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }
        double x, z;
        String label;
        if (parts[1].equals("p") && index >= 0 && index < state.places().size()) {
            BigMap.Place p = state.places().get(index);
            x = p.x();
            z = p.z();
            label = p.name();
        } else if (parts[1].equals("z") && index >= 0 && index < BigMap.ZONE_COLS * BigMap.ZONE_ROWS) {
            BigMap.Place near = placeInZone(state, index);
            if (near != null) {
                x = near.x();
                z = near.z();
                label = near.name();
            } else {
                double[] w = state.view().toWorld(BigMap.zoneCenter(index)[0], BigMap.zoneCenter(index)[1]);
                x = w[0];
                z = w[1];
                label = areaName(x, z) + " (지도에서 고른 곳)";
            }
        } else {
            return;
        }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        Location target = new Location(player.getWorld(), bx + 0.5, terrain.standY(bx, bz), bz + 0.5);
        plugin.gps().start(player, target, label);
        bar.message(player, Text.mm("<aqua>길 안내: <white>" + Text.esc(label)));
        show(player, state.zoom(), state.cx(), state.cz());
    }

    /** 리소스팩이 없을 때 F키: 장소 목록으로 길 안내 */
    private void placeList(Player player) {
        Screen screen = new Screen("<gold><bold>지도</bold> <gray>· 장소 목록")
                .line("<gray>리소스팩을 받지 않아서 지도 그림 대신 목록으로 보여 드려요.")
                .line("<gray>누르면 길 안내를 켭니다.")
                .columns(3)
                .buttonWidth(110);
        for (BigMap.Place p : places) {
            screen.button("<white>" + Text.esc(p.name()), () -> {
                int bx = (int) Math.floor(p.x()), bz = (int) Math.floor(p.z());
                plugin.gps().start(player, new Location(player.getWorld(), bx + 0.5, terrain.standY(bx, bz), bz + 0.5), p.name());
                bar.message(player, Text.mm("<aqua>길 안내: <white>" + Text.esc(p.name())));
                plugin.ui().close(player);
            });
        }
        plugin.ui().show(player, screen);
    }
}
