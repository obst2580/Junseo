package com.junseo.citymap.terrain;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 생성기가 만들 지형을 위에서 내려다본 그림으로 저장합니다. 서버 없이 결과를 확인하는 용도입니다.
 * 실행: ./gradlew :citymap:test -DmapPreview=true  →  citymap/build/preview/
 */
class PreviewRenderTest {

    @Test
    void renderPreview() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        CityTerrain terrain = CityTerrainTest.load();
        File dir = new File("build/preview");
        dir.mkdirs();

        long start = System.nanoTime();
        // 전체: 1px = 2블록
        double[] min = terrain.layout().borderMin(), max = terrain.layout().borderMax();
        int w = (int) ((max[0] - min[0]) / 2), h = (int) ((max[1] - min[1]) / 2);
        ImageIO.write(render(terrain, (int) min[0], (int) min[1], w, h, 2), "png", new File(dir, "map-preview.png"));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        System.out.println("전체 미리보기 " + elapsed + "ms (" + (w * h) + "칸)");

        // 확대: 1px = 1블록. 설계도 크기가 바뀌어도 같은 곳이 나오게 구역·거점 기준으로
        double[] yeouido = terrain.districtCenter("yeouido");
        double[] namsan = terrain.districtCenter("namsan");
        double[] airport = terrain.districtCenter("airport");
        ImageIO.write(crop(terrain, yeouido, 520, 340), "png", new File(dir, "yeouido-1to1.png"));
        ImageIO.write(crop(terrain, namsan, 380, 480), "png", new File(dir, "namsan-tunnel-1to1.png"));
        ImageIO.write(crop(terrain, airport, 560, 380), "png", new File(dir, "airport-1to1.png"));
    }

    private static BufferedImage crop(CityTerrain terrain, double[] center, int w, int h) {
        return render(terrain, (int) center[0] - w / 2, (int) center[1] - h / 2, w, h, 1);
    }

    static BufferedImage render(CityTerrain terrain, int x0, int z0, int w, int h, int scale) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int x = x0 + px * scale, z = z0 + py * scale;
                Column c = terrain.column(x, z);
                img.setRGB(px, py, color(terrain, c, x, z, scale));
            }
        }
        return img;
    }

    static int color(CityTerrain terrain, Column c, int x, int z, int scale) {
        if (c.isWater() && !c.deck) {
            int depth = c.waterTop - c.groundY;
            double t = Math.min(1, depth / 25.0);
            return rgb(120 - 70 * t, 180 - 70 * t, 230 - 40 * t);
        }
        if (c.tunnel) {
            // 터널은 산 위로 보이지만 길이 보이게 보라색 점선 느낌
            return ((x + z) / 6) % 2 == 0 ? rgb(150, 90, 200) : shade(rgb(95, 140, 70), c.mountainHeight);
        }
        int base = switch (c.surface) {
            case GRASS -> c.mountainHeight > 0 ? rgb(95, 140, 70) : rgb(125, 175, 90);
            case ROCK -> rgb(140, 140, 140);
            case SAND -> rgb(225, 210, 150);
            case EMBANKMENT -> rgb(110, 110, 110);
            case ASPHALT -> rgb(80, 80, 85);
            case LINE_WHITE -> rgb(245, 245, 245);
            case LINE_YELLOW -> rgb(240, 200, 40);
            case SIDEWALK -> rgb(175, 175, 175);
            case BORDER_1 -> rgb(240, 140, 30);
            case BORDER_2 -> rgb(110, 200, 40);
            case BORDER_3 -> rgb(80, 160, 230);
            case PAD -> rgb(250, 240, 240);
            case RIVER_BED, SEA_BED -> rgb(200, 190, 150);
        };
        if (c.hubPillar > 0) {
            base = rgb(200, 20, 40);
        }
        if (c.mountainHeight > 0 && !c.isRoad()) {
            // 북서쪽에서 빛이 오는 것처럼 음영
            Column east = terrain.column(x + scale, z + scale);
            int diff = c.groundY - east.groundY;
            double light = Math.max(0.55, Math.min(1.35, 1 + diff * 0.08 / scale));
            base = scale(base, light);
            base = shade(base, c.mountainHeight);
        }
        return base;
    }

    static int shade(int rgb, int height) {
        // 높을수록 조금 밝게
        return scale(rgb, 0.85 + Math.min(0.35, height / 600.0));
    }

    static int scale(int rgb, double f) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return rgb(r * f, g * f, b * f);
    }

    static int rgb(double r, double g, double b) {
        int ri = (int) Math.max(0, Math.min(255, r)), gi = (int) Math.max(0, Math.min(255, g)), bi = (int) Math.max(0, Math.min(255, b));
        return (ri << 16) | (gi << 8) | bi;
    }
}
