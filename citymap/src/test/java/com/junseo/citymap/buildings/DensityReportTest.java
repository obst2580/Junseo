package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 분석용: 구역별 빈 땅·건물 높이 (-DmapPreview=true 일 때만) */
class DensityReportTest {
    @Test
    void report() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"));
        CityTerrain t = TestCity.terrain();
        CityBuildings b = TestCity.buildings();
        List<Placement> solid = b.placements().stream().filter(p -> !CityBuildings.isGround(p)).toList();
        Map<Placement, Integer> height = new HashMap<>();
        for (Placement p : solid) {
            Voxels v = p.voxels();
            height.put(p, v.y0 + v.h);
        }
        double[] min = t.layout().borderMin(), max = t.layout().borderMax();
        int X0 = (int) min[0], Z0 = (int) min[1], W = (int) (max[0] - min[0]), H = (int) (max[1] - min[1]);
        int S = 2;
        BufferedImage img = new BufferedImage(W / S, H / S, BufferedImage.TYPE_INT_RGB);
        boolean[][] empty = new boolean[H][W];
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-11s %7s %7s %7s %7s %6s %6s %6s  %s%n", "district", "area", "road", "build", "empty", "empty%", "avgH", "maxH", "empty blocks>=400"));
        for (Layout.District d : t.layout().districts()) {
            Polygon area = Plans.district(t, d.id());
            BuildMask m = BuildMask.of(t, area, 2);
            int build = 0;
            double[] bb = area.bounds();
            for (int z = (int) bb[1]; z <= bb[3]; z++) {
                for (int x = (int) bb[0]; x <= bb[2]; x++) {
                    if (m.free(x, z)) {
                        build++;
                    }
                }
            }
            GuincheonPlan.claimAll(m, solid);
            int areaN = 0, road = 0, emptyN = 0;
            for (int z = (int) bb[1]; z <= bb[3]; z++) {
                for (int x = (int) bb[0]; x <= bb[2]; x++) {
                    if (!area.contains(x + 0.5, z + 0.5)) {
                        continue;
                    }
                    areaN++;
                    Column c = t.column(x, z);
                    if (c.isRoad()) {
                        road++;
                    }
                    if (m.free(x, z)) {
                        emptyN++;
                        if (z - Z0 >= 0 && z - Z0 < H && x - X0 >= 0 && x - X0 < W) {
                            empty[z - Z0][x - X0] = true;
                        }
                    }
                }
            }
            List<String> blocks = new ArrayList<>();
            for (int[] k : m.blocks()) {
                if (k[4] >= 400) {
                    blocks.add((k[2] - k[0] + 1) + "x" + (k[3] - k[1] + 1) + "@" + ((k[0] + k[2]) / 2) + "," + ((k[1] + k[3]) / 2) + "(" + k[4] + ")");
                }
            }
            int n = 0;
            long sum = 0;
            int mx = 0;
            for (Placement p : solid) {
                double[] pb = p.boundsRef();
                if (area.contains((pb[0] + pb[2]) / 2, (pb[1] + pb[3]) / 2)) {
                    int hh = height.get(p);
                    n++;
                    sum += (long) hh * (long) ((pb[2] - pb[0]) * (pb[3] - pb[1]));
                    mx = Math.max(mx, hh);
                }
            }
            double fp = 0;
            for (Placement p : solid) {
                double[] pb = p.boundsRef();
                if (area.contains((pb[0] + pb[2]) / 2, (pb[1] + pb[3]) / 2)) {
                    fp += (pb[2] - pb[0]) * (pb[3] - pb[1]);
                }
            }
            sb.append(String.format("%-11s %7d %7d %7d %7d %5.0f%% %6.1f %6d  n=%d %s%n", d.id(), areaN, road, build, emptyN,
                    build == 0 ? 0 : 100.0 * emptyN / build, fp == 0 ? 0 : sum / fp, mx, n, blocks));
        }
        // 그림: 건물 높이(진할수록 높음), 빈 땅 빨강, 길 회색, 물 파랑, 산 초록
        for (int j = 0; j < H / S; j++) {
            for (int i = 0; i < W / S; i++) {
                int x = X0 + i * S, z = Z0 + j * S;
                Column c = t.column(x, z);
                int rgb;
                if (c.isWater()) {
                    rgb = 0x3A6EA5;
                } else if (c.isRoad()) {
                    rgb = 0x707070;
                } else if (c.mountainHeight > 0) {
                    rgb = 0x5E8C4A;
                } else {
                    rgb = 0xB9D79E;
                }
                Placement p = b.at(x + 0.5, z + 0.5);
                if (p != null) {
                    int hh = Math.min(100, height.get(p));
                    int g = 235 - hh * 2;
                    rgb = p.kind.equals("parking") ? 0x9AA4B0 : p.kind.equals("plaza") || p.kind.equals("park") ? 0x7FB069 : (g << 16) | (g << 8) | Math.min(255, g + 20);
                } else if (empty[z - Z0][x - X0]) {
                    rgb = 0xE0453A;
                }
                img.setRGB(i, j, rgb);
            }
        }
        File dir = new File("build/preview");
        dir.mkdirs();
        ImageIO.write(img, "png", new File(dir, "density-empty.png"));
        int[][] crops = {{-300, -460, -60, -300}, {520, 330, 800, 620}, {560, -200, 960, 120}, {220, -250, 620, 160}};
        String[] names = {"hongdae", "gangnam", "namsan", "yongsan"};
        for (int q = 0; q < crops.length; q++) {
            int[] c = crops[q];
            int cw = c[2] - c[0], ch = c[3] - c[1];
            BufferedImage ci = new BufferedImage(cw * 3, ch * 3, BufferedImage.TYPE_INT_RGB);
            for (int j = 0; j < ch; j++) {
                for (int i = 0; i < cw; i++) {
                    int x = c[0] + i, z = c[1] + j;
                    Column col = t.column(x, z);
                    int rgb = col.isWater() ? 0x3A6EA5 : col.isRoad() ? 0x707070 : col.mountainHeight > 0 ? 0x5E8C4A - Math.min(40, col.mountainHeight) * 0x000100 : 0xB9D79E;
                    Placement p = b.at(x + 0.5, z + 0.5);
                    if (p != null) {
                        int hh = Math.min(100, height.get(p));
                        int g = 235 - hh * 2;
                        rgb = p.kind.equals("parking") ? 0x9AA4B0 : p.kind.equals("plaza") || p.kind.equals("park") ? 0x7FB069 : (g << 16) | (g << 8) | Math.min(255, g + 20);
                    } else if (z - Z0 >= 0 && z - Z0 < H && x - X0 >= 0 && x - X0 < W && empty[z - Z0][x - X0]) {
                        rgb = 0xE0453A;
                    }
                    for (int a = 0; a < 3; a++) {
                        for (int bb2 = 0; bb2 < 3; bb2++) {
                            ci.setRGB(i * 3 + a, j * 3 + bb2, rgb);
                        }
                    }
                }
            }
            ImageIO.write(ci, "png", new File(dir, "density-" + names[q] + ".png"));
        }
        System.out.println(sb);
        java.nio.file.Files.writeString(new File(dir, "density.txt").toPath(), sb);
    }

    /** 도시 전체 위에서 본 그림: 칸마다 가장 높은 블록 색, 높이 그늘(남동쪽으로 그림자) */
    @Test
    void cityMap() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"));
        CityTerrain t = TestCity.terrain();
        CityBuildings b = TestCity.buildings();
        double[] min = t.layout().borderMin(), max = t.layout().borderMax();
        int X0 = (int) min[0], Z0 = (int) min[1], W = (int) (max[0] - min[0]), H = (int) (max[1] - min[1]);
        int[] top = new int[W * H], rgb = new int[W * H];
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++) {
                int x = X0 + i, z = Z0 + j;
                Column c = t.column(x, z);
                int[] best = {c.isWater() ? c.waterTop : c.groundY, c.isWater() ? 0x3A6EA5 : IsoRender.surfaceColor(c)};
                b.column(x, z, c, (y, block) -> {
                    if (y >= best[0] && !block.isAir() && block.rgb() >= 0) {
                        best[0] = y;
                        best[1] = block.rgb();
                    }
                });
                top[j * W + i] = best[0];
                rgb[j * W + i] = best[1];
            }
        }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++) {
                int h = top[j * W + i];
                // 북서쪽에서 해가 비춤: 북서쪽 이웃보다 낮으면 그늘
                int shade = 0;
                for (int k = 1; k <= 24 && i - k >= 0 && j - k >= 0; k++) {
                    if (top[(j - k) * W + (i - k)] - h > k) {
                        shade = 1;
                        break;
                    }
                }
                double lit = shade == 1 ? 0.62 : 1.0;
                int c = rgb[j * W + i];
                int rr = (int) (((c >> 16) & 255) * lit), gg = (int) (((c >> 8) & 255) * lit), bb = (int) ((c & 255) * lit);
                img.setRGB(i, j, (rr << 16) | (gg << 8) | bb);
            }
        }
        File dir = new File("build/preview");
        dir.mkdirs();
        ImageIO.write(img, "png", new File(dir, "city-map.png"));
        int[][] crops = {{-450, -560, 650, 200}, {350, -600, 1550, 200}, {350, 120, 1700, 900}, {-1700, -100, -100, 1000}};
        String[] names = {"city-map-west", "city-map-north", "city-map-east", "city-map-airport"};
        for (int q = 0; q < crops.length; q++) {
            int[] cr = crops[q];
            int x0 = Math.max(0, cr[0] - X0), z0 = Math.max(0, cr[1] - Z0), x1 = Math.min(W, cr[2] - X0), z1 = Math.min(H, cr[3] - Z0);
            ImageIO.write(img.getSubimage(x0, z0, x1 - x0, z1 - z0), "png", new File(dir, names[q] + ".png"));
        }
    }
}
