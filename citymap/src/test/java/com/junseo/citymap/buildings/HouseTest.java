package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 구역별 주택: 걸어서 모든 층·옥상·반지하, 주차 자리, 살 만한 크기, 블록 이름 */
class HouseTest {
    static final String[] DISTRICTS = {"university", "gwangjin", "gangnam", "songpa", "yeouido", "junggu", "mapo", "hongdae",
            "yongsan", "namsan", "bukhansan", "guincheon"};
    static final int[][] SIZES = {{12, 14}, {13, 15}, {14, 16}, {16, 18}, {18, 20}, {20, 22}, {24, 24}, {12, 24}, {24, 14}, {15, 20}};

    interface Each {
        void accept(String district, int w, int d, int floors, int seed, House h);
    }

    static void forAll(int seeds, Each each) {
        for (String dist : DISTRICTS) {
            for (int[] s : SIZES) {
                for (int f = 1; f <= 6; f++) {
                    for (int seed = 0; seed < seeds; seed++) {
                        House h = House.make(dist, s[0], s[1], f, new Random(seed * 31L + f));
                        each.accept(dist, s[0], s[1], f, seed, h);
                    }
                }
            }
        }
    }

    @Test
    void everyFloorRoofAndBasementIsReachable() {
        List<String> bad = new ArrayList<>();
        forAll(3, (dist, w, d, f, seed, h) -> {
            Voxels v = h.voxels();
            Walk walk = new Walk(v).run();
            String tag = dist + " " + w + "×" + d + " " + f + "층 #" + seed + " " + java.util.Arrays.toString(h.floorPlan);
            for (int k = 0; k < f; k++) {
                int n = walk.reachedAt(h.lv[k]);
                if (n < 6) {
                    bad.add(tag + ": " + (k + 1) + "층(" + h.lv[k] + ") " + n + "칸");
                }
            }
            if (!h.hipRoof && walk.reachedAt(h.lv[f]) < 6) {
                bad.add(tag + ": 옥상 " + walk.reachedAt(h.lv[f]));
            }
            if (h.basement && walk.reachedAt(-2) < 6) {
                bad.add(tag + ": 반지하 " + walk.reachedAt(-2));
            }
        });
        assertTrue(bad.isEmpty(), bad.size() + "곳: " + bad.stream().limit(15).toList());
    }

    @Test
    void carSpotsHaveRoomAndAWayOut() {
        List<String> bad = new ArrayList<>();
        int[] spots = {0};
        forAll(2, (dist, w, d, f, seed, h) -> {
            Voxels v = h.voxels();
            for (double[] s : v.carSpots()) {
                spots[0]++;
                int y = (int) s[1], ci = (int) Math.floor(s[0]), cj = (int) Math.floor(s[2]);
                String tag = dist + " " + w + "×" + d + " " + f + "층 #" + seed + " 자리 " + s[0] + "," + s[2];
                if (!air(v, ci, y, cj) || !air(v, ci, y + 1, cj) || !v.solid(ci, y - 1, cj)) {
                    bad.add(tag + ": 자리에 공간 없음");
                    continue;
                }
                if (s[3] != 0 || s[4] != 1) {
                    bad.add(tag + ": 정면을 안 봄");
                }
                // 차 몸통 (2칸 폭 × 5칸 길이) 자리가 비었는지
                double ax = s[0] - 1, az = s[2] - 2.5;
                for (int j = (int) Math.floor(az); j < (int) Math.ceil(az + 5) && j < v.d; j++) {
                    for (int i = (int) Math.floor(ax); i < (int) Math.ceil(ax + 2); i++) {
                        if (!air(v, i, y, j) || !air(v, i, y + 1, j)) {
                            bad.add(tag + ": 차 몸통 자리 막힘 " + i + "," + j);
                        }
                    }
                }
                // 3칸 폭 길로 앞(남쪽) 끝까지
                boolean way = false;
                for (int s0 = ci - 2; s0 <= ci && !way; s0++) {
                    boolean ok = true;
                    for (int j = cj; j < v.d && ok; j++) {
                        for (int i = s0; i <= s0 + 2 && ok; i++) {
                            Block floor = v.get(i, y - 1, j);
                            ok = air(v, i, y, j) && air(v, i, y + 1, j) && floor != null && !floor.isAir()
                                    && !floor.id().endsWith("_pane") && !floor.id().endsWith("iron_bars");
                        }
                    }
                    way = ok;
                }
                if (!way) {
                    bad.add(tag + ": 앞으로 나가는 3칸 길 없음");
                }
            }
        });
        assertTrue(spots[0] > 300, "주차 자리 " + spots[0]);
        assertTrue(bad.isEmpty(), bad.size() + "곳: " + bad.stream().limit(15).toList());
    }

    static boolean air(Voxels v, int i, int y, int j) {
        Block b = v.get(i, y, j);
        return b == null || b.isAir();
    }

    /** 살 만한 크기: 원룸 6×8(욕실 2×3), 투룸 9×10 넓이에 현관·거실·주방·침실 둘·욕실, 단독·연남동 집 크기 */
    @Test
    void homesAreLivableSize() {
        List<String> bad = new ArrayList<>();
        int[] homes = {0};
        forAll(2, (dist, w, d, f, seed, h) -> {
            String tag = dist + " " + w + "×" + d + " " + f + "층 #" + seed + " ";
            for (House.Dwelling dw : h.dwellings) {
                homes[0]++;
                String t = tag + dw + ": ";
                switch (dw.kind) {
                    case "원룸" -> {
                        if (dw.area < 48) {
                            bad.add(t + "원룸 넓이");
                        }
                        need(bad, t, dw, "욕실", 2, 3, 1);
                        if (!dw.named("원룸").isEmpty() && !dw.named("원룸").get(0).fits(6, 8)) {
                            bad.add(t + "원룸 6×8");
                        }
                    }
                    case "투룸" -> {
                        if (dw.area < 90) {
                            bad.add(t + "투룸 넓이");
                        }
                        need(bad, t, dw, "현관", 2, 2, 1);
                        need(bad, t, dw, "거실", 4, 5, 1);
                        need(bad, t, dw, "주방", 2, 4, 1);
                        need(bad, t, dw, "침실", 3, 4, 2);
                        need(bad, t, dw, "욕실", 2, 3, 1);
                    }
                    case "단독" -> {
                        int fw = h.i1 - h.i0 + 1, fd = h.jf - h.j0 + 1;
                        if (dist.equals("hongdae") && !(Math.min(fw, fd) >= 10 && Math.max(fw, fd) >= 12)) {
                            bad.add(t + "연남동 집 바닥 " + fw + "×" + fd);
                        }
                        if (dist.equals("bukhansan") && w >= 18 && d >= 20 && !(Math.min(fw, fd) >= 14 && Math.max(fw, fd) >= 16)) {
                            bad.add(t + "평창동 집 바닥 " + fw + "×" + fd);
                        }
                        need(bad, t, dw, "욕실", 2, 3, f >= 2 && dist.equals("bukhansan") && w >= 16 ? 2 : 1);
                        boolean big = dist.equals("bukhansan") && w >= 16 && d >= 20;
                        long beds = dw.rooms.stream().filter(s -> (s.name().equals("침실") || s.name().equals("안방")
                                || s.name().equals("손님방")) && s.fits(3, 4)).count();
                        if (beds < (f >= 2 ? (big ? 3 : 2) : 1)) {
                            bad.add(t + "침실 수 " + beds);
                        }
                        if (big) {
                            need(bad, t, dw, "거실", 6, 6, 1);
                            if (f >= 2) {
                                need(bad, t, dw, "안방", 5, 5, 1);
                            }
                        } else {
                            need(bad, t, dw, "거실", 4, 5, 1);
                        }
                    }
                    default -> {
                    }
                }
            }
        });
        assertTrue(homes[0] > 1000, "집 " + homes[0]);
        assertTrue(bad.isEmpty(), bad.size() + "곳: " + bad.stream().limit(15).toList());
    }

    private static void need(List<String> bad, String tag, House.Dwelling dw, String name, int a, int b, int count) {
        long n = dw.named(name).stream().filter(s -> s.fits(a, b)).count();
        if (n < count) {
            bad.add(tag + name + " " + a + "×" + b + " " + count + "개 필요 (" + n + ")");
        }
    }

    @Test
    void blocksExistAndNoBlockEntities() throws Exception {
        Set<String> known = new HashSet<>();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(
                HouseTest.class.getResourceAsStream("/block-ids-26.2.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = rd.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    known.add("minecraft:" + line.trim());
                }
            }
        }
        Set<String> unknown = new HashSet<>();
        List<String> longSigns = new ArrayList<>();
        String[] forbidden = {"chest", "_bed", "banner", "shulker", "decorated_pot", "skull", "_head", "bell", "conduit",
                "beacon", "enchanting", "lectern", "chiseled_bookshelf", "jukebox", "spawner"};
        forAll(1, (dist, w, d, f, seed, h) -> {
            Voxels v = h.voxels();
            for (int y = v.y0; y < v.y0 + v.h; y++) {
                for (int j = 0; j < v.d; j++) {
                    for (int i = 0; i < v.w; i++) {
                        Block b = v.get(i, y, j);
                        if (b == null) {
                            continue;
                        }
                        if (!known.contains(b.id())) {
                            unknown.add(b.id());
                        }
                        for (String x : forbidden) {
                            if (b.id().contains(x)) {
                                unknown.add(b.id() + " (블록 엔티티)");
                            }
                        }
                        for (String line : b.textLines()) {
                            int px = 0;
                            for (char ch : line.toCharArray()) {
                                px += ch >= 0xAC00 && ch <= 0xD7A3 ? 9 : 6;
                            }
                            if (px > 90) {
                                longSigns.add(line);
                            }
                        }
                    }
                }
            }
        });
        assertTrue(unknown.isEmpty(), "없는 블록: " + unknown);
        assertTrue(longSigns.isEmpty(), "긴 표지판: " + longSigns);
    }

    @Test
    void namesMatchBuildsAndAreFast() {
        long t = System.nanoTime();
        int n = 0;
        for (String dist : DISTRICTS) {
            for (int seed = 0; seed < 40; seed++) {
                String name = House.name(dist, new Random(seed));
                House.build(dist, 12 + seed % 13, 14 + seed % 11, 1 + seed % 6, new Random(seed));
                assertTrue(!name.isBlank());
                n++;
            }
        }
        double ms = (System.nanoTime() - t) / 1e6 / n;
        assertTrue(ms < 20, "한 채에 " + ms + "ms");
    }

    // ------------------------------------------------------------------ 미리보기

    /** 구역마다 거리 한 줄 + 속 보기: -DmapPreview=true 일 때 build/preview/dz-house-*.png */
    @Test
    void preview() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"));
        File dir = new File("build/preview");
        dir.mkdirs();
        String only = System.getProperty("previewOnly", "");
        String[] rows = {"university", "gwangjin", "gangnam", "songpa", "mapo", "hongdae", "yongsan", "bukhansan"};
        for (String dist : rows) {
            if (!("dz-house-" + dist).contains(only)) {
                continue;
            }
            Random r = new Random(dist.hashCode());
            List<Voxels> row = new ArrayList<>();
            int[][] sizes = {{14, 18}, {12, 16}, {16, 20}, {13, 18}, {18, 22}, {12, 18}};
            int[] fl = dist.equals("bukhansan") || dist.equals("hongdae") ? new int[]{2, 2, 2, 3, 2, 1}
                    : dist.equals("yongsan") || dist.equals("mapo") ? new int[]{3, 2, 3, 4, 3, 2} : new int[]{4, 5, 4, 4, 5, 3};
            for (int k = 0; k < sizes.length; k++) {
                row.add(House.build(dist, sizes[k][0], sizes[k][1], fl[k], new Random(r.nextLong())));
            }
            Voxels street = paste(row, 2);
            ImageIO.write(new IsoRender(street, 4).iso(7, 400), "png", new File(dir, "dz-house-" + dist + ".png"));
            // 길에서 본 눈높이 그림 (필로티·대문·반지하 창이 보이게)
            IsoRender eye = new IsoRender(street, 4);
            ImageIO.write(eye.perspective(street.w * 0.12, 1.7, street.d - 1.5, 222, 3, 70, 960, 540), "png",
                    new File(dir, "dz-house-eye-" + dist + ".png"));
        }
        // 속 보기 (2층 바닥 높이에서 자름)
        String[][] cuts = {{"university", "16", "18", "5"}, {"gwangjin", "14", "16", "4"}, {"mapo", "16", "18", "3"},
                {"hongdae", "13", "16", "2"}, {"bukhansan", "20", "22", "2"}, {"yongsan", "14", "18", "3"}};
        for (String[] c : cuts) {
            String name = "dz-house-cut-" + c[0];
            if (!name.contains(only)) {
                continue;
            }
            House h = House.make(c[0], Integer.parseInt(c[1]), Integer.parseInt(c[2]), Integer.parseInt(c[3]), new Random(7));
            Voxels v = h.voxels();
            int lvl = h.lv.length > 2 ? h.lv[1] : h.lv[0];
            ImageIO.write(new IsoRender(cut(v, lvl + 1), 2).iso(12, lvl + 1), "png", new File(dir, name + ".png"));
            ImageIO.write(new IsoRender(cut(v, h.lv[0] + 1), 2).iso(12, h.lv[0] + 1), "png", new File(dir, name + "-1f.png"));
            if (h.basement) {
                ImageIO.write(new IsoRender(cut(v, -1), 2).iso(12, -1), "png", new File(dir, name + "-b1.png"));
            }
        }
    }

    /** ymax 위를 잘라낸 복사본 (속 보기 그림을 작게) */
    static Voxels cut(Voxels v, int ymax) {
        Voxels out = new Voxels(v.w, v.d, v.y0, ymax);
        for (int y = v.y0; y <= ymax; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null) {
                        out.set(i, y, j, b);
                    }
                }
            }
        }
        return out;
    }

    /** 건물들을 가로로 나란히 (사이 gap 칸) */
    static Voxels paste(List<Voxels> list, int gap) {
        int w = 0, d = 0, y0 = 0, y1 = 0;
        for (Voxels v : list) {
            w += v.w + gap;
            d = Math.max(d, v.d);
            y0 = Math.min(y0, v.y0);
            y1 = Math.max(y1, v.y0 + v.h - 1);
        }
        Voxels out = new Voxels(w, d + 9, y0, y1);
        out.fill(0, -1, d, w - 1, -1, d + 1, Block.of("light_gray_concrete", 0x7D7D73));
        out.fill(0, -1, d + 2, w - 1, -1, d + 8, Block.of("gray_concrete", 0x36393D));
        int x = 0;
        for (Voxels v : list) {
            int dz = d - v.d;
            for (int y = v.y0; y < v.y0 + v.h; y++) {
                for (int j = 0; j < v.d; j++) {
                    for (int i = 0; i < v.w; i++) {
                        Block b = v.get(i, y, j);
                        if (b != null) {
                            out.set(x + i, y, dz + j, b);
                        }
                    }
                }
            }
            x += v.w + gap;
        }
        return out;
    }

    /** WalkCheck 과 같은 걷기 검사인데 땅 밑(반지하)까지 봄 */
    static final class Walk {
        private final Voxels v;
        private final int w, d, ylo, yhi;
        private final BitSet seen;

        Walk(Voxels v) {
            this.v = v;
            this.w = v.w + 2;
            this.d = v.d + 2;
            this.ylo = v.y0 + 1;
            this.yhi = v.y0 + v.h;
            this.seen = new BitSet(w * d * (yhi - ylo + 1));
        }

        private Block at(int x, int y, int z) {
            Block b = v.get(x - 1, y, z - 1);
            if (b == null) {
                return y < 0 ? GROUND : null;
            }
            return b.isAir() ? null : b;
        }

        private static final Block GROUND = Block.of("stone", 0);

        private boolean stand(int x, int y, int z) {
            if (x < 0 || z < 0 || x >= w || z >= d || y < ylo || y + 1 > yhi) {
                return false;
            }
            return WalkCheck.passable(at(x, y, z)) && WalkCheck.passable(at(x, y + 1, z)) && WalkCheck.support(at(x, y - 1, z));
        }

        private int key(int x, int y, int z) {
            return ((y - ylo) * d + z) * w + x;
        }

        Walk run() {
            ArrayDeque<int[]> q = new ArrayDeque<>();
            int sx = w / 2, sz = d - 1;
            seen.set(key(sx, 0, sz));
            q.add(new int[]{sx, 0, sz});
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!q.isEmpty()) {
                int[] p = q.poll();
                for (int[] dd : dirs) {
                    int nx = p[0] + dd[0], nz = p[2] + dd[1];
                    visit(q, nx, p[1], nz);
                    if (WalkCheck.step(at(nx, p[1], nz)) && WalkCheck.passable(at(p[0], p[1] + 2, p[2]))) {
                        visit(q, nx, p[1] + 1, nz);
                    }
                    visit(q, nx, p[1] - 1, nz);
                }
            }
            return this;
        }

        private void visit(ArrayDeque<int[]> q, int x, int y, int z) {
            if (stand(x, y, z) && !seen.get(key(x, y, z))) {
                seen.set(key(x, y, z));
                q.add(new int[]{x, y, z});
            }
        }

        int reachedAt(int y) {
            int n = 0;
            if (y < ylo || y + 1 > yhi) {
                return 0;
            }
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    if (seen.get(key(i + 1, y, j + 1))) {
                        n++;
                    }
                }
            }
            return n;
        }
    }
}
