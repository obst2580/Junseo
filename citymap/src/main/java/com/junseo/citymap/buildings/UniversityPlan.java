package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 대학교 구역: 대학병원(의료국 거점) 블록에 준서대학교병원과 앞 광장, 큰길 건너 남동쪽 블록에 서울대학교 본 캠퍼스
 * (정문 「샤」, 학생회관, 인문관, 중앙도서관과 아크로폴리스 광장, 공학관, 행정관, 연구소, 잔디밭과 길),
 * 정문 맞은편 블록에 기숙사(관악사 두 동). 나머지 블록은 동네 채우기가 원룸·빌라로 채웁니다.
 * <p>
 * 캠퍼스 건물 자리는 블록 북서 모서리에서 잰 거리로 정하고, 땅이 모자라면 그 건물은 빼고 짓습니다.
 * 정문 광장 가운데 (306, 537) 는 정문 거점 자리로 비워 둡니다.
 */
final class UniversityPlan {
    /** 정문 광장 거점 자리 (월드) */
    static final int GATE_HUB_X = 306, GATE_HUB_Z = 537;

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "university");
        if (area == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        Random rnd = Plans.random(t, "university");
        List<Placement> out = new ArrayList<>();
        hospital(t, m, rnd, out);
        campus(t, area, m, rnd, out);
        dorms(t, area, m, rnd, out);
        return out;
    }

    // ------------------------------------------------------------------ 대학병원

    private static void hospital(CityTerrain t, BuildMask m, Random rnd, List<Placement> out) {
        Layout.Hub hub = Plans.hub(t, "hospital");
        if (hub == null) {
            return;
        }
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] blk = m.componentNear(hx, hz, 30);
        if (blk == null || blk[2] - blk[0] + 1 < Hospital.MIN_W) {
            return;
        }
        int x0 = blk[0], x1 = blk[2], z1 = blk[3], z0 = z1 - Hospital.depth() + 1;
        if (z0 - 12 < hz || !m.rectFree(x0, z0, x1, z1)) {
            return;
        }
        long seed = rnd.nextLong();
        // 앞 광장 (큰길 쪽 여유 2칸까지)
        int fx0 = x0 - 2, fx1 = x1 + 2, fz0 = blk[1] - 2, fz1 = z0 - 1;
        Ground g = Ground.rect(t, fx0, fz0, fx1, fz1, "north");
        int hubI = fx1 - hx, hubJ = fz1 - hz;
        int erI = fx1 - (x1 - 10), mainI = fx1 - (x1 - (x1 - x0 + 1) / 2);
        long fseed = rnd.nextLong();
        out.add(Placement.rect("병원 앞 광장", "plaza", fx0, fz0, fx1, fz1, "north",
                (w, d) -> Hospital.forecourt(w, d, new Random(fseed), g, hubI, hubJ, erI, mainI)));
        out.add(Placement.rect(Hospital.NAME, "hospital", x0, z0, x1, z1, "north",
                (w, d) -> Hospital.build(w, d, new Random(seed))));
        m.claim(fx0, fz0, fx1, z1 + 1);
    }

    // ------------------------------------------------------------------ 본 캠퍼스

    /** 캠퍼스 건물 하나: 월드 사각형, 정면, 설정 */
    record Hall(int x0, int z0, int x1, int z1, String front, CampusHall.Spec spec) {
        boolean frontNS() {
            return front.equals("south") || front.equals("north");
        }

        /** 건물 상자 정면 너비와 깊이 */
        int w() {
            return frontNS() ? x1 - x0 + 1 : z1 - z0 + 1;
        }

        int d() {
            return frontNS() ? z1 - z0 + 1 : x1 - x0 + 1;
        }
    }

    /** 본 캠퍼스 건물들 (블록 북서 모서리 bx, bz 기준) */
    static List<Hall> halls(int bx, int bz) {
        List<Hall> halls = new ArrayList<>();
        CampusHall.Spec union = spec("학생회관", "63동", 4, CampusHall.Kind.UNION, CampusHall.Skin.BRICK);
        halls.add(new Hall(bx + 2, bz + 4, bx + 45, bz + 33, "south", union));
        CampusHall.Spec lectureA = spec("인문관", "14동", 5, CampusHall.Kind.LECTURE, CampusHall.Skin.BRICK);
        halls.add(new Hall(bx + 83, bz + 4, bx + 140, bz + 33, "south", lectureA));
        CampusHall.Spec lib = spec("중앙도서관", "62동", 5, CampusHall.Kind.LIBRARY, CampusHall.Skin.GLASS);
        lib.north = 16;
        lib.south = 16;
        halls.add(new Hall(bx + 5, bz + 44, bx + 48, bz + 97, "east", lib));
        CampusHall.Spec lectureB = spec("공학관", "301동", 5, CampusHall.Kind.LECTURE, CampusHall.Skin.BRICK);
        lectureB.south = 10;
        halls.add(new Hall(bx + 101, bz + 46, bx + 131, bz + 81, "west", lectureB));
        CampusHall.Spec admin = spec("행정관", "60동", 5, CampusHall.Kind.ADMIN, CampusHall.Skin.CONCRETE_FINS);
        admin.south = 10;
        halls.add(new Hall(bx + 48, bz + 103, bx + 88, bz + 133, "north", admin));
        CampusHall.Spec lab = spec("자연과학연구소", "220동", 6, CampusHall.Kind.LAB, CampusHall.Skin.CONCRETE_FINS);
        lab.south = 10;
        halls.add(new Hall(bx + 7, bz + 105, bx + 37, bz + 149, "east", lab));
        return halls;
    }

    /** 기숙사 한 동 설정 */
    static CampusHall.Spec dormSpec(String number) {
        CampusHall.Spec s = spec("관악사", number, 6, CampusHall.Kind.DORM, CampusHall.Skin.BRICK);
        s.north = 6;
        s.south = 7;
        s.front = 3;
        s.groundH = Floors.HOME;
        s.typicalH = Floors.HOME;
        return s;
    }

    private static CampusHall.Spec spec(String name, String number, int floors, CampusHall.Kind kind, CampusHall.Skin skin) {
        CampusHall.Spec s = new CampusHall.Spec(0, floors, kind, skin);
        s.name = name;
        s.number = number;
        return s;
    }

    private static void campus(CityTerrain t, Polygon area, BuildMask m, Random rnd, List<Placement> out) {
        int[] blk = m.componentNear(GATE_HUB_X, GATE_HUB_Z + 30, 20);
        if (blk == null || blk[2] - blk[0] < 150 || blk[3] - blk[1] < 140) {
            return;
        }
        int bx = blk[0], bz = blk[1];
        List<Hall> halls = halls(bx, bz);
        int gx0 = bx + 48, gz0 = bz + 1, gx1 = gx0 + SnuCampus.GATE_W - 1, gz1 = gz0 + SnuCampus.GATE_D - 1;
        boolean gate = m.rectFree(gx0, gz0, gx1, gz1);
        List<Hall> placed = new ArrayList<>();
        for (Hall h : halls) {
            if (h.d() == h.spec.depth() && m.rectFree(h.x0, h.z0, h.x1, h.z1)) {
                placed.add(h);
            }
        }

        // 캠퍼스 땅 지도
        int X0 = blk[0] - 2, Z0 = blk[1] - 2, X1 = blk[2] + 2, Z1 = blk[3] + 2;
        char[][] map = baseMap(t, area, m, X0, Z0, X1, Z1);
        for (Hall h : placed) {
            mark(map, X0, Z0, h.x0, h.z0, h.x1, h.z1, SnuCampus.BUILDING, true);
        }
        if (gate) {
            mark(map, X0, Z0, gx0, gz0, gx1, gz1, SnuCampus.BUILDING, true);
        }
        // 정문 광장과 가로수 길 (정문 ㅅ 아래 길이 가운데)
        int road = gx1 - SnuCampus.GATE_ROAD; // 정문은 정면이 북쪽이라 i 가 서쪽으로 감
        mark(map, X0, Z0, gx0 - 1, gz1 + 1, bx + 81, bz + 30, SnuCampus.PLAZA, false);
        mark(map, X0, Z0, road - 6, bz - 2, road + 6, gz0 - 1, SnuCampus.AVENUE, false);
        mark(map, X0, Z0, road - 6, bz + 31, road + 6, bz + 43, SnuCampus.AVENUE, false);
        mark(map, X0, Z0, GATE_HUB_X - 3, GATE_HUB_Z - 3, GATE_HUB_X + 3, GATE_HUB_Z + 3, SnuCampus.OPEN, false);
        // 학생회관·인문관 앞 길
        mark(map, X0, Z0, bx + 2, bz + 34, bx + 140, bz + 37, SnuCampus.PATH, false);
        // 아크로폴리스 (가운데 잔디)
        mark(map, X0, Z0, bx + 53, bz + 44, bx + 97, bz + 99, SnuCampus.PLAZA, false);
        mark(map, X0, Z0, bx + 63, bz + 55, bx + 87, bz + 88, SnuCampus.LAWN, false);
        // 도서관·공학관·행정관·연구소로 가는 길
        mark(map, X0, Z0, bx + 49, bz + 64, bx + 52, bz + 76, SnuCampus.PATH, false);
        mark(map, X0, Z0, bx + 98, bz + 58, bx + 100, bz + 70, SnuCampus.PATH, false);
        mark(map, X0, Z0, X0, bz + 100, bx + 97, bz + 102, SnuCampus.PATH, false); // 서쪽 큰길에서 들어오는 길
        mark(map, X0, Z0, bx + 38, bz + 102, bx + 46, bz + 133, SnuCampus.PATH, false);
        mark(map, X0, Z0, bx + 98, bz + 82, bx + 101, bz + 102, SnuCampus.PATH, false);

        long gseed = rnd.nextLong();
        Ground ground = Ground.rect(t, X0, Z0, X1, Z1, "south");
        out.add(Placement.rect("서울대학교 캠퍼스", "plaza", X0, Z0, X1, Z1, "south",
                (w, d) -> SnuCampus.grounds(w, d, map, ground, new Random(gseed))));
        if (gate) {
            out.add(Placement.rect("서울대학교 정문", "campus", gx0, gz0, gx1, gz1, "north", (w, d) -> SnuCampus.gate()));
        }
        for (Hall h : placed) {
            long seed = rnd.nextLong();
            out.add(Placement.rect("서울대학교 " + h.spec.name, "campus", h.x0, h.z0, h.x1, h.z1, h.front,
                    (w, d) -> CampusHall.build(h.spec, w, d, new Random(seed))));
        }
        m.claim(X0, Z0, X1, Z1);
    }

    // ------------------------------------------------------------------ 기숙사

    private static void dorms(CityTerrain t, Polygon area, BuildMask m, Random rnd, List<Placement> out) {
        int[] blk = m.componentNear(GATE_HUB_X - 20, GATE_HUB_Z - 90, 20);
        if (blk == null || blk[2] - blk[0] < 80 || blk[3] - blk[1] < 80 || blk[4] < 6000) {
            return;
        }
        int bx = blk[0], bz = blk[1];
        int w = Math.min(70, blk[2] - blk[0] - 14);
        int x0 = bx + (blk[2] - blk[0] + 1 - w) / 2, x1 = x0 + w - 1;
        List<int[]> rects = new ArrayList<>();
        String[] numbers = {"900동", "901동"};
        int[] zs = {bz + 4, bz + 56};
        int X0 = blk[0] - 2, Z0 = blk[1] - 2, X1 = blk[2] + 2, Z1 = blk[3] + 2;
        char[][] map = baseMap(t, area, m, X0, Z0, X1, Z1);
        List<Placement> buildings = new ArrayList<>();
        for (int n = 0; n < 2; n++) {
            CampusHall.Spec s = dormSpec(numbers[n]);
            int z0 = zs[n], z1 = z0 + s.depth() - 1;
            if (z1 > blk[3] - 6 || !m.rectFree(x0, z0, x1, z1)) {
                continue;
            }
            mark(map, X0, Z0, x0, z0, x1, z1, SnuCampus.BUILDING, true);
            mark(map, X0, Z0, x0 - 6, z1 + 1, x1 + 6, z1 + 4, SnuCampus.PATH, false);
            rects.add(new int[]{x0, z0, x1, z1});
            long seed = rnd.nextLong();
            buildings.add(Placement.rect("서울대학교 관악사 " + numbers[n], "campus", x0, z0, x1, z1, "south",
                    (bw, bd) -> CampusHall.build(s, bw, bd, new Random(seed))));
        }
        if (buildings.isEmpty()) {
            return;
        }
        // 가운데 남북 길 (큰길까지)
        int mx = (x0 + x1) / 2;
        mark(map, X0, Z0, mx - 2, bz - 2, mx + 2, blk[3] + 2, SnuCampus.PATH, false);
        long gseed = rnd.nextLong();
        Ground ground = Ground.rect(t, X0, Z0, X1, Z1, "south");
        out.add(Placement.rect("서울대학교 기숙사 단지", "plaza", X0, Z0, X1, Z1, "south",
                (gw, gd) -> SnuCampus.grounds(gw, gd, map, ground, new Random(gseed))));
        out.addAll(buildings);
        m.claim(X0, Z0, X1, Z1);
    }

    // ------------------------------------------------------------------ 땅 지도

    /** 블록 땅: 구역 안의 평평한 땅은 잔디, 길에서 2칸 안은 보도, 길·물·구역 밖은 비움 */
    private static char[][] baseMap(CityTerrain t, Polygon area, BuildMask m, int X0, int Z0, int X1, int Z1) {
        char[][] map = new char[Z1 - Z0 + 1][X1 - X0 + 1];
        for (int z = Z0; z <= Z1; z++) {
            for (int x = X0; x <= X1; x++) {
                Column c = t.column(x, z);
                char ch;
                if (c.isRoad() || c.isWater() || c.deck || c.tunnel || c.mountainHeight > 0 || c.hubPillar > 0
                        || !area.contains(x + 0.5, z + 0.5)) {
                    ch = SnuCampus.OUT;
                } else if (m.roadDistance(x, z) <= 2) {
                    ch = SnuCampus.EDGE;
                } else {
                    ch = SnuCampus.LAWN;
                }
                map[z - Z0][x - X0] = ch;
            }
        }
        return map;
    }

    /** 지도에 사각형 칸 종류를 칠함 (밖은 그대로, 건물 칸은 force 일 때만 덮음) */
    private static void mark(char[][] map, int X0, int Z0, int x0, int z0, int x1, int z1, char c, boolean force) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                int j = z - Z0, i = x - X0;
                if (j < 0 || i < 0 || j >= map.length || i >= map[0].length) {
                    continue;
                }
                char old = map[j][i];
                if (old == SnuCampus.OUT || (old == SnuCampus.BUILDING && !force)) {
                    continue;
                }
                map[j][i] = c;
            }
        }
    }

    private UniversityPlan() {
    }
}
