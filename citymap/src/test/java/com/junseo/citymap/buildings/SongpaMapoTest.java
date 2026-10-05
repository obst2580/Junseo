package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 송파(테마파크 부지·초고층 타워·잠실 야구장)와 마포(서울월드컵경기장): 지어졌는지, 모든 층·관중석에 걸어서 가는지 */
class SongpaMapoTest {

    /** 이 종류·이름의 건물 (다른 구역도 같은 종류를 쓸 수 있어서 이름으로 찾음) */
    static Placement find(String kind) {
        String name = switch (kind) {
            case "site" -> "테마파크 건설 예정 부지";
            case "skyscraper" -> "준서월드타워";
            case "ballpark" -> "잠실 야구장";
            default -> "서울월드컵경기장";
        };
        for (Placement p : TestCity.buildings().placements()) {
            if (p.kind.equals(kind) && p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    @Test
    void themeParkSiteOfficeIsWalkable() {
        Placement p = find("site");
        assertNotNull(p, "테마파크 부지");
        Voxels v = p.voxels();
        WalkCheck walk = new WalkCheck(v).run();
        // 현장 사무실 1층(서는 높이 0)과 2층(4)
        assertTrue(walk.reachedAt(0) > 2000, "부지 안 땅");
        assertTrue(walk.reachedAt(4) > 20, "현장 사무실 2층: " + walk.reachedAt(4));
    }

    /**
     * 땅 아래로 판 곳(낮춘 경기장)까지 걷는 검사. WalkCheck 는 y 0 아래를 땅으로 보므로, 건물을 DEPTH 칸 올린 사본에서
     * 걷고 (원래 땅 아래 빈 칸은 흙으로 채움) 좌표를 되돌려 줍니다. 거점에서 출발합니다.
     */
    static final class DeepWalk {
        static final int DEPTH = 12;
        final Voxels v;
        final WalkCheck walk;

        DeepWalk(Voxels src, int[] hub) {
            v = src;
            Voxels c = new Voxels(src.w, src.d, 0, src.y0 + src.h - 1 + DEPTH);
            Block soil = Block.of("dirt", 0x86603F);
            for (int y = src.y0; y < src.y0 + src.h; y++) {
                for (int j = 0; j < src.d; j++) {
                    for (int i = 0; i < src.w; i++) {
                        Block b = src.get(i, y, j);
                        if (b == null && y < 0) {
                            b = soil;
                        }
                        c.set(i, y + DEPTH, j, b);
                    }
                }
            }
            for (int y = 0; y < src.y0 + DEPTH; y++) {
                c.fill(0, y, 0, src.w - 1, y, src.d - 1, soil);
            }
            walk = new WalkCheck(c).run(hub[0] + 1, DEPTH, hub[1] + 1);
        }

        boolean reached(int i, int y, int j) {
            return walk.reached(i, y + DEPTH, j);
        }

        int reachedAt(int y) {
            return walk.reachedAt(y + DEPTH);
        }

        /** 이 높이 범위에서 갈 수 있는 칸 수 */
        String profile(int ya, int yb) {
            StringBuilder sb = new StringBuilder();
            for (int y = ya; y <= yb; y++) {
                sb.append(y).append(':').append(reachedAt(y)).append(' ');
            }
            return sb.toString();
        }
    }

    static int[] hub(String district, String hub, int maxSide) {
        return SongpaPlan.hubLocal(TestCity.terrain(), district, hub, maxSide);
    }

    /**
     * 관중석 좌석·통로 계단(계단 블록) 중 걸어서 올라설 수 있는 것. {아래층 좌석 수, 간 수, 위층 좌석 수, 간 수}
     * splitY 보다 낮으면 아래층.
     */
    static int[] seats(DeepWalk walk, java.util.Set<String> ids, int splitY) {
        Voxels v = walk.v;
        int[] n = new int[4];
        for (int y = v.y0; y < v.y0 + v.h - 2; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b == null || !ids.contains(b.id())) {
                        continue;
                    }
                    if (!open(v.get(i, y + 1, j)) || !open(v.get(i, y + 2, j))) {
                        continue;
                    }
                    int k = y < splitY ? 0 : 2;
                    n[k]++;
                    if (walk.reached(i, y + 1, j)) {
                        n[k + 1]++;
                    }
                }
            }
        }
        return n;
    }

    /** 표지판 글씨에 이 낱말이 든 것 수 */
    static int signs(Voxels v, String word) {
        int n = 0;
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null && b.text() != null && String.join(" ", b.textLines()).contains(word)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    static boolean open(Block b) {
        return b == null || b.isAir() || WalkCheck.passable(b);
    }

    @Test
    void worldCupStadiumEveryTierWalkable() {
        Placement p = find("stadium");
        assertNotNull(p, "서울월드컵경기장");
        DeepWalk walk = new DeepWalk(p.voxels(), hub("mapo", "football", MapoPlan.MAX_SIDE));
        int[] n = seats(walk, java.util.Set.of("minecraft:red_nether_brick_stairs", "minecraft:resin_brick_stairs",
                "minecraft:polished_andesite_stairs"), 0);
        System.out.println("월드컵경기장 좌석: 1층 " + n[1] + "/" + n[0] + ", 2층 " + n[3] + "/" + n[2] + ", 높이별 " + walk.profile(-8, 20));
        assertTrue(n[0] > 1000 && n[2] > 1000, "좌석 수");
        assertTrue(n[1] >= n[0] * 0.97, "1층 관중석에 걸어서: " + n[1] + "/" + n[0]);
        assertTrue(n[3] >= n[2] * 0.97, "2층 관중석에 걸어서: " + n[3] + "/" + n[2]);
        assertTrue(walk.reachedAt(FootballStadium.FIELD + 1) > 2000, "피치에 선수 통로로: " + walk.reachedAt(FootballStadium.FIELD + 1));
        assertTrue(signs(p.voxels(), "매표소") >= 2, "매표소");
        assertTrue(signs(p.voxels(), "서울월드컵경기장") >= 1, "정문 이름");
        assertTrue(signs(p.voxels(), "화장실") >= 4, "화장실");
        assertTrue(signs(p.voxels(), "치킨") >= 1, "매점");
        assertTrue(walk.reachedAt(0) > 3000, "콘코스");
    }

    @Test
    void ballparkEveryStandWalkable() {
        Placement p = find("ballpark");
        assertNotNull(p, "잠실 야구장");
        DeepWalk walk = new DeepWalk(p.voxels(), hub("songpa", "ballpark", SongpaPlan.MAX_SIDE));
        int[] n = seats(walk, java.util.Set.of("minecraft:warped_stairs", "minecraft:waxed_cut_copper_stairs",
                "minecraft:red_nether_brick_stairs", "minecraft:waxed_oxidized_cut_copper_stairs", "minecraft:polished_andesite_stairs"), 0);
        System.out.println("잠실 야구장 좌석: 1층 " + n[1] + "/" + n[0] + ", 2층 " + n[3] + "/" + n[2] + ", 높이별 " + walk.profile(-8, 20));
        assertTrue(n[0] > 800 && n[2] > 300, "좌석 수");
        assertTrue(n[1] >= n[0] * 0.97, "1층 관중석에 걸어서: " + n[1] + "/" + n[0]);
        assertTrue(n[3] >= n[2] * 0.97, "2층 관중석에 걸어서: " + n[3] + "/" + n[2]);
        assertTrue(walk.reachedAt(Ballpark.FIELD + 1) > 1500, "그라운드에 더그아웃 통로로: " + walk.reachedAt(Ballpark.FIELD + 1));
        System.out.println("잠실 야구장 표지판: 매표소 " + signs(p.voxels(), "매표소") + ", 화장실 " + signs(p.voxels(), "화장실")
                + ", 매점 " + signs(p.voxels(), "치킨"));
        assertTrue(signs(p.voxels(), "매표소") >= 1, "매표소");
        assertTrue(signs(p.voxels(), "잠실야구장") >= 1, "정문 이름");
        assertTrue(signs(p.voxels(), "화장실") >= 2, "화장실");
        assertTrue(signs(p.voxels(), "치킨") >= 1, "매점");
    }

    @Test
    void supertallEveryFloorWalkable() {
        Placement p = find("skyscraper");
        assertNotNull(p, "준서월드타워");
        Voxels v = p.voxels();
        int[] h = hub("songpa", "skyscraper", SongpaPlan.MAX_SIDE);
        WalkCheck walk = new WalkCheck(v).run(h[0] + 1, 0, h[1] + 1);
        int[] levels = Floors.levels(Floors.GROUND, Floors.OFFICE, SupertallTower.FLOORS);
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < levels.length; k++) {
            sb.append(k + 1).append("F:").append(walk.reachedAt(levels[k])).append(' ');
        }
        System.out.println("준서월드타워 층별 걸어서 간 칸: " + sb);
        for (int k = 0; k < levels.length; k++) {
            int n = walk.reachedAt(levels[k]);
            assertTrue(n > 20, "준서월드타워 " + (k + 1) + "층(서는 높이 " + levels[k] + ")에 걸어서 못 가요 (" + n + "칸)");
        }
        assertTrue(v.y0 + v.h - 1 <= 317, "높이 한도");
    }

    @Test
    void renderPreviews() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        // -DpreviewOnly=lm-site:3,7 → 테마파크 부지만, 높이 3·7 에서 자른 그림도
        String spec = System.getProperty("previewOnly", "");
        String[] parts = spec.split(":", -1);
        String only = parts[0];
        String cuts = parts.length > 1 ? parts[1] : "";
        // 세 번째 칸: 부분만 크게 (i0,j0,i1,j1,배율[,y0,y1])
        int[] crop = parts.length > 2 ? java.util.Arrays.stream(parts[2].split(",")).mapToInt(Integer::parseInt).toArray() : null;
        File dir = new File("build/preview");
        dir.mkdirs();
        for (String kind : new String[]{"site", "skyscraper", "ballpark", "stadium"}) {
            if (!only.isEmpty() && !("lm-" + kind).contains(only)) {
                continue;
            }
            Placement p = find(kind);
            if (p == null) {
                continue;
            }
            Voxels v = p.voxels();
            int scale = v.h > 100 ? 2 : 4;
            if (crop != null) {
                int ya = crop.length > 5 ? crop[5] : v.y0, yb = crop.length > 6 ? crop[6] : v.y0 + v.h - 1;
                Voxels c = new Voxels(crop[2] - crop[0] + 1, crop[3] - crop[1] + 1, ya, yb);
                for (int y = ya; y <= yb; y++) {
                    for (int j = crop[1]; j <= crop[3]; j++) {
                        for (int i = crop[0]; i <= crop[2]; i++) {
                            c.set(i - crop[0], y, j - crop[1], v.get(i, y, j));
                        }
                    }
                }
                v = c;
                scale = crop[4];
            }
            ImageIO.write(new IsoRender(v, 2).iso(scale, Integer.MAX_VALUE), "png", new File(dir, "lm-" + kind + ".png"));
            ImageIO.write(new IsoRender(v, 2).top(crop != null ? scale : 6), "png", new File(dir, "lm-" + kind + "-top.png"));
            if (kind.equals("skyscraper") && crop == null) {
                // 멀리서 본 원근 그림 (정면, 중간 높이에서): 전체 모습
                IsoRender ir = new IsoRender(v, 2);
                ImageIO.write(ir.perspective(v.w / 2.0, 150, v.d + 150, 180, 0, 100, 720, 1100), "png", new File(dir, "lm-skyscraper-far.png"));
                ImageIO.write(ir.perspective(v.w / 2.0 + 30, 2.6, v.d - 6, 160, -35, 85, 1280, 720), "png", new File(dir, "lm-skyscraper-plaza.png"));
            }
            for (String cut : cuts.split(",")) {
                if (!cut.isBlank()) {
                    int y = Integer.parseInt(cut.trim());
                    ImageIO.write(new IsoRender(v, 2).iso(crop != null ? scale : Math.max(scale, 4), y), "png", new File(dir, "lm-" + kind + "-cut" + y + ".png"));
                }
            }
        }
    }
}
