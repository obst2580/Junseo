package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;

import java.util.ArrayList;
import java.util.List;

/**
 * 도시의 건물 목록과, 생성기가 칸마다 건물 블록을 얻어 가는 곳.
 * 설계도(layout.json)와 구역별 계획(랜드마크 + 동네 채우기)으로 정해지므로 같은 설계도면 늘 같은 도시가 나옵니다.
 * 한 번 만들면 바뀌지 않아서 여러 스레드에서 같이 써도 됩니다 (건물 블록 상자는 처음 쓸 때 만듦).
 */
public final class CityBuildings {
    /** 도로·물 위로는 이 높이(땅 위 칸 수)부터만 놓음: 길 위를 가로지르는 문·지붕은 되고 길을 막지는 않게 */
    static final int ROAD_CLEARANCE = 6;
    /** 거리 시설이 차도 위로 뻗을 때 이 높이(땅 위 칸 수)부터 */
    static final int STREET_CLEARANCE = 4;
    /** 거점 표시 기둥 위로는 이 높이부터만 */
    static final int HUB_CLEARANCE = 9;
    private static final int CELL = 64;

    private final CityTerrain terrain;
    private final List<Placement> placements;
    private final int gx0, gz0, gw, gh;
    private final int[][] cells;

    private CityBuildings(CityTerrain terrain, List<Placement> placements) {
        this.terrain = terrain;
        this.placements = List.copyOf(placements);
        double[] min = terrain.layout().borderMin(), max = terrain.layout().borderMax();
        gx0 = Math.floorDiv((int) Math.floor(min[0]), CELL);
        gz0 = Math.floorDiv((int) Math.floor(min[1]), CELL);
        gw = Math.floorDiv((int) Math.ceil(max[0]), CELL) - gx0 + 1;
        gh = Math.floorDiv((int) Math.ceil(max[1]), CELL) - gz0 + 1;
        List<List<Integer>> lists = new ArrayList<>();
        for (int i = 0; i < gw * gh; i++) {
            lists.add(new ArrayList<>());
        }
        for (int k = 0; k < this.placements.size(); k++) {
            double[] b = this.placements.get(k).bounds();
            for (int cz = Math.floorDiv((int) Math.floor(b[1]), CELL); cz <= Math.floorDiv((int) Math.ceil(b[3]), CELL); cz++) {
                for (int cx = Math.floorDiv((int) Math.floor(b[0]), CELL); cx <= Math.floorDiv((int) Math.ceil(b[2]), CELL); cx++) {
                    int gx = cx - gx0, gz = cz - gz0;
                    if (gx >= 0 && gz >= 0 && gx < gw && gz < gh) {
                        lists.get(gz * gw + gx).add(k);
                    }
                }
            }
        }
        cells = new int[gw * gh][];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = lists.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /** 설계도로 도시 전체 건물 배치를 정합니다 (구역 땅을 훑느라 1~2초 걸림) */
    public static CityBuildings plan(CityTerrain terrain) {
        List<Placement> all = new ArrayList<>();
        all.addAll(StreetPlan.paving(terrain)); // 포장이 먼저, 건물이 그 위를 덮음
        all.addAll(AirportPlan.plan(terrain));
        all.addAll(CheongnaPlan.plan(terrain));
        all.addAll(GuincheonPlan.plan(terrain));
        all.addAll(MarketPlan.plan(terrain));
        all.addAll(YongsanPlan.plan(terrain));
        all.addAll(GangnamPlan.plan(terrain));
        all.addAll(HongdaePlan.plan(terrain));
        all.addAll(UniversityPlan.plan(terrain));
        all.addAll(GwangjinPlan.plan(terrain));
        // 랜드마크를 먼저 짓고, 남은 동네 땅을 일반 건물로 채움
        for (String id : DistrictFill.DISTRICTS) {
            all.addAll(DistrictFill.fill(terrain, id, all));
        }
        all.addAll(StreetPlan.furniture(terrain));
        all.addAll(StreetPlan.hubCovers(terrain)); // 거점 기둥 걷기는 맨 나중 (위에 무엇도 남지 않게)
        return new CityBuildings(terrain, all);
    }

    /** 건물 없이 (테스트·설정으로 끌 때) */
    public static CityBuildings none(CityTerrain terrain) {
        return new CityBuildings(terrain, List.of());
    }

    public List<Placement> placements() {
        return placements;
    }

    private int[] cell(double x, double z) {
        int gx = Math.floorDiv((int) Math.floor(x), CELL) - gx0, gz = Math.floorDiv((int) Math.floor(z), CELL) - gz0;
        if (gx < 0 || gz < 0 || gx >= gw || gz >= gh) {
            return new int[0];
        }
        return cells[gz * gw + gx];
    }

    /**
     * 칸 (x, z) 의 건물 블록을 sink 에 넣습니다. 지형을 깐 다음에 부릅니다.
     * 도로·물·다리 위와 거점 표시 위는 길을 막지 않도록 높은 곳만 놓습니다.
     */
    public void column(int x, int z, Column c, Placement.Sink sink) {
        int[] here = cell(x + 0.5, z + 0.5);
        if (here.length == 0) {
            return;
        }
        int base = terrain.groundY() + 1;
        boolean road = c.isRoad() || c.isWater() || c.deck || c.tunnel;
        boolean pad = c.surface == Surface.PAD || c.hubPillar > 0;
        int minY = Integer.MIN_VALUE;
        if (road) {
            minY = base + ROAD_CLEARANCE;
        } else if (pad) {
            minY = base + HUB_CLEARANCE;
        }
        // 거리 시설: 인도 위에는 바닥부터(나무 구덩이), 차도 위로는 4칸 위부터(나뭇가지·가로등 팔)
        int streetMinY = pad ? base + HUB_CLEARANCE
                : c.surface == Surface.SIDEWALK && !c.deck ? base - 1
                : road ? base + STREET_CLEARANCE : Integer.MIN_VALUE;
        for (int k : here) {
            Placement p = placements.get(k);
            double[] b = p.boundsRef();
            if (x + 1 > b[0] && x < b[2] && z + 1 > b[1] && z < b[3]) {
                int floor = switch (p.kind) {
                    case "street" -> streetMinY;
                    case "hubcover" -> Integer.MIN_VALUE;
                    default -> minY;
                };
                p.column(x, z, base, floor, sink);
            }
        }
    }

    /** 청크 (cx, cz) 안의 글씨 있는 표지판들 (청크를 처음 불러올 때 글씨를 쓰는 데 씀) */
    public List<Placement.SignSpot> signs(int cx, int cz) {
        List<Placement.SignSpot> out = new ArrayList<>();
        int x0 = cx << 4, z0 = cz << 4;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int[] corner : new int[][]{{x0, z0}, {x0 + 15, z0}, {x0, z0 + 15}, {x0 + 15, z0 + 15}}) {
            for (int k : cell(corner[0] + 0.5, corner[1] + 0.5)) {
                if (!seen.add(k)) {
                    continue;
                }
                Placement p = placements.get(k);
                double[] b = p.boundsRef();
                if (b[2] < x0 || b[0] > x0 + 16 || b[3] < z0 || b[1] > z0 + 16) {
                    continue;
                }
                for (Placement.SignSpot s : p.signs(terrain.groundY() + 1)) {
                    if (s.x() >> 4 == cx && s.z() >> 4 == cz) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    /** 칸 (x, z) 를 덮는 건물 (없으면 null). 포장·거리 시설은 건물로 치지 않음. 미니맵용 */
    public Placement at(double x, double z) {
        for (int k : cell(x, z)) {
            Placement p = placements.get(k);
            if (!isGround(p) && p.covers(x, z)) {
                return p;
            }
        }
        return null;
    }

    /** 땅에 깔린 것 (포장, 거리 시설, 거점 표시 걷기) */
    public static boolean isGround(Placement p) {
        return p.kind.equals("pave") || p.kind.equals("street") || p.kind.equals("hubcover");
    }

    public CityTerrain terrain() {
        return terrain;
    }
}
