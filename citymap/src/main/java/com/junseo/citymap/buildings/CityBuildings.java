package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;

import java.util.ArrayList;
import java.util.List;

/**
 * 도시의 건물 목록과, 생성기가 칸마다 건물 블록을 얻어 가는 곳.
 * 설계도(layout.json)와 구역별 계획(공항·청라·구인천·대형시장)으로 정해지므로 같은 설계도면 늘 같은 도시가 나옵니다.
 * 한 번 만들면 바뀌지 않아서 여러 스레드에서 같이 써도 됩니다 (건물 블록 상자는 처음 쓸 때 만듦).
 */
public final class CityBuildings {
    /** 도로·물 위로는 이 높이(땅 위 칸 수)부터만 놓음: 길 위를 가로지르는 문·지붕은 되고 길을 막지는 않게 */
    static final int ROAD_CLEARANCE = 6;
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
        all.addAll(AirportPlan.plan(terrain));
        all.addAll(CheongnaPlan.plan(terrain));
        all.addAll(GuincheonPlan.plan(terrain));
        all.addAll(MarketPlan.plan(terrain));
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
        int minY = Integer.MIN_VALUE;
        if (c.isRoad() || c.isWater() || c.deck || c.tunnel) {
            minY = base + ROAD_CLEARANCE;
        } else if (c.surface == Surface.PAD || c.hubPillar > 0) {
            minY = base + HUB_CLEARANCE;
        }
        for (int k : here) {
            Placement p = placements.get(k);
            double[] b = p.boundsRef();
            if (x + 1 > b[0] && x < b[2] && z + 1 > b[1] && z < b[3]) {
                p.column(x, z, base, minY, sink);
            }
        }
    }

    /** 칸 (x, z) 를 덮는 건물 (없으면 null). 미니맵용 */
    public Placement at(double x, double z) {
        for (int k : cell(x, z)) {
            Placement p = placements.get(k);
            if (p.covers(x, z)) {
                return p;
            }
        }
        return null;
    }

    public CityTerrain terrain() {
        return terrain;
    }
}
