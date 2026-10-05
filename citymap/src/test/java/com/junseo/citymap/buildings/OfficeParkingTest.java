package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 구역별 업무·주상복합 빌딩과 공영 주차빌딩: 걸어서 모든 층, 차로 모든 칸, 미리보기 */
class OfficeParkingTest {

    // ------------------------------------------------------------------ 주차빌딩

    @Test
    void parkingBuildingDrivesToEveryStallAndWalksToEveryDeck() {
        int[] ws = {24, 27, 29, 30, 33, 36, 37, 40, 42, 45};
        int[] ds = {30, 31, 37, 44, 52, 60};
        int seed = 0;
        List<String> bad = new ArrayList<>();
        for (int w : ws) {
            for (int d : ds) {
                for (int levels = 2; levels <= 6; levels += 2) {
                    seed++;
                    String tag = w + "×" + d + " " + levels + "층 #" + seed;
                    long t0 = System.nanoTime();
                    Voxels v = ParkingBuilding.build(w, d, levels, "망원동", new Random(seed));
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    assertTrue(ms < 200, tag + " 너무 느려요 " + ms + "ms");
                    // 사람: 모든 층
                    WalkCheck walk = new WalkCheck(v).run();
                    for (int dk = 0; dk < levels; dk++) {
                        int n = walk.reachedAt(ParkingBuilding.level(dk));
                        if (n < 60) {
                            bad.add(tag + ": " + (dk + 1) + "층 걸어서 " + n + "칸");
                        }
                    }
                    // 차: 출입구에서 모든 칸
                    List<double[]> spots = v.carSpots();
                    int[] perDeck = new int[levels];
                    for (double[] s : spots) {
                        perDeck[(int) s[1] / ParkingBuilding.H]++;
                    }
                    for (int dk = 0; dk < levels; dk++) {
                        if (perDeck[dk] < 6) {
                            bad.add(tag + ": " + (dk + 1) + "층 주차 칸 " + perDeck[dk]);
                        }
                    }
                    DriveCheck drive = new DriveCheck(v).run(v.w / 2); // 바깥 길은 어디로든 이어지니 앞 가운데서 출발
                    int miss = 0;
                    String first = null;
                    for (double[] s : spots) {
                        if (!drive.reachedSpot(s)) {
                            miss++;
                            if (first == null) {
                                first = String.format("(%.1f, %d, %.1f → %d,%d)", s[0], (int) s[1], s[2], (int) s[3], (int) s[4]);
                            }
                        }
                    }
                    if (miss > 0) {
                        bad.add(tag + ": 차로 못 가는 칸 " + miss + "/" + spots.size() + " 예 " + first);
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void renderParkingPreview() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        Voxels v = ParkingBuilding.build(36, 48, 5, "망원동", new Random(3));
        ImageIO.write(new IsoRender(v, 4).iso(8, Integer.MAX_VALUE), "png", new File(dir, "dz-parking-building.png"));
        for (int dk : new int[]{0, 1, 4}) {
            ImageIO.write(new IsoRender(v, 4).iso(8, ParkingBuilding.level(dk) + 1), "png", new File(dir, "dz-parking-deck" + (dk + 1) + ".png"));
        }
        ImageIO.write(new IsoRender(v, 30).perspective(v.w + 6, 1.7, v.d + 16, 150, -8, 75, 1280, 720), "png",
                new File(dir, "dz-parking-eye.png"));
        Voxels small = ParkingBuilding.build(24, 30, 3, "준서 제2", new Random(4));
        ImageIO.write(new IsoRender(small, 4).iso(10, Integer.MAX_VALUE), "png", new File(dir, "dz-parking-small.png"));
        ImageIO.write(new IsoRender(small, 4).iso(10, 1), "png", new File(dir, "dz-parking-small-1f.png"));
        Voxels big = ParkingBuilding.build(45, 60, 6, "잠실", new Random(5));
        ImageIO.write(new IsoRender(big, 4).iso(6, Integer.MAX_VALUE), "png", new File(dir, "dz-parking-big.png"));
        ImageIO.write(new IsoRender(big, 4).iso(6, ParkingBuilding.level(2) + 1), "png", new File(dir, "dz-parking-big-3f.png"));
    }

    // ------------------------------------------------------------------ 업무·주상복합 빌딩

    static final String[] DISTRICTS = {"yeouido", "gangnam", "junggu", "yongsan", "mapo", "songpa", "hongdae"};
    static final int[] FLOORS = {6, 8, 11, 13, 15, 17, 20, 22, 24, 27, 31, 36, 45, 60};
    static final int[][] SIZES = {{24, 24}, {28, 30}, {34, 28}, {40, 36}, {48, 44}, {56, 50}, {62, 56}, {70, 60}, {30, 52}};

    /** 방 종류별 최소 안쪽 크기 {짧은 변, 긴 변} (density-brief 의 실거주 크기) */
    static int[] minimum(String type) {
        return switch (type) {
            case "원룸" -> new int[]{6, 8};
            case "84형" -> new int[]{10, 12};
            case "거실" -> new int[]{5, 6};
            case "주방" -> new int[]{3, 5};
            case "안방" -> new int[]{4, 4};
            case "침실" -> new int[]{3, 4};
            case "욕실" -> new int[]{2, 3};
            case "현관" -> new int[]{2, 2};
            case "발코니" -> new int[]{1, 3};
            default -> throw new IllegalArgumentException(type);
        };
    }

    @Test
    void officesHaveStairsToEveryFloorAndLivableHomes() {
        List<String> bad = new ArrayList<>();
        int seed = 100;
        java.util.Set<Office.Style> styles = java.util.EnumSet.noneOf(Office.Style.class);
        int apts = 0, studios = 0;
        long worst = 0;
        for (String dist : DISTRICTS) {
            for (int floors : FLOORS) {
                for (boolean podium : new boolean[]{false, true}) {
                    seed++;
                    int[] sz = SIZES[(seed * 7) % SIZES.length];
                    String tag = dist + " " + floors + "층 " + sz[0] + "×" + sz[1] + (podium ? " 포디움" : "") + " #" + seed;
                    long t0 = System.nanoTime();
                    Office.Built b = Office.inspect(dist, sz[0], sz[1], floors, podium, new Random(seed));
                    worst = Math.max(worst, (System.nanoTime() - t0) / 1_000_000);
                    styles.add(Office.style(dist, floors));
                    assertTrue(b.levels().length == floors + 1, tag + ": 층수 " + (b.levels().length - 1));
                    WalkCheck walk = new WalkCheck(b.voxels()).run();
                    for (int k = 0; k < floors; k++) {
                        if (!b.used()[k]) {
                            continue; // 8층 이상 빈 껍데기 층은 검사하지 않음
                        }
                        int n = walk.reachedAt(b.levels()[k]);
                        if (n < 20) {
                            bad.add(tag + ": " + (k + 1) + "층(서는 높이 " + b.levels()[k] + ") 걸어서 " + n + "칸");
                        }
                    }
                    // 집: 최소 크기, 방마다 걸어서 들어감
                    for (Office.Room rm : b.rooms()) {
                        int[] min = minimum(rm.type());
                        if (rm.small() < min[0] || rm.large() < min[1]) {
                            bad.add(tag + ": " + rm.type() + " " + rm.sizeI() + "×" + rm.sizeJ() + " < " + min[0] + "×" + min[1]);
                        }
                        boolean in = false;
                        for (int j = rm.j0(); j <= rm.j1() && !in; j++) {
                            for (int i = rm.i0(); i <= rm.i1() && !in; i++) {
                                in = walk.reached(i, rm.level(), j);
                            }
                        }
                        if (!in) {
                            bad.add(tag + ": " + rm.type() + " (" + rm.i0() + "," + rm.level() + "," + rm.j0() + ") 에 못 들어가요");
                        }
                    }
                    long units = b.rooms().stream().filter(x -> x.type().equals("84형")).count();
                    long rooms1 = b.rooms().stream().filter(x -> x.type().equals("원룸")).count();
                    apts += units;
                    studios += rooms1;
                    for (String type : new String[]{"거실", "주방", "안방", "현관", "발코니"}) {
                        long c = b.rooms().stream().filter(x -> x.type().equals(type)).count();
                        if (c != units) {
                            bad.add(tag + ": 84형 " + units + "세대인데 " + type + " " + c);
                        }
                    }
                    if (b.rooms().stream().filter(x -> x.type().equals("침실")).count() != 2 * units) {
                        bad.add(tag + ": 84형 침실 수");
                    }
                    if (Office.style(dist, floors) == Office.Style.RESIDENTIAL && units + rooms1 < 3) {
                        bad.add(tag + ": 꾸민 집이 너무 적어요 " + units + "+" + rooms1);
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
        assertTrue(styles.size() == Office.Style.values().length, "양식 " + styles);
        assertTrue(apts > 20, "세대 " + apts + " 원룸 " + studios);
        assertTrue(worst < 1500, "가장 느린 빌딩 " + worst + "ms");
        assertTrue(Office.kind("yongsan", 40).equals("apartment") && Office.kind("gangnam", 30).equals("office"));
    }

    /** 문서에 적은 크기 범위 어디서나 예외 없이 짓고, 층수를 그대로 지킴 */
    @Test
    void neverThrowsInDocumentedRanges() {
        Random pick = new Random(42);
        String[] districts = {"yeouido", "gangnam", "junggu", "yongsan", "mapo", "songpa", "gwangjin", "hongdae", "university", "namsan", "bukhansan", "x"};
        for (int n = 0; n < 500; n++) {
            int w = Office.MIN_W + pick.nextInt(Office.MAX_W - Office.MIN_W + 1);
            int d = Office.MIN_D + pick.nextInt(Office.MAX_D - Office.MIN_D + 1);
            int floors = Office.MIN_FLOORS + pick.nextInt(n % 5 == 0 ? Office.MAX_FLOORS - Office.MIN_FLOORS + 1 : 20);
            String dist = districts[pick.nextInt(districts.length)];
            boolean podium = pick.nextBoolean();
            Office.Built b = Office.inspect(dist, w, d, floors, podium, new Random(n));
            assertTrue(b.levels().length == floors + 1 && b.voxels().w == w && b.voxels().d == d, dist + " " + w + "×" + d + " " + floors);
            assertTrue(b.voxels().y0 + b.voxels().h - 1 <= 317, "너무 높아요 " + floors);
            Office.name(dist, floors, new Random(n));
        }
        for (int w = ParkingBuilding.MIN_W; w <= ParkingBuilding.MAX_W; w++) {
            for (int d = ParkingBuilding.MIN_D; d <= ParkingBuilding.MAX_D; d += 1 + (w % 3)) {
                int levels = ParkingBuilding.MIN_LEVELS + (w + d) % (ParkingBuilding.MAX_LEVELS - ParkingBuilding.MIN_LEVELS + 1);
                Voxels v = ParkingBuilding.build(w, d, levels, "준서", new Random(w * 100 + d));
                assertTrue(v.w == w && v.d == d && !v.carSpots().isEmpty(), w + "×" + d);
            }
        }
    }

    /** 26.2 에 있는 블록만, 블록 엔티티가 있어야 보이는 블록은 없음, 표지판 글씨는 4줄·한 줄 90px 안 */
    @Test
    void blocksAndSignsAreValid() throws IOException {
        java.util.Set<String> known = new java.util.HashSet<>();
        try (java.io.BufferedReader rd = new java.io.BufferedReader(new java.io.InputStreamReader(
                OfficeParkingTest.class.getResourceAsStream("/block-ids-26.2.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = rd.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    known.add("minecraft:" + line.trim());
                }
            }
        }
        String[] banned = {"chest", "_bed", "banner", "shulker_box", "decorated_pot", "skull", "_head", "bell", "conduit",
                "beacon", "enchanting_table", "lectern", "chiseled_bookshelf", "jukebox", "spawner"};
        List<Voxels> all = new ArrayList<>();
        int seed = 1;
        for (String dist : DISTRICTS) {
            for (int floors : FLOORS) {
                for (boolean podium : new boolean[]{false, true}) {
                    for (int rep = 0; rep < 2; rep++) {
                        int[] sz = SIZES[(seed * 5) % SIZES.length];
                        all.add(Office.build(dist, sz[0], sz[1], floors, podium, new Random(seed++)));
                    }
                }
            }
        }
        for (int s = 0; s < 40; s++) {
            all.add(ParkingBuilding.build(24 + s % 22, 30 + (s * 7) % 31, 2 + s % 5, "준서동 제" + s, new Random(s)));
        }
        java.util.Set<String> bad = new java.util.TreeSet<>();
        for (Voxels v : all) {
            for (int y = v.y0; y < v.y0 + v.h; y++) {
                for (int j = 0; j < v.d; j++) {
                    for (int i = 0; i < v.w; i++) {
                        Block b = v.get(i, y, j);
                        if (b == null) {
                            continue;
                        }
                        if (!known.contains(b.id())) {
                            bad.add("없는 블록 " + b.id());
                        }
                        for (String x : banned) {
                            if (b.id().contains(x)) {
                                bad.add("블록 엔티티 " + b.id());
                            }
                        }
                        String[] lines = b.textLines();
                        if (lines.length > 4) {
                            bad.add("표지판 " + lines.length + "줄");
                        }
                        for (String l : lines) {
                            int px = 0;
                            for (char ch : l.toCharArray()) {
                                px += ch >= 0xAC00 && ch <= 0xD7A3 ? 9 : 6;
                            }
                            if (px > 90) {
                                bad.add("표지판 글씨가 넘침: " + l);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    /** 여러 건물을 옆으로 붙여 한 상자에 (gap 칸 띄움) */
    static Voxels row(List<Voxels> list, int gap) {
        int w = 0, d = 0, y0 = 0, y1 = 0;
        for (Voxels v : list) {
            w += v.w + gap;
            d = Math.max(d, v.d);
            y0 = Math.min(y0, v.y0);
            y1 = Math.max(y1, v.y0 + v.h - 1);
        }
        Voxels out = new Voxels(w - gap, d, y0, y1);
        int x = 0;
        for (Voxels v : list) {
            int dz = d - v.d;
            for (int y = v.y0; y < v.y0 + v.h; y++) {
                for (int j = 0; j < v.d; j++) {
                    for (int i = 0; i < v.w; i++) {
                        Block b = v.get(i, y, j);
                        if (b != null) {
                            out.set(x + i, y, j + dz, b);
                        }
                    }
                }
            }
            for (int i = x + v.w; i < x + v.w + gap && i < out.w; i++) {
                for (int j = 0; j < d; j++) {
                    out.set(i, -1, j, Block.of("gray_concrete", 0x36393D));
                }
            }
            x += v.w + gap;
        }
        return out;
    }

    @Test
    void renderOfficePreview() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        String only = System.getProperty("previewOnly", "");
        Object[][] rows = {
                {"yeouido", new int[][]{{24, 22, 40, 34, 0}, {27, 30, 44, 38, 0}, {45, 50, 56, 50, 1}, {18, 42, 36, 30, 0}, {22, 23, 40, 36, 0}}},
                {"gangnam", new int[][]{{24, 40, 36, 44, 0}, {36, 52, 48, 50, 1}, {28, 44, 40, 46, 0}, {12, 30, 30, 34, 0}, {32, 48, 44, 48, 1}}},
                {"junggu", new int[][]{{10, 26, 26, 28, 0}, {18, 36, 34, 32, 0}, {22, 40, 36, 38, 0}, {30, 48, 44, 44, 1}, {8, 24, 24, 26, 0}}},
                {"yongsan", new int[][]{{38, 60, 54, 50, 1}, {42, 62, 56, 56, 1}, {16, 40, 36, 40, 0}, {35, 56, 50, 44, 1}}},
                {"mapo", new int[][]{{18, 38, 34, 36, 0}, {22, 44, 40, 40, 1}, {12, 28, 28, 30, 0}, {25, 40, 36, 38, 0}}},
                {"songpa", new int[][]{{12, 30, 30, 32, 0}, {17, 40, 34, 36, 0}, {20, 44, 40, 40, 1}, {14, 32, 30, 32, 0}}},
        };
        for (Object[] row : rows) {
            String dist = (String) row[0];
            if (!only.isEmpty() && !("office-" + dist).contains(only)) {
                continue;
            }
            List<Voxels> list = new ArrayList<>();
            int seed = dist.hashCode();
            for (int[] spec : (int[][]) row[1]) {
                Office.Built b = Office.inspect(dist, spec[1], spec[2], spec[0], spec[4] == 1, new Random(seed++));
                list.add(b.voxels());
                // 속 보기는 두 장만: 강남 포디움 1층, 용산 주상복합 맨 아래 주거층
                int[] lv = b.levels();
                Voxels bv = b.voxels();
                if (dist.equals("gangnam") && spec[4] == 1 && list.size() == 2) {
                    ImageIO.write(new IsoRender(LandmarkEastTest.crop(bv, 0, 0, bv.w - 1, bv.d - 1, lv[1] - 3), 2).iso(11, Integer.MAX_VALUE),
                            "png", new File(dir, "dz-office-cut-podium-1f.png"));
                }
                if (dist.equals("yongsan") && list.size() == 1) {
                    int k = java.util.stream.IntStream.range(1, lv.length - 1).filter(q -> b.used()[q] && b.rooms().stream()
                            .anyMatch(rm -> rm.level() == lv[q])).findFirst().orElse(1);
                    ImageIO.write(new IsoRender(LandmarkEastTest.crop(bv, 0, 0, bv.w - 1, bv.d - 1, lv[k] + 1), 2).iso(11, Integer.MAX_VALUE),
                            "png", new File(dir, "dz-office-cut-homes.png"));
                }
            }
            Voxels all = row(list, 8);
            ImageIO.write(new IsoRender(all, 6).iso(3, Integer.MAX_VALUE), "png", new File(dir, "dz-office-" + dist + ".png"));
        }
    }
}
