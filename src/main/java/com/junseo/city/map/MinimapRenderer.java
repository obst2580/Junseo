package com.junseo.city.map;

import com.junseo.citymap.layout.Layout;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지도 아이템 그리기 (플레이어마다 따로).
 * <ul>
 *   <li>왼손: 미니맵. GTA 레이더처럼 보는 방향이 위, 차를 타면 더 넓게</li>
 *   <li>오른손: 전체 지도. 북쪽이 위, 우클릭으로 도시 → 구역 → 동네 확대</li>
 * </ul>
 * 움직이지 않으면 다시 그리지 않아서 서버와 네트워크 부담을 줄입니다.
 */
final class MinimapRenderer extends MapRenderer {
    private static final long RADAR_INTERVAL_MS = 150;
    private static final long MAP_INTERVAL_MS = 250;

    /** 마지막으로 그린 상태. 같으면 다시 그리지 않음 */
    private record Drawn(boolean big, MinimapService.Zoom zoom, int x, int z, int yaw, int targetHash, long at) {
    }

    private final MinimapService service;
    private final Map<UUID, Drawn> drawn = new ConcurrentHashMap<>();

    MinimapRenderer(MinimapService service) {
        super(true);
        this.service = service;
    }

    void forget(UUID uuid) {
        drawn.remove(uuid);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        RadarRaster fine = service.fine();
        if (fine == null) {
            return; // 아직 지도를 만드는 중
        }
        boolean big = MinimapService.isMinimap(player.getInventory().getItemInMainHand());
        MinimapService.Zoom zoom = big ? service.zoom(player) : null;
        Location loc = player.getLocation();
        Location target = service.gpsTarget(player);

        // 바뀐 게 없거나 너무 자주 그리려 하면 건너뜀
        double bpp = big ? zoom.blocksPerPixel : (player.isInsideVehicle() ? 3.5 : 2.0);
        int qx = (int) Math.floor(loc.getX() / Math.max(1, bpp));
        int qz = (int) Math.floor(loc.getZ() / Math.max(1, bpp));
        int qyaw = big ? 0 : Math.round(loc.getYaw() / 4f);
        int targetHash = target == null ? 0 : (target.getBlockX() * 31 + target.getBlockZ());
        long now = System.currentTimeMillis();
        Drawn last = drawn.get(player.getUniqueId());
        if (last != null) {
            boolean sameMode = last.big == big && last.zoom == zoom && last.targetHash == targetHash;
            boolean same = sameMode && last.x == qx && last.z == qz && last.yaw == qyaw;
            long interval = big ? MAP_INTERVAL_MS : RADAR_INTERVAL_MS;
            if (same || (sameMode && now - last.at < interval)) {
                return;
            }
        }
        drawn.put(player.getUniqueId(), new Drawn(big, zoom, qx, qz, qyaw, targetHash, now));

        RadarView view;
        RadarRaster raster;
        if (!big) {
            view = new RadarView(loc.getX(), loc.getZ(), bpp, loc.getYaw(), true);
            raster = fine;
        } else if (zoom == MinimapService.Zoom.CITY) {
            RadarRaster coarse = service.coarse();
            view = new RadarView(coarse.centerX(), coarse.centerZ(), zoom.blocksPerPixel, 0, false);
            raster = coarse;
        } else {
            view = new RadarView(loc.getX(), loc.getZ(), zoom.blocksPerPixel, 0, false);
            raster = zoom == MinimapService.Zoom.DISTRICT ? service.mid() : fine;
        }

        byte[] kinds = new byte[RadarView.SIZE * RadarView.SIZE];
        view.draw(raster, kinds);
        for (int y = 0; y < RadarView.SIZE; y++) {
            for (int x = 0; x < RadarView.SIZE; x++) {
                canvas.setPixelColor(x, y, RadarPalette.color(kinds[y * RadarView.SIZE + x]));
            }
        }
        canvas.setCursors(cursors(player, loc, view, big, zoom, target));
    }

    private MapCursorCollection cursors(Player player, Location loc, RadarView view, boolean big,
                                        MinimapService.Zoom zoom, Location target) {
        MapCursorCollection out = new MapCursorCollection();
        Layout layout = service.layout();

        if (big && zoom == MinimapService.Zoom.CITY) {
            // 도시 전체: 구역 이름
            for (Layout.District d : layout.districts()) {
                double[] c = service.terrain().districtCenter(d.id());
                add(out, view.toScreen(c[0], c[1]), (byte) 0, MapCursor.Type.BANNER_WHITE, Component.text(d.name()));
            }
        } else {
            // 확대 화면과 미니맵: 주요 장소 (미니맵에는 이름 없이 아이콘만)
            for (Layout.Hub h : layout.hubs()) {
                MapCursor.Type type = hubIcon(h.id());
                if (type == null) {
                    continue;
                }
                double[] s = view.toScreen(h.x(), h.z());
                if (s[0] < 2 || s[1] < 2 || s[0] > RadarView.SIZE - 2 || s[1] > RadarView.SIZE - 2) {
                    continue;
                }
                add(out, s, (byte) 0, type, big ? Component.text(shortName(h.name())) : null);
            }
        }

        // 길 안내 목적지 (화면 밖이면 가장자리에)
        if (target != null && target.getWorld() == loc.getWorld()) {
            double[] s = RadarView.clampToEdge(view.toScreen(target.getX(), target.getZ()), 3);
            add(out, s, (byte) 0, s[2] > 0 ? MapCursor.Type.RED_MARKER : MapCursor.Type.TARGET_X, null);
        }

        // 나
        double[] me = view.toScreen(loc.getX(), loc.getZ());
        boolean inside = me[0] >= 0 && me[1] >= 0 && me[0] < RadarView.SIZE && me[1] < RadarView.SIZE;
        if (!big) {
            add(out, me, RadarView.UP, MapCursor.Type.PLAYER, null);
        } else if (inside) {
            add(out, me, RadarView.direction(loc.getYaw()), MapCursor.Type.PLAYER, null);
        } else {
            add(out, RadarView.clampToEdge(me, 2), (byte) 0, MapCursor.Type.PLAYER_OFF_MAP, null);
        }
        return out;
    }

    private static void add(MapCursorCollection out, double[] screen, byte direction, MapCursor.Type type,
                            Component caption) {
        out.addCursor(new MapCursor(RadarView.cursor(screen[0]), RadarView.cursor(screen[1]), direction, type, true, caption));
    }

    /** 지도에 보여 줄 장소와 아이콘 색 (없으면 안 보여 줌) */
    private static MapCursor.Type hubIcon(String id) {
        return switch (id) {
            case "spawn" -> MapCursor.Type.BANNER_LIGHT_BLUE;
            case "plaza" -> MapCursor.Type.BANNER_YELLOW;
            case "hospital" -> MapCursor.Type.BANNER_RED;
            case "police_north", "police_south" -> MapCursor.Type.BANNER_BLUE;
            case "station", "cityhall" -> MapCursor.Type.BANNER_WHITE;
            case "port", "prison" -> MapCursor.Type.BANNER_GRAY;
            case "bank_hq", "depot", "mall" -> MapCursor.Type.BANNER_GREEN;
            default -> null;
        };
    }

    /** "공항 터미널 (스폰·입국 심사)" → "공항 터미널" */
    static String shortName(String name) {
        int i = name.indexOf(" (");
        return i > 0 ? name.substring(0, i) : name;
    }
}
