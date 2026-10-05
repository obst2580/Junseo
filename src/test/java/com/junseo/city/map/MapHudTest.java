package com.junseo.city.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.junseo.city.hud.HudLayout;
import com.junseo.city.hud.TextWidth;
import com.junseo.city.pack.PackBuilder;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 리소스팩 글꼴 + 미니맵·큰 지도 글자열을 클라이언트 규칙대로 그려 보고, 의도한 그림과 같은지 검사합니다 */
class MapHudTest {
    static CityTerrain terrain;
    static RadarRaster walk;
    static RadarRaster mid;
    static RadarRaster coarse;
    static PackBuilder.Pack pack;
    static List<RadarHud.Marker> markers = new ArrayList<>();
    static List<BigMap.Place> places = new ArrayList<>();

    @BeforeAll
    static void setUp() throws IOException {
        Path file = Path.of(System.getProperty("layoutFile", "map/layout.json"));
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            terrain = CityTerrain.load(reader);
        }
        RadarRaster fine = RadarRaster.build(terrain, 2);
        walk = fine.pooled(2);
        mid = fine.pooled(4);
        coarse = fine.pooled(8);
        pack = PackBuilder.build();
        for (Layout.Hub h : terrain.layout().hubs()) {
            int icon = MapIcons.forHub(h.id());
            if (icon >= 0) {
                markers.add(new RadarHud.Marker(h.x(), h.z(), icon));
                places.add(new BigMap.Place(h.id(), BigMap.shortName(h.name()), icon, h.x(), h.z()));
            }
        }
    }

    // ------------------------------------------------------------------ 리소스팩

    @Test
    void glyphWidthsMatchWhatTheDrawersAssume() {
        PackBuilder.FontModel font = pack.font();
        for (MapGlyphs.RunSet set : List.of(MapGlyphs.HUD, MapGlyphs.BIG)) {
            for (int row = 0; row < set.rows(); row++) {
                for (int k = 0; k < MapGlyphs.KINDS; k++) {
                    for (int l = 0; l < set.lengthCount(); l++) {
                        PackBuilder.Glyph g = font.bitmaps().get(set.codepoint(row, k, l));
                        assertNotNull(g);
                        assertEquals(MapGlyphs.runAdvance(set.length(l)), g.advance());
                        assertEquals(1 - row, g.ascent());
                        assertTrue(g.ascent() <= g.height(), "ascent 는 height 보다 클 수 없음");
                    }
                }
            }
        }
        for (int row = MapGlyphs.ICON_MIN_ROW; row <= MapGlyphs.ICON_MAX_ROW; row++) {
            for (int i = 0; i < MapIcons.COUNT; i++) {
                PackBuilder.Glyph g = font.bitmaps().get(MapGlyphs.icon(row, i));
                assertEquals(MapIcons.advance(i), g.advance(), "icon " + i);
                assertTrue(g.ascent() <= g.height());
            }
        }
        assertEquals(RadarHud.frameAdvance(), font.advance(MapGlyphs.FRAME));
        for (int j = 0; j < MapGlyphs.hotspotRows(); j++) {
            assertEquals(MapGlyphs.hotspotAdvance(), font.advance(MapGlyphs.hotspot(j)));
        }
        for (int d = -MapGlyphs.SPACE_RANGE; d <= MapGlyphs.SPACE_RANGE; d++) {
            assertEquals(d, font.advance(MapGlyphs.space(d)));
        }
        // 미니맵에 쓰는 글자는 모두 3바이트 글자(BMP) 라서 네트워크로 보낼 때 작음
        assertTrue(MapGlyphs.HUD.lastCodepoint() < 0xF700);
    }

    @Test
    void packIsValidAndAlwaysTheSame() throws IOException {
        assertEquals(pack.sha1(), PackBuilder.build().sha1(), "같은 내용이면 같은 zip");
        assertTrue(pack.zip().length < 400_000, "리소스팩 크기 " + pack.zip().length);
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pack.zip()))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                files.put(e.getName(), zip.readAllBytes());
            }
        }
        JsonObject meta = JsonParser.parseString(new String(files.get("pack.mcmeta"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(meta.getAsJsonObject("pack").get("min_format").getAsInt() > 64);
        JsonObject font = JsonParser.parseString(new String(files.get("assets/junseocity/font/map.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray providers = font.getAsJsonArray("providers");
        int glyphs = 0;
        for (JsonElement el : providers) {
            JsonObject p = el.getAsJsonObject();
            if (p.get("type").getAsString().equals("bitmap")) {
                String path = "assets/junseocity/textures/" + p.get("file").getAsString().substring("junseocity:".length());
                assertNotNull(files.get(path), path);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(files.get(path)));
                JsonArray chars = p.getAsJsonArray("chars");
                int cols = chars.get(0).getAsString().codePointCount(0, chars.get(0).getAsString().length());
                assertEquals(0, img.getWidth() % cols);
                assertEquals(p.get("height").getAsInt(), img.getHeight() / chars.size(), "배율 1");
                assertTrue(p.get("ascent").getAsInt() <= p.get("height").getAsInt());
                glyphs += cols * chars.size();
            }
        }
        assertEquals(pack.font().bitmaps().size(), glyphs);
    }

    // ------------------------------------------------------------------ 미니맵

    @Test
    void radarDrawsExactlyTheMapInsideItsShape() {
        Random rnd = new Random(7);
        long bytes = 0, maxBytes = 0;
        int n = 60;
        for (int i = 0; i < n; i++) {
            double x = -2200 + rnd.nextDouble() * 4300, z = -1200 + rnd.nextDouble() * 2400;
            float yaw = rnd.nextFloat() * 720 - 360;
            boolean driving = i % 2 == 1;
            double bpp = driving ? 10 : 6;
            RadarRaster raster = driving ? mid : walk;
            String radar = RadarHud.build(raster, x, z, yaw, bpp, markers, new RadarHud.Marker(0, 0, MapIcons.WAYPOINT));

            ClientTextSim sim = new ClientTextSim(pack.font(), RadarHud.W + 20, RadarHud.H + 30, 10, 4);
            sim.draw(radar, cp -> cp < 0xF680);
            assertEquals(0, sim.cursor, "미니맵 글자열은 폭이 0 이어야 메시지 자리가 안 바뀜");

            Viewport view = new Viewport(x, z, bpp, yaw, true, RadarHud.CENTER_X, RadarHud.CENTER_Y);
            byte[] kinds = new byte[RadarHud.W];
            for (int y = -2; y < RadarHud.H + 2; y++) {
                if (y >= 0 && y < RadarHud.H) {
                    view.sampleRow(raster, y, 0, RadarHud.W, kinds);
                }
                for (int px = -2; px < RadarHud.W + 2; px++) {
                    int got = sim.pixels[(4 + 6 + y) * sim.width + 10 + px];
                    if (RadarHud.inside(px, y)) {
                        int want = 0xFF000000 | RadarPalette.color(kinds[px]).getRGB();
                        assertEquals(want, got, "(" + px + "," + y + ") at " + x + "," + z);
                    } else {
                        assertEquals(0, got, "모양 밖에 그림 (" + px + "," + y + ")");
                    }
                }
            }
            long b = modifiedUtf8(radar);
            bytes += b;
            maxBytes = Math.max(maxBytes, b);
        }
        System.out.println("미니맵 한 장 평균 " + bytes / n + " 바이트, 최대 " + maxBytes);
        assertTrue(bytes / n < 6000, "평균 " + bytes / n);
        assertTrue(maxBytes < 30000, "최대 " + maxBytes);
    }

    @Test
    void radarStaysInPlaceWhateverTheMessage() {
        double[] plaza = hub("plaza");
        String radar = RadarHud.build(walk, plaza[0], plaza[1], 30, 6, markers, null);
        for (HudLayout.Side side : HudLayout.Side.values()) {
            for (float textWidth : new float[]{0, 1, 7, 8.5f, 33, 120.5f, 301}) {
                int lead = HudLayout.radarLead(textWidth, side);
                int start = -((int) Math.ceil(textWidth) / 2);
                assertEquals(HudLayout.radarLeft(side), start + lead);
                GlyphWriter w = new GlyphWriter();
                w.move(lead);
                GlyphWriter back = new GlyphWriter();
                back.move(-lead);
                ClientTextSim sim = new ClientTextSim(pack.font(), 10, 10, 0, 0);
                sim.draw(w + radar + back, cp -> false);
                assertEquals(0, sim.cursor, "미니맵 부분은 폭 0");
            }
        }
    }

    @Test
    void textWidthsFollowTheClientFont() {
        assertEquals(24, TextWidth.of("가나다", false));
        assertEquals(25.5f, TextWidth.of("가나다", true));
        assertEquals(24, TextWidth.of("Hello", false)); // H6 e6 l3 l3 o6
        assertEquals(29, TextWidth.of("Hello", true));
        assertEquals(4, TextWidth.of(" ", false));
        assertEquals(59, TextWidth.of("72 km/h 속도", false)); // 7 2 _ k m / h _ 속 도 = 6+6+4+5+6+6+6+4+8+8
    }

    // ------------------------------------------------------------------ 큰 지도

    @Test
    void bigMapDrawsTheWholeCityWithMouseAreas() {
        double[] min = terrain.layout().borderMin(), max = terrain.layout().borderMax();
        double bpp = Math.max((max[0] - min[0]) / BigMap.W, (max[1] - min[1]) / BigMap.H);
        Viewport view = BigMap.view((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, bpp);
        double[] hospital = hub("hospital"), plaza = hub("plaza");
        BigMap.Picture picture = BigMap.draw(coarse, view, places, hospital, new double[]{plaza[0], plaza[1], 45});
        StringBuilder all = new StringBuilder();
        for (BigMap.Segment s : picture.segments()) {
            all.append(s.text());
        }
        assertTrue(picture.maxCursor() <= BigMap.W + 1, "대화창 줄 폭을 넘으면 줄이 바뀜: " + picture.maxCursor());

        ClientTextSim sim = new ClientTextSim(pack.font(), BigMap.W + 20, BigMap.H + 30, 8, 2);
        sim.draw(all.toString(), cp -> cp >= 0xF0000 && cp <= MapGlyphs.BIG.lastCodepoint());
        assertEquals(BigMap.W, sim.cursor, "줄 폭 = 지도 폭이어야 가운데 정렬이 맞음");
        byte[] kinds = new byte[BigMap.W];
        for (int y = 0; y < BigMap.H; y++) {
            view.sampleRow(coarse, y, 0, BigMap.W, kinds);
            for (int x = 0; x < BigMap.W; x++) {
                int want = 0xFF000000 | RadarPalette.color(kinds[x]).getRGB();
                assertEquals(want, sim.pixels[(2 + BigMap.TOP_OFFSET + y) * sim.width + 8 + x], "(" + x + "," + y + ")");
            }
        }

        // 마우스 칸: 16×16 칸마다 하나씩, 제자리에
        int zones = 0;
        int cursor = 0;
        for (BigMap.Segment s : picture.segments()) {
            if (s.zone() >= 0) {
                double[] c = BigMap.zoneCenter(s.zone());
                assertEquals((int) (c[0] - BigMap.ZONE / 2.0), cursor);
                assertEquals(MapGlyphs.hotspot((int) (c[1] / BigMap.ZONE)), s.text().codePointAt(0));
                zones++;
            }
            ClientTextSim step = new ClientTextSim(pack.font(), 1, 1, 0, 0);
            step.draw(s.text(), cp -> false);
            cursor += step.cursor;
        }
        assertEquals(BigMap.ZONE_COLS * BigMap.ZONE_ROWS, zones);
        long places = picture.segments().stream().filter(s -> s.place() >= 0).count();
        assertTrue(places >= 15, "장소 아이콘 " + places);
        System.out.println("큰 지도 색 막대 " + picture.runs() + "개, 글자열 " + modifiedUtf8(all.toString()) + " 바이트");
    }

    /** 리소스팩·미니맵·큰 지도 미리보기 (./gradlew test -DmapPreview=true → build/preview/) */
    @Test
    void writePreviews() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"));
        File dir = new File("build/preview");
        dir.mkdirs();
        // 게임 화면(GUI 480×270, 1080p 기본 크기)에 미니맵 얹은 모습 3장
        double[] plaza = hub("plaza"), police = hub("police_south"), hospital = hub("hospital");
        // 공항고속도로가 해협 대교를 건너 청라에 닿는 곳
        double[] bridge = terrain.layout().roads().stream().filter(r -> r.id().equals("H1")).findFirst().orElseThrow().line().get(3);
        double[][] spots = {{plaza[0] + 8, plaza[1] + 14, 160, 6}, {bridge[0], bridge[1], -60, 10}, {police[0], police[1] - 40, 200, 6}};
        String[] names = {"여의도 광장에서 걷기 (북쪽 보기)", "공항 다리를 차로 건너기", "강남 경찰서 근처"};
        int gw = 480, gh = 270, scale = 2, crop = 96;
        BufferedImage sheet = new BufferedImage(gw * scale, crop * scale * spots.length, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < spots.length; i++) {
            double[] s = spots[i];
            String radar = RadarHud.build(s[3] >= 8 ? mid : walk, s[0], s[1], (float) s[2], s[3], markers,
                    new RadarHud.Marker(hospital[0], hospital[1], MapIcons.WAYPOINT));
            ClientTextSim sim = new ClientTextSim(pack.font(), gw, gh, gw / 2 + HudLayout.radarLeft(HudLayout.Side.LEFT),
                    gh - 72);
            fakeScreen(sim.pixels, gw, gh, i);
            sim.draw(radar);
            int[] bottom = java.util.Arrays.copyOfRange(sim.pixels, (gh - crop) * gw, gh * gw);
            paste(sheet, bottom, gw, crop, 0, crop * i, scale);
        }
        ImageIO.write(sheet, "png", new File(dir, "hud-minimap.png"));
        System.out.println("미리보기: " + String.join(" / ", names));

        // 큰 지도 (대화창 모습)
        double[] min = terrain.layout().borderMin(), max = terrain.layout().borderMax();
        double bpp = Math.max((max[0] - min[0]) / BigMap.W, (max[1] - min[1]) / BigMap.H);
        int[][] views = {{0}, {1}};
        BufferedImage maps = new BufferedImage((BigMap.W + 24) * 3, (BigMap.H + 24) * 3 * views.length, BufferedImage.TYPE_INT_ARGB);
        for (int v = 0; v < views.length; v++) {
            Viewport view = views[v][0] == 0
                    ? BigMap.view((min[0] + max[0]) / 2, (min[1] + max[1]) / 2, bpp)
                    : BigMap.view(plaza[0] + 100, plaza[1] + 150, 6);
            BigMap.Picture picture = BigMap.draw(views[v][0] == 0 ? mid : walk, view, places, hospital,
                    new double[]{plaza[0], plaza[1], 135});
            StringBuilder all = new StringBuilder();
            picture.segments().forEach(s -> all.append(s.text()));
            ClientTextSim sim = new ClientTextSim(pack.font(), BigMap.W + 24, BigMap.H + 24, 12, 12 - BigMap.TOP_OFFSET);
            java.util.Arrays.fill(sim.pixels, 0xFF101010);
            sim.draw(all.toString());
            paste(maps, sim.pixels, sim.width, sim.height, 0, (BigMap.H + 24) * v, 3);
        }
        ImageIO.write(maps, "png", new File(dir, "big-map.png"));
    }

    /** 설계도의 거점 좌표 */
    private static double[] hub(String id) {
        Layout.Hub h = terrain.layout().hubs().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
        return new double[]{h.x(), h.z()};
    }

    /** 배경(하늘·땅)과 핫바 자리 */
    private static void fakeScreen(int[] px, int w, int h, int variant) {
        int[] sky = {0xFF87B5E8, 0xFF9CC3E6, 0xFF7FA9D9};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = y < h * 0.55 ? sky[variant] : 0xFF6E8B5A - ((y * 3) & 0x0F0F0F);
            }
        }
        int hx = w / 2 - 91, hy = h - 22;
        for (int y = hy; y < h; y++) {
            for (int x = hx; x < hx + 182; x++) {
                boolean border = y == hy || y == h - 1 || (x - hx) % 20 == 0;
                px[y * w + x] = border ? 0xFF2B2B2B : 0xFF8B8B8B;
            }
        }
    }

    private static void paste(BufferedImage dst, int[] px, int w, int h, int ox, int oy, int scale) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = px[y * w + x];
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        dst.setRGB((ox + x) * scale + sx, (oy + y) * scale + sy, v);
                    }
                }
            }
        }
    }

    /** 네트워크(NBT 문자열)로 보낼 때 크기: 글자 단위(UTF-16) 마다 1~3바이트 */
    private static long modifiedUtf8(String s) {
        long n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            n += c >= 1 && c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
        }
        return n;
    }
}
