package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 구역별 상가 건물 (StreetShop) 검사와 미리보기 */
class StreetShopTest {
    static final String[] DISTRICTS = {"junggu", "gangnam", "gwangjin", "songpa", "yongsan", "hongdae", "mapo", "university",
            "yeouido", "bukhansan", "namsan", "nowhere"};

    /** 블록 엔티티가 있어야 보이는 블록 (생성 단계에서 놓으면 안 보임) */
    private static final String[] BANNED = {"chest", "_bed", "banner", "shulker", "decorated_pot", "skull", "_head", "bell", "conduit",
            "beacon", "enchanting_table", "lectern", "chiseled_bookshelf", "jukebox", "spawner", "_shelf", "golem_statue", "campfire"};

    /** 층마다 서는 칸 중 걸어서 닿는 비율 */
    private static double[] reachRatios(StreetShop s, WalkCheck walk) {
        Voxels v = s.voxels();
        double[] out = new double[s.levels.length];
        for (int k = 0; k < s.levels.length; k++) {
            int y = s.levels[k], stand = 0, got = 0;
            for (int j = 0; j < v.d - 1; j++) {
                for (int i = 0; i < v.w; i++) {
                    if (WalkCheck.passable(v.get(i, y, j)) && WalkCheck.passable(v.get(i, y + 1, j)) && WalkCheck.support(v.get(i, y - 1, j))) {
                        stand++;
                        if (walk.reached(i, y, j)) {
                            got++;
                        }
                    }
                }
            }
            out[k] = stand == 0 ? 0 : (double) got / stand;
        }
        return out;
    }

    @Test
    void everyFloorIsReachableOnFoot() {
        int[][] sizes = {{7, 9}, {10, 12}, {12, 14}, {14, 16}, {16, 18}, {22, 20}, {9, 11}, {18, 13}};
        List<String> bad = new ArrayList<>();
        int built = 0;
        long t0 = System.nanoTime();
        for (String dist : DISTRICTS) {
            for (boolean party : new boolean[]{true, false}) {
                for (int[] sz : sizes) {
                    for (int floors = 1; floors <= 10; floors++) {
                        for (int seed = 0; seed < 2; seed++) {
                            long sd = seed * 7919L + floors * 31L + sz[0] * 101L + dist.hashCode();
                            StreetShop s = StreetShop.create(dist, party, sz[0], sz[1], floors, new Random(sd));
                            built++;
                            assertEquals(floors + 1, s.levels.length, "층 수");
                            WalkCheck walk = new WalkCheck(s.voxels()).run();
                            double[] ratio = reachRatios(s, walk);
                            for (int k = 0; k <= floors; k++) {
                                int n = walk.reachedAt(s.levels[k]);
                                // 1층만 있는 건물(옥상 계단 없음)은 옥상을 빼고
                                boolean roofless = k == floors && floors == 1 && n == 0;
                                if (roofless) {
                                    continue;
                                }
                                if (n < 6 || ratio[k] < 0.8) {
                                    bad.add(dist + (party ? " 맞벽 " : " 골목 ") + sz[0] + "×" + sz[1] + " " + floors + "층 #" + seed + ": "
                                            + (k + 1) + "층 " + n + "칸 " + Math.round(ratio[k] * 100) + "% (" + s.look() + ", "
                                            + (k < floors ? s.tenant(k).use() + " " + s.tenant(k).kind() : "옥상") + ")");
                                }
                            }
                        }
                    }
                }
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("StreetShop " + built + "채, 걷기 검사 포함 " + ms + "ms");
        // 종류별로 묶어서 첫 예만
        java.util.Map<String, String> first = new java.util.TreeMap<>();
        java.util.Map<String, Integer> count = new java.util.TreeMap<>();
        for (String b : bad) {
            String key = b.substring(b.indexOf('(')) + (b.contains(": 1층") ? " 1층" : "");
            first.putIfAbsent(key, b);
            count.merge(key, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        first.forEach((k, e) -> sb.append(count.get(k)).append("× ").append(e).append('\n'));
        assertTrue(bad.isEmpty(), bad.size() + "곳:\n" + sb);
    }

    @Test
    void anySizeInRangeBuildsFastAndDeterministic() {
        long t0 = System.nanoTime();
        int n = 0;
        for (String dist : DISTRICTS) {
            for (int w = 7; w <= 22; w += 3) {
                for (int d = 9; d <= 20; d += 2) {
                    for (int floors : new int[]{1, 2, 4, 6, 10}) {
                        boolean party = (w + d) % 2 == 0;
                        Voxels a = StreetShop.build(dist, party, w, d, floors, new Random(w * 1000L + d * 10L + floors));
                        assertEquals(w, a.w);
                        assertEquals(d, a.d);
                        assertTrue(a.y0 + a.h - 1 <= 317, "높이 제한");
                        n++;
                    }
                }
            }
        }
        long per = (System.nanoTime() - t0) / n / 1000;
        System.out.println("StreetShop 한 채 평균 " + per + "µs (" + n + "채)");
        Voxels x = StreetShop.build("gwangjin", true, 14, 16, 5, new Random(9));
        Voxels y = StreetShop.build("gwangjin", true, 14, 16, 5, new Random(9));
        assertEquals(digest(x), digest(y));
        assertTrue(StreetShop.name("junggu", new Random(1)).length() > 2);
    }

    private static long digest(Voxels v) {
        long h = 17;
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    h = h * 31 + (b == null ? 0 : b.data().hashCode() * 7 + (b.text() == null ? 0 : b.text().hashCode()));
                }
            }
        }
        return h;
    }

    @Test
    void blocksAndSignsAreValid() throws IOException {
        Set<String> known = new HashSet<>();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(
                StreetShopTest.class.getResourceAsStream("/block-ids-26.2.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = rd.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    known.add("minecraft:" + line.trim());
                }
            }
        }
        Set<String> unknown = new TreeSet<>(), banned = new TreeSet<>(), badText = new TreeSet<>();
        int signs = 0;
        for (String dist : DISTRICTS) {
            for (int seed = 0; seed < 25; seed++) {
                int w = 8 + seed % 15, d = 10 + seed % 11, floors = 1 + seed % 10;
                Voxels v = StreetShop.build(dist, seed % 3 != 0, w, d, floors, new Random(seed * 13L + dist.length()));
                for (Block b : v.palette()) {
                    if (!known.contains(b.id())) {
                        unknown.add(b.id());
                    }
                    for (String x : BANNED) {
                        if (b.id().contains(x)) {
                            banned.add(b.id());
                        }
                    }
                    if (b.text() != null) {
                        signs++;
                        String[] lines = b.textLines();
                        if (lines.length > 4) {
                            badText.add(lines.length + "줄: " + String.join("/", lines));
                        }
                        for (String line : lines) {
                            int px = 0;
                            for (char ch : line.toCharArray()) {
                                px += ch >= 0xAC00 && ch <= 0xD7A3 ? 9 : 6;
                            }
                            if (px > 90) {
                                badText.add(line);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(unknown.isEmpty(), "26.2 에 없는 블록: " + unknown);
        assertTrue(banned.isEmpty(), "블록 엔티티 블록: " + banned);
        assertTrue(signs > 500, "표지판 " + signs);
        assertTrue(badText.isEmpty(), "표지판에 안 들어가는 글씨: " + badText);
    }

    /** 맞벽 건물: 옆벽(i = 0, w-1)에 창·문·구멍 없음, 땅 폭을 꽉 채움 */
    @Test
    void partyWallsAreBlank() {
        List<String> bad = new ArrayList<>();
        for (String dist : DISTRICTS) {
            for (int seed = 0; seed < 12; seed++) {
                int w = 10 + seed, d = 12 + seed % 9, floors = 2 + seed % 8;
                StreetShop s = StreetShop.create(dist, true, w, d, floors, new Random(seed));
                Voxels v = s.voxels();
                int top = s.levels[floors] - 1;
                for (int i : new int[]{0, w - 1}) {
                    for (int y = 0; y <= top; y++) {
                        for (int j = 0; j <= d - 2; j++) {
                            Block b = v.get(i, y, j);
                            if (b == null || b.isAir() || b.id().contains("glass") || b.id().contains("door") || b.id().contains("bars")) {
                                bad.add(dist + " " + w + "×" + d + " #" + seed + " (" + i + "," + y + "," + j + ") " + (b == null ? "빈칸" : b.id()));
                                break;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "맞벽에 구멍: " + bad.subList(0, Math.min(20, bad.size())));
    }

    /** 실거주 크기: 원룸 6×8 (욕실 2×3 포함), 투룸 9×10 (현관·거실·주방·침실 둘·욕실), 고시원 방 3×4 + 공용 욕실·주방 */
    @Test
    void homesAreLivableSize() {
        int two = 0, one = 0, gosi = 0;
        List<String> bad = new ArrayList<>();
        for (String dist : new String[]{"mapo", "namsan", "university", "bukhansan", "hongdae", "yongsan", "junggu", "nowhere"}) {
            for (boolean party : new boolean[]{true, false}) {
                for (int w = 10; w <= 22; w += 2) {
                    for (int d = 12; d <= 20; d += 2) {
                        for (int seed = 0; seed < 3; seed++) {
                            int floors = 2 + (w + d + seed) % 5;
                            StreetShop s = StreetShop.create(dist, party, w, d, floors, new Random(seed * 97L + w * 7L + d));
                            String tag = dist + " " + w + "×" + d + " " + floors + "층 #" + seed + ": ";
                            for (int k = 1; k < floors; k++) {
                                StreetShop.Use u = s.tenant(k).use();
                                if (u == StreetShop.Use.HOME || u == StreetShop.Use.ONEROOM || u == StreetShop.Use.GOSIWON) {
                                    final int fk = k;
                                    if (s.units.stream().noneMatch(x -> x.floor() == fk)) {
                                        bad.add(tag + (k + 1) + "층 " + u + " 인데 집이 없음");
                                    }
                                }
                            }
                            for (StreetShop.Unit unit : s.units) {
                                String t = tag + (unit.floor() + 1) + "층 " + unit.type() + " ";
                                switch (unit.type()) {
                                    case "투룸" -> {
                                        two++;
                                        if (Math.min(unit.w(), unit.d()) < 9 || Math.max(unit.w(), unit.d()) < 10) {
                                            bad.add(t + unit.w() + "×" + unit.d());
                                        }
                                        int beds = 0;
                                        Set<String> has = new HashSet<>();
                                        for (StreetShop.Rect rc : unit.rooms()) {
                                            has.add(rc.name());
                                            boolean ok = switch (rc.name()) {
                                                case "현관" -> rc.atLeast(2, 2);
                                                case "거실" -> rc.atLeast(4, 5);
                                                case "주방" -> rc.atLeast(2, 4);
                                                case "욕실" -> rc.atLeast(2, 3);
                                                case "침실" -> rc.atLeast(3, 4);
                                                default -> true;
                                            };
                                            if (rc.name().equals("침실")) {
                                                beds++;
                                            }
                                            if (!ok) {
                                                bad.add(t + rc.name() + " " + rc.w() + "×" + rc.d());
                                            }
                                        }
                                        if (beds < 2 || !has.containsAll(List.of("현관", "거실", "주방", "욕실"))) {
                                            bad.add(t + "방 모자람 " + has);
                                        }
                                    }
                                    case "원룸" -> {
                                        one++;
                                        StreetShop.Rect in = unit.rooms().get(0);
                                        if (!in.atLeast(6, 8)) {
                                            bad.add(t + in.w() + "×" + in.d());
                                        }
                                        if (unit.rooms().stream().noneMatch(x -> x.name().equals("욕실") && x.atLeast(2, 3))) {
                                            bad.add(t + "욕실 없음");
                                        }
                                    }
                                    case "고시원" -> {
                                        gosi++;
                                        int rooms = 0;
                                        boolean bath = false, kitchen = false;
                                        for (StreetShop.Rect rc : unit.rooms()) {
                                            switch (rc.name()) {
                                                case "방" -> {
                                                    rooms++;
                                                    if (!rc.atLeast(3, 4)) {
                                                        bad.add(t + "방 " + rc.w() + "×" + rc.d());
                                                    }
                                                }
                                                case "공용욕실" -> bath = rc.atLeast(2, 3);
                                                case "공용주방" -> kitchen = true;
                                                default -> {
                                                }
                                            }
                                        }
                                        if (rooms < 3 || !bath || !kitchen) {
                                            bad.add(t + "방 " + rooms + " 욕실 " + bath + " 주방 " + kitchen);
                                        }
                                    }
                                    default -> bad.add(t + "모르는 집");
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("투룸 " + two + ", 원룸 " + one + ", 고시원 " + gosi);
        assertTrue(two > 20 && one > 20 && gosi > 5, "투룸 " + two + ", 원룸 " + one + ", 고시원 " + gosi);
        assertTrue(bad.isEmpty(), bad.size() + "곳: " + String.join("\n", bad.subList(0, Math.min(40, bad.size()))));
    }

    // ------------------------------------------------------------------ 미리보기

    /** 거리 한 줄: 건물들을 맞벽으로 붙이고 앞에 인도·차도 */
    static Voxels row(String dist, int d, int[] widths, int[] floors, long seed) {
        List<Voxels> parts = new ArrayList<>();
        int total = 0, top = 0;
        for (int k = 0; k < widths.length; k++) {
            Voxels b = StreetShop.build(dist, true, widths[k], d, floors[k], new Random(seed + k * 31L));
            parts.add(b);
            total += widths[k];
            top = Math.max(top, b.y0 + b.h - 1);
        }
        int road = 9;
        Voxels out = new Voxels(total, d + road, -1, top);
        out.fill(0, -1, d, total - 1, -1, d + 2, Block.of("light_gray_concrete", 0x9A9A96));
        out.fill(0, -1, d + 3, total - 1, -1, d + road - 1, Block.of("gray_concrete", 0x3A3D42));
        out.fill(0, -1, d + 6, total - 1, -1, d + 6, Block.of("white_concrete", 0xD0D0D0));
        int x = 0;
        for (Voxels b : parts) {
            for (int y = b.y0; y < b.y0 + b.h; y++) {
                for (int j = 0; j < b.d; j++) {
                    for (int i = 0; i < b.w; i++) {
                        Block c = b.get(i, y, j);
                        if (c != null) {
                            out.set(x + i, y, j, c);
                        }
                    }
                }
            }
            x += b.w;
        }
        return out;
    }

    static void save(BufferedImage img, String name) throws IOException {
        File dir = new File("build/preview");
        dir.mkdirs();
        ImageIO.write(YeouidoJungguTest.crop(img), "png", new File(dir, name + ".png"));
    }

    @Test
    void renderStreets() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        String only = System.getProperty("previewOnly", "");
        Object[][] streets = {
                {"junggu", 15, new int[]{11, 9, 13, 10, 12, 9, 14}, new int[]{4, 3, 5, 4, 3, 4, 6}},
                {"gangnam", 18, new int[]{14, 12, 16, 13, 15, 12}, new int[]{6, 5, 7, 6, 8, 5}},
                {"gwangjin", 15, new int[]{11, 10, 12, 9, 13, 10, 11}, new int[]{4, 3, 5, 4, 3, 5, 4}},
                {"songpa", 16, new int[]{12, 11, 13, 10, 12, 14}, new int[]{4, 5, 3, 4, 5, 4}},
                {"yongsan", 14, new int[]{10, 9, 12, 10, 11, 9, 12}, new int[]{3, 2, 4, 3, 2, 4, 3}},
                {"hongdae", 15, new int[]{11, 12, 10, 13, 9, 12, 10}, new int[]{5, 4, 6, 3, 2, 5, 4}},
                {"mapo", 14, new int[]{9, 8, 10, 9, 11, 8, 10}, new int[]{2, 3, 2, 1, 3, 2, 2}},
                {"university", 15, new int[]{10, 12, 11, 10, 13, 11}, new int[]{4, 5, 3, 4, 5, 4}},
                {"yeouido", 18, new int[]{15, 13, 16, 14, 12}, new int[]{6, 5, 7, 6, 5}},
                {"bukhansan", 14, new int[]{11, 10, 12, 9, 11}, new int[]{2, 2, 1, 2, 2}},
                {"namsan", 13, new int[]{9, 8, 10, 9, 8, 10}, new int[]{2, 1, 2, 3, 2, 1}},
        };
        for (Object[] st : streets) {
            String dist = (String) st[0];
            if (!only.isEmpty() && !("dz-shop-" + dist).contains(only) && !only.contains(dist)) {
                continue;
            }
            int d = (Integer) st[1];
            int[] ws = (int[]) st[2], fs = (int[]) st[3];
            Voxels v = row(dist, d, ws, fs, 1234 + dist.hashCode());
            save(new IsoRender(v, 2).iso(6, Integer.MAX_VALUE), "dz-shop-" + dist);
            int total = 0;
            for (int x : ws) {
                total += x;
            }
            IsoRender eye = new IsoRender(v, 2);
            save(eye.perspective(total * 0.08, 1.7, d + 8.5, 212, -12, 70, 1100, 620), "dz-shop-" + dist + "-street");
        }
        if (only.isEmpty() || only.contains("cut")) {
            cut("gwangjin", 12, 15, 4, 11, "dz-shop-cut-mukja");
            cut("mapo", 14, 16, 3, 5, "dz-shop-cut-mangwon");
        }
    }

    private static void cut(String dist, int w, int d, int floors, long seed, String name) throws IOException {
        StreetShop s = StreetShop.create(dist, true, w, d, floors, new Random(seed));
        Voxels v = s.voxels();
        StringBuilder log = new StringBuilder(name + " (" + s.look() + "):");
        for (int k = 0; k < floors; k++) {
            log.append(" ").append(k + 1).append("F ").append(s.tenant(k).name()).append("/").append(s.tenant(k).use());
            if (k <= 1) {
                save(new IsoRender(v, 2).iso(12, s.levels[k] + 2), name + "-" + (k + 1) + "f");
            }
        }
        System.out.println(log);
    }
}
