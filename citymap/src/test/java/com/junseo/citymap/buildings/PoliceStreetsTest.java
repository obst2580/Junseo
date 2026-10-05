package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 용산·강남 경찰서, 이태원 거리, 홍대 클럽 거리: 걸어서 모든 층에 가는지와 미리보기 그림 */
class PoliceStreetsTest {

    static Voxels yongsan() {
        return PoliceStation.build(89, 60, new Random(7), PoliceStation.Style.CLASSIC, "용산경찰서", 44, 10);
    }

    static Voxels gangnam() {
        return PoliceStation.build(95, 60, new Random(8), PoliceStation.Style.MODERN, "강남경찰서", 35, 10);
    }

    @Test
    void policeStationsHaveStairsToEveryFloor() {
        InteriorTest.assertAllFloorsReachable("용산경찰서", yongsan(), PoliceStation.levels());
        InteriorTest.assertAllFloorsReachable("강남경찰서", gangnam(), PoliceStation.levels());
    }

    /** 거리 건물 견본: 이태원 식당·펍·옥상 바, 홍대 클럽(지하 두 층)·라이브홀·PC방·편의점 */
    static StreetBuilding[] streetSamples() {
        StreetBuilding.Spec a = new StreetBuilding.Spec();
        a.ground = new StreetBuilding.Shop(StreetBuilding.Use.RESTAURANT, "이스탄불 케밥", "Kebab", "kebab");
        a.upper = new StreetBuilding.Shop[]{new StreetBuilding.Shop(StreetBuilding.Use.BAR, "런던 펍", "British Pub", "pub"),
                new StreetBuilding.Shop(StreetBuilding.Use.RESTAURANT, "할랄 키친", "Halal Food", "halal")};
        a.rooftop = "스카이 라운지";
        a.skin = 0;
        StreetBuilding.Spec b = new StreetBuilding.Spec();
        b.ground = new StreetBuilding.Shop(StreetBuilding.Use.STORE, "24시 편의점", "24h Store");
        b.upper = new StreetBuilding.Shop[]{new StreetBuilding.Shop(StreetBuilding.Use.PC, "준서 PC방", "PC Cafe"),
                new StreetBuilding.Shop(StreetBuilding.Use.NORAE, "코인노래방", "Coin Karaoke"),
                new StreetBuilding.Shop(StreetBuilding.Use.CAFE, "카페 담다", "Cafe")};
        b.basement = new StreetBuilding.Shop[]{new StreetBuilding.Shop(StreetBuilding.Use.CLUB, "클럽 오로라", "CLUB AURORA"),
                new StreetBuilding.Shop(StreetBuilding.Use.CLUB, "클럽 오로라", "CLUB AURORA")};
        b.skin = 3;
        b.coreLeft = false;
        StreetBuilding.Spec c = new StreetBuilding.Spec();
        c.ground = new StreetBuilding.Shop(StreetBuilding.Use.CAFE, "카페 소리", "Coffee");
        c.upper = new StreetBuilding.Shop[]{new StreetBuilding.Shop(StreetBuilding.Use.CLOTHES, "빈티지 옷가게", "Vintage"),
                new StreetBuilding.Shop(StreetBuilding.Use.RESTAURANT, "홍대포차", "Pocha", "pocha")};
        c.basement = new StreetBuilding.Shop[]{new StreetBuilding.Shop(StreetBuilding.Use.LIVE, "라이브홀 소리", "LIVE HALL")};
        c.skin = 2;
        return new StreetBuilding[]{
                StreetBuilding.create(13, 18, new Random(1), a),
                StreetBuilding.create(14, 20, new Random(2), b),
                StreetBuilding.create(12, 19, new Random(3), c)};
    }

    /**
     * 지하가 있는 건물도 걷기 검사를 하려고, 상자를 위로 올려 놓고(지하 바닥이 0 이상이 되게) 땅 밑 빈칸은 흙으로 채운 복사본.
     * 길에서 출발점: 앞 줄 가운데, 길 높이.
     */
    static WalkCheck walkFromStreet(Voxels v, int shift, int startI) {
        Voxels c = new Voxels(v.w, v.d, v.y0 + shift, v.y0 + v.h - 1 + shift);
        Block soil = Block.of("dirt", 0x86603F);
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    c.set(i, y + shift, j, b == null && y < 0 ? soil : b);
                }
            }
        }
        // 출발점: 앞 줄에서 몸이 지나갈 수 있는 칸 (입간판·탁자가 있는 칸은 건너뜀)
        int si = startI;
        for (int k = 0; k < v.w; k++) {
            int i = (startI + k) % v.w;
            if (WalkCheck.passable(v.get(i, 0, v.d - 1)) && WalkCheck.passable(v.get(i, 1, v.d - 1))) {
                si = i;
                break;
            }
        }
        return new WalkCheck(c).run(si + 1, shift, v.d);
    }

    @Test
    void streetBuildingsHaveStairsToEveryFloorAndBasement() {
        for (StreetBuilding b : streetSamples()) {
            int shift = -b.levels[0];
            Voxels v = b.voxels();
            int startI = v.w / 2;
            WalkCheck walk = walkFromStreet(v, shift, startI);
            for (int k = 0; k < b.levels.length; k++) {
                int n = walk.reachedAt(b.levels[k] + shift);
                assertTrue(n > 12, "거리 건물 " + v.w + "×" + v.d + ": 서는 높이 " + b.levels[k] + " 에 걸어서 못 가요 (" + n + "칸)");
            }
        }
    }

    @Test
    void itaewonHotelHasStairsToEveryFloor() {
        InteriorTest.assertAllFloorsReachable("이태원 호텔", YongsanPlan.hotel(26, 34, new Random(4)), Floors.levels(Floors.GROUND, Floors.OFFICE, 9));
    }

    /** 도시에 실제로 놓인 이태원·홍대 건물 전부: 길에서 걸어 들어가 지하부터 옥상까지 */
    @Test
    void placedStreetBuildingsAreWalkable() {
        int checked = 0;
        for (Placement p : TestCity.buildings().placements()) {
            if (!p.kind.equals("restaurant") && !p.kind.equals("nightlife")) {
                continue;
            }
            Voxels v = p.voxels();
            int shift = -(v.y0 + 1);
            int cx = v.w / 2, cj = (v.d - 2) / 2;
            WalkCheck walk = walkFromStreet(v, shift, cx);
            for (int L = -StreetBuilding.BASEMENT_H * 2; L <= v.y0 + v.h - 6; L += L < 0 ? StreetBuilding.BASEMENT_H : StreetBuilding.FLOOR_H) {
                Block slab = v.get(v.w - 3, L - 1, v.d - 3);
                if (L < v.y0 + 1 || slab == null || slab.isAir()) {
                    continue;
                }
                int n = walk.reachedAt(L + shift);
                assertTrue(n > 12, p + ": 서는 높이 " + L + " 에 걸어서 못 가요 (" + n + "칸)");
            }
            checked++;
        }
        assertTrue(checked >= 18, "검사한 거리 건물 " + checked);
    }

    @Test
    void renderPreviews() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        String only = System.getProperty("previewOnly", "");
        File dir = new File("build/preview");
        dir.mkdirs();
        if (only.isEmpty() || "police-yongsan".contains(only) || only.contains("police-yongsan")) {
            police("police-yongsan", yongsan(), dir);
        }
        if (only.isEmpty() || "police-gangnam".contains(only) || only.contains("police-gangnam")) {
            police("police-gangnam", gangnam(), dir);
        }
        if (only.isEmpty() || only.contains("police-detail")) {
            Voxels v = yongsan();
            int[][] spots = {{12, 13, 32, 29, 6}, {20, 24, 68, 46, 2}, {5, 24, 45, 39, 24}, {34, 26, 82, 39, 18}, {56, 13, 82, 29, 1}};
            String[] names = {"cells", "lobby", "hall", "chief", "situation"};
            for (int k = 0; k < spots.length; k++) {
                int[] q = spots[k];
                ImageIO.write(new IsoRender(crop(v, q[0], q[1], q[2], q[3], q[4]), 1).iso(18, q[4]), "png",
                        new File(dir, "police-detail-" + names[k] + ".png"));
            }
        }
        if (only.isEmpty() || only.contains("street")) {
            int n = 0;
            for (StreetBuilding b : streetSamples()) {
                Voxels v = b.voxels();
                n++;
                ImageIO.write(new IsoRender(v, 2).iso(12, Integer.MAX_VALUE), "png", new File(dir, "street-" + n + ".png"));
                IsoRender ir = new IsoRender(v, 2);
                for (int k = 0; k < b.levels.length - 1; k++) {
                    int L = b.levels[k], cut = L + b.levels[k + 1] - L - 3;
                    String tag = "street-" + n + "-" + (L < 0 ? "b" + (-L) : "" + L);
                    ImageIO.write(new IsoRender(crop(v, 0, 0, v.w - 1, v.d - 1, cut), 2).iso(16, cut), "png", new File(dir, tag + ".png"));
                    // 계단에서 나와 앞(길 쪽)을 보는 눈높이
                    ImageIO.write(ir.perspective(b.landing()[0] + 0.5, L + 1.6, b.landing()[1] + 0.5, 0, 8, 85, 640, 400), "png", new File(dir, tag + "-eye.png"));
                }
            }
        }
    }

    private static void police(String n, Voxels v, File dir) throws IOException {
        IsoRender r = new IsoRender(v, 3);
        ImageIO.write(r.iso(6, Integer.MAX_VALUE), "png", new File(dir, n + ".png"));
        int[] lv = PoliceStation.levels();
        int half = v.w / 2;
        for (int k = 0; k < lv.length - 1; k++) {
            int cut = lv[k] + lv[k + 1] - lv[k] - 3;
            ImageIO.write(new IsoRender(crop(v, 0, 10, half + 4, v.d - 18), 1).iso(11, cut), "png", new File(dir, n + "-" + (k + 1) + "f-w.png"));
            ImageIO.write(new IsoRender(crop(v, half - 4, 10, v.w - 1, v.d - 18), 1).iso(11, cut), "png", new File(dir, n + "-" + (k + 1) + "f-e.png"));
        }
    }

    /** 상자 일부만 떼어 낸 것 (가까이 보기용) */
    static Voxels crop(Voxels v, int i0, int j0, int i1, int j1) {
        return crop(v, i0, j0, i1, j1, v.y0 + v.h - 1);
    }

    static Voxels crop(Voxels v, int i0, int j0, int i1, int j1, int ymax) {
        Voxels c = new Voxels(i1 - i0 + 1, j1 - j0 + 1, v.y0, ymax);
        for (int y = v.y0; y <= ymax; y++) {
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    c.set(i - i0, y, j - j0, v.get(i, y, j));
                }
            }
        }
        return c;
    }
}
