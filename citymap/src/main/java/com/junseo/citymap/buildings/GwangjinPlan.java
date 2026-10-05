package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 광진구: 대공원 거점(park) 블록에 서울어린이대공원 같은 공원(정문은 거점 쪽 서쪽 큰길), 그 남쪽 한강변 블록에
 * 카지노 리조트(준서카지노 호텔)와 주차장. 나머지 블록은 동네 채우기가 빌라·원룸촌으로 채웁니다.
 * <p>
 * 시설 자리는 블록 북서 모서리에서 잰 거리(u, v)로 정하고, 땅이 모자라면 그 시설은 빼고 짓습니다.
 */
final class GwangjinPlan {
    /** 카지노 앞 보행 광장 (거점 후보 자리, 월드) */
    static int casinoHubX = Integer.MIN_VALUE, casinoHubZ = Integer.MIN_VALUE;

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "gwangjin");
        if (area == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        Random rnd = Plans.random(t, "gwangjin");
        List<Placement> out = new ArrayList<>();
        park(t, area, m, rnd, out);
        casino(t, m, rnd, out);
        return out;
    }

    // ------------------------------------------------------------------ 대공원

    private static void park(CityTerrain t, Polygon area, BuildMask m, Random rnd, List<Placement> out) {
        Layout.Hub hub = Plans.hub(t, "park");
        if (hub == null) {
            return;
        }
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] blk = m.componentNear(hx, hz, 20);
        if (blk == null || blk[2] - blk[0] < 95 || blk[3] - blk[1] < 80) {
            return;
        }
        int bx = blk[0], bz = blk[1];
        int X0 = blk[0] - 2, Z0 = blk[1] - 2, X1 = blk[2] + 2, Z1 = blk[3] + 2;
        char[][] map = new char[Z1 - Z0 + 1][X1 - X0 + 1];
        for (int z = Z0; z <= Z1; z++) {
            for (int x = X0; x <= X1; x++) {
                Column c = t.column(x, z);
                char ch;
                if (c.isRoad() || c.isWater() || c.deck || c.tunnel || c.mountainHeight > 0 || !area.contains(x + 0.5, z + 0.5)) {
                    ch = SnuCampus.OUT;
                } else if (c.hubPillar > 0) {
                    ch = SnuCampus.OPEN;
                } else if (m.roadDistance(x, z) <= 2) {
                    ch = SnuCampus.EDGE;
                } else {
                    ch = SnuCampus.LAWN;
                }
                map[z - Z0][x - X0] = ch;
            }
        }
        // 정문 광장 (거점이 있는 서쪽), 거점 둘레는 비움
        int hu = hx - bx, hv = hz - bz;
        mark(map, X0, Z0, bx - 2, bz + hv - 15, bx + 20, bz + hv + 13, SnuCampus.PLAZA);
        mark(map, X0, Z0, hx - 3, hz - 3, hx + 3, hz + 3, SnuCampus.OPEN);
        // 큰길: 정문에서 분수 광장, 동문까지
        mark(map, X0, Z0, bx + 21, bz + hv - 3, bx + 52, bz + hv + 3, SnuCampus.AVENUE);
        mark(map, X0, Z0, bx + 69, bz + hv - 2, blk[2] + 2, bz + hv + 2, SnuCampus.PATH);
        // 분수 광장 (둥글게)
        int fu = 60, fv = hv;
        disk(map, X0, Z0, bx + fu, bz + fv, 10.5, SnuCampus.PLAZA);
        // 연못과 둘레 길, 분수 광장에서 연못으로
        int pu = 44, pv = hv - 34, pri = 16, prj = 9;
        ring(map, X0, Z0, bx + pu, bz + pv, pri + 3.5, prj + 3.5, pri + 1.0, prj + 1.0, SnuCampus.PATH);
        ellipse(map, X0, Z0, bx + pu, bz + pv, pri + 1.0, prj + 1.0, SnuCampus.WATER);
        mark(map, X0, Z0, bx + 58, bz + pv + prj, bx + 62, bz + fv - 9, SnuCampus.PATH);
        // 정자 자리 (연못 서쪽)
        int gu = pu - pri - 7, gv = pv;
        mark(map, X0, Z0, bx + gu - 4, bz + gv - 4, bx + gu + 4, bz + gv + 5, SnuCampus.FIELD);
        mark(map, X0, Z0, bx + gu - 1, bz + gv + 5, bx + gu + 1, bz + gv + 9, SnuCampus.PATH);
        // 동물마을: 우리 셋과 사이 길
        int[][] paddocks = {{72, pv - 15, 85, pv + 3}, {88, pv - 15, 99, pv + 3}, {72, pv + 8, 99, fv - 9}};
        mark(map, X0, Z0, bx + 68, bz + pv - 17, bx + 71, bz + fv - 3, SnuCampus.PATH);
        mark(map, X0, Z0, bx + 72, bz + pv + 4, bx + 99, bz + pv + 7, SnuCampus.PATH);
        mark(map, X0, Z0, bx + 86, bz + pv - 15, bx + 87, bz + pv + 3, SnuCampus.PATH);
        for (int[] p : paddocks) {
            mark(map, X0, Z0, bx + p[0], bz + p[1], bx + p[2], bz + p[3], SnuCampus.FIELD);
        }
        // 남쪽 길, 놀이터(모래), 잔디광장
        mark(map, X0, Z0, bx + 21, bz + fv + 7, bx + 101, bz + fv + 9, SnuCampus.PATH);
        int[] play = {24, fv + 12, 42, fv + 25};
        mark(map, X0, Z0, bx + play[0], bz + play[1], bx + play[2], bz + play[3], SnuCampus.SAND);
        mark(map, X0, Z0, bx + 75, bz + fv + 12, bx + 97, bz + fv + 23, SnuCampus.FIELD);
        // 꽃밭 (큰길 양옆)
        List<int[]> beds = new ArrayList<>();
        for (int u = 24; u + 4 <= 50; u += 7) {
            for (int dv : new int[]{-7, 5}) {
                beds.add(new int[]{bx + u - X0, bz + fv + dv - Z0, bx + u + 4 - X0, bz + fv + dv + 2 - Z0});
                mark(map, X0, Z0, bx + u, bz + fv + dv, bx + u + 4, bz + fv + dv + 2, SnuCampus.FIELD);
            }
        }

        // 따로 놓는 건물: 정문, 화장실, 매점 둘
        List<Placement> buildings = new ArrayList<>();
        int gx0 = bx + 15, gx1 = gx0 + 5, gz0 = hz - 11, gz1 = hz + 11;
        if (m.rectFree(gx0, gz0, gx1, gz1)) {
            mark(map, X0, Z0, gx0, gz0, gx1, gz1, SnuCampus.BUILDING);
            buildings.add(Placement.rect(GrandPark.NAME + " 정문", "park", gx0, gz0, gx1, gz1, "west", (w, d) -> GrandPark.gate(w, d)));
        }
        int[][] small = {
                {bx + 52, bz + fv + 12, bx + 62, bz + fv + 20}, // 화장실 (정면 북쪽)
                {bx + 74, bz + fv - 8, bx + 80, bz + fv - 3},   // 매점 1 (정면 남쪽)
                {bx + 45, bz + fv + 12, bx + 51, bz + fv + 17}, // 매점 2 (정면 북쪽)
        };
        String[] fronts = {"north", "south", "north"};
        for (int n = 0; n < small.length; n++) {
            int[] s = small[n];
            if (!m.rectFree(s[0], s[1], s[2], s[3])) {
                continue;
            }
            mark(map, X0, Z0, s[0], s[1], s[2], s[3], SnuCampus.BUILDING);
            if (n == 0) {
                mark(map, X0, Z0, s[0] + 3, bz + fv + 10, s[2] - 3, s[1] - 1, SnuCampus.PATH);
                buildings.add(Placement.rect(GrandPark.NAME + " 화장실", "park", s[0], s[1], s[2], s[3], fronts[n], (w, d) -> GrandPark.restroom(w, d)));
            } else {
                buildings.add(Placement.rect(GrandPark.NAME + " 매점", "park", s[0], s[1], s[2], s[3], fronts[n], (w, d) -> GrandPark.kiosk(w, d)));
            }
        }
        int[][] pads = new int[paddocks.length][];
        for (int n = 0; n < paddocks.length; n++) {
            pads[n] = new int[]{bx + paddocks[n][0] - X0, bz + paddocks[n][1] - Z0, bx + paddocks[n][2] - X0, bz + paddocks[n][3] - Z0};
        }
        GrandPark.Layout lay = new GrandPark.Layout(bx + fu - X0, bz + fv - Z0, bx + pu - X0, bz + pv - Z0, pri, prj,
                bx + gu - X0, bz + gv - Z0, pads,
                new int[]{bx + play[0] - X0, bz + play[1] - Z0, bx + play[2] - X0, bz + play[3] - Z0},
                bx + 24 - X0, bz + fv - 10 - Z0, beds.toArray(new int[0][]));
        long seed = rnd.nextLong();
        Ground ground = Ground.rect(t, X0, Z0, X1, Z1, "south");
        out.add(Placement.rect(GrandPark.NAME, "plaza", X0, Z0, X1, Z1, "south",
                (w, d) -> GrandPark.grounds(w, d, map, ground, new Random(seed), lay)));
        out.addAll(buildings);
        m.claim(X0, Z0, X1, Z1);
    }

    // ------------------------------------------------------------------ 카지노

    private static void casino(CityTerrain t, BuildMask m, Random rnd, List<Placement> out) {
        Layout.Hub hub = Plans.hub(t, "park");
        if (hub == null) {
            return;
        }
        // 대공원 남쪽, 한강변 블록
        int[] blk = m.componentNear((int) hub.x() + 40, (int) hub.z() + 90, 25);
        if (blk == null || blk[2] - blk[0] + 1 < Casino.MIN_W + 26 || blk[3] - blk[1] + 1 < Casino.MIN_D) {
            return;
        }
        int x0 = blk[0] + 2, z0 = blk[1];
        int w = Math.min(74, blk[2] - blk[0] - 25), d = Math.min(62, blk[3] - blk[1] - 2);
        int x1 = x0 + w - 1, z1 = z0 + d - 1;
        if (!m.rectFree(x0, z0, x1, z1) || w < Casino.MIN_W || d < Casino.MIN_D) {
            return;
        }
        long seed = rnd.nextLong();
        out.add(Placement.rect(Casino.NAME, "casino", x0, z0, x1, z1, "north", (bw, bd) -> Casino.build(bw, bd, new Random(seed))));
        // 앞 보행 광장 (거점 후보): 상자 서쪽 끝 앞
        casinoHubX = x0 + 9;
        casinoHubZ = z0 + 5;
        m.claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
        // 주차장 (동쪽, 주차선만)
        int px0 = x1 + 4, px1 = blk[2], pz0 = blk[1], pz1 = Math.min(blk[3], z1);
        if (px1 - px0 >= 15 && m.rectFree(px0, pz0, px1, pz1)) {
            out.add(Placement.rect(Casino.NAME + " 주차장", "parking", px0, pz0, px1, pz1, "north", (pw, pd) -> Kit.parking(pw, pd)));
            m.claim(px0 - 1, pz0 - 1, px1 + 1, pz1 + 1);
        }
    }

    // ------------------------------------------------------------------ 땅 지도

    private static void mark(char[][] map, int X0, int Z0, int x0, int z0, int x1, int z1, char c) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                set(map, x - X0, z - Z0, c);
            }
        }
    }

    private static void set(char[][] map, int i, int j, char c) {
        if (j < 0 || i < 0 || j >= map.length || i >= map[0].length) {
            return;
        }
        char old = map[j][i];
        if (old == SnuCampus.OUT || (old == SnuCampus.OPEN && c != SnuCampus.BUILDING) || old == SnuCampus.BUILDING) {
            return;
        }
        map[j][i] = c;
    }

    private static void disk(char[][] map, int X0, int Z0, int cx, int cz, double r, char c) {
        ellipse(map, X0, Z0, cx, cz, r, r, c);
    }

    private static void ellipse(char[][] map, int X0, int Z0, int cx, int cz, double ri, double rj, char c) {
        for (int z = (int) (cz - rj - 1); z <= cz + rj + 1; z++) {
            for (int x = (int) (cx - ri - 1); x <= cx + ri + 1; x++) {
                double a = (x + 0.5 - cx - 0.5) / ri, b = (z + 0.5 - cz - 0.5) / rj;
                if (a * a + b * b <= 1) {
                    set(map, x - X0, z - Z0, c);
                }
            }
        }
    }

    private static void ring(char[][] map, int X0, int Z0, int cx, int cz, double ri, double rj, double ii, double ij, char c) {
        for (int z = (int) (cz - rj - 1); z <= cz + rj + 1; z++) {
            for (int x = (int) (cx - ri - 1); x <= cx + ri + 1; x++) {
                double a = (x - cx) / ri, b = (z - cz) / rj, a2 = (x - cx) / ii, b2 = (z - cz) / ij;
                if (a * a + b * b <= 1 && a2 * a2 + b2 * b2 > 1) {
                    set(map, x - X0, z - Z0, c);
                }
            }
        }
    }

    private GwangjinPlan() {
    }
}
