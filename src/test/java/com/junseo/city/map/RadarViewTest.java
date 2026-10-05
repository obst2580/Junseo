package com.junseo.city.map;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarViewTest {
    static CityTerrain terrain;
    static RadarRaster fine;

    @BeforeAll
    static void setUp() throws IOException {
        Path file = Path.of(System.getProperty("layoutFile", "map/layout.json"));
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            terrain = CityTerrain.load(reader);
        }
        fine = RadarRaster.build(terrain, 2);
    }

    @Test
    void screenAndWorldRoundTrip() {
        for (boolean rotate : new boolean[]{true, false}) {
            RadarView view = new RadarView(100, 200, 2, 37f, rotate);
            double[] s = view.toScreen(150, 260);
            double[] w = view.toWorld(s[0], s[1]);
            assertEquals(150, w[0], 1e-6);
            assertEquals(260, w[1], 1e-6);
        }
    }

    @Test
    void radarPutsWhatIsAheadAtTheTop() {
        // 북쪽(yaw 180)을 보고 있으면 북쪽 50블록이 화면 위쪽 가운데
        RadarView north = new RadarView(0, 0, 2, 180f, true);
        double[] s = north.toScreen(0, -50);
        assertEquals(64, s[0], 1e-6);
        assertTrue(s[1] < 64);
        // 동쪽(yaw -90)을 보고 있으면 동쪽이 위, 남쪽이 오른쪽
        RadarView east = new RadarView(0, 0, 2, -90f, true);
        assertTrue(east.toScreen(50, 0)[1] < 64);
        assertTrue(east.toScreen(0, 50)[0] > 64);
    }

    @Test
    void fullMapKeepsNorthUp() {
        RadarView map = new RadarView(0, 0, 8, 73f, false);
        double[] north = map.toScreen(0, -100);
        assertEquals(64, north[0], 1e-6);
        assertTrue(north[1] < 64);
    }

    @Test
    void cursorAndDirectionMatchVanilla() {
        assertEquals(-128, RadarView.cursor(0));
        assertEquals(0, RadarView.cursor(64));
        assertEquals(127, RadarView.cursor(128));
        assertEquals(0, RadarView.direction(0f));     // 남쪽
        assertEquals(8, RadarView.direction(180f));   // 북쪽
        assertEquals(12, RadarView.direction(-90f));  // 동쪽
        double[] edge = RadarView.clampToEdge(new double[]{64 + 500, 64}, 3);
        assertEquals(1, edge[2]);
        assertEquals(125, edge[0], 1e-6);
    }

    @Test
    void coarseMapStillShowsRoadsAndWater() {
        RadarRaster coarse = fine.pooled(20);
        int roads = 0, water = 0;
        RadarView view = new RadarView(coarse.centerX(), coarse.centerZ(), 40, 0, false);
        byte[] out = new byte[128 * 128];
        view.draw(coarse, out);
        for (byte b : out) {
            if (b == RadarRaster.ROAD) {
                roads++;
            } else if (b == RadarRaster.WATER) {
                water++;
            }
        }
        assertTrue(roads > 300, "도시 전체 지도에도 도로가 보여야 해요: " + roads);
        assertTrue(water > 500, "한강·바다가 보여야 해요: " + water);
    }

    @Test
    void renderPreview() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        Layout.Hub plaza = terrain.layout().hubs().stream().filter(h -> h.id().equals("plaza")).findFirst().orElseThrow();
        write(new RadarView(plaza.x(), plaza.z(), 2, 180f, true), fine, new File(dir, "radar-plaza-north.png"));
        write(new RadarView(plaza.x(), plaza.z(), 2, -90f, true), fine, new File(dir, "radar-plaza-east.png"));
        write(new RadarView(plaza.x(), plaza.z(), 3.5, -60f, true), fine, new File(dir, "radar-plaza-car.png"));
        RadarRaster coarse = fine.pooled(20);
        write(new RadarView(coarse.centerX(), coarse.centerZ(), 40, 0, false), coarse, new File(dir, "map-city.png"));
        write(new RadarView(plaza.x(), plaza.z(), 8, 0, false), fine.pooled(4), new File(dir, "map-district.png"));
    }

    /** 128×128 을 4배로 키워 저장 */
    static void write(RadarView view, RadarRaster raster, File file) throws IOException {
        byte[] out = new byte[128 * 128];
        view.draw(raster, out);
        BufferedImage img = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 512; y++) {
            for (int x = 0; x < 512; x++) {
                img.setRGB(x, y, RadarPalette.color(out[(y / 4) * 128 + x / 4]).getRGB());
            }
        }
        // 가운데 표시 (내 위치)
        for (int d = -6; d <= 6; d++) {
            img.setRGB(256 + d, 256, 0xff3030);
            img.setRGB(256, 256 + d, 0xff3030);
        }
        ImageIO.write(img, "png", file);
    }
}
