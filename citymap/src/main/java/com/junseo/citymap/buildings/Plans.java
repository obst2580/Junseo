package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.List;
import java.util.Random;

/** 구역별 건물 계획에서 같이 쓰는 도구 */
final class Plans {

    static Polygon district(CityTerrain t, String id) {
        for (Layout.District d : t.layout().districts()) {
            if (d.id().equals(id)) {
                return d.ellipse() != null
                        ? Polygon.ellipse(d.ellipse().cx(), d.ellipse().cz(), d.ellipse().rx(), d.ellipse().rz(), d.ellipse().angle(), 64)
                        : new Polygon(d.polygon());
            }
        }
        return null;
    }

    static Layout.Hub hub(CityTerrain t, String id) {
        for (Layout.Hub h : t.layout().hubs()) {
            if (h.id().equals(id)) {
                return h;
            }
        }
        return null;
    }

    static Layout.Road road(CityTerrain t, String id) {
        for (Layout.Road r : t.layout().roads()) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** 이름과 설계도 버전으로 정하는 난수 (같은 설계도면 같은 건물) */
    static Random random(CityTerrain t, String name) {
        return new Random(name.hashCode() * 31L + t.layout().version() * 1_000_003L);
    }

    /** 필지 하나에 맞는 건물을 놓습니다 */
    static Placement lot(String name, String kind, LotPlanner.Lot lot, java.util.function.BiFunction<Integer, Integer, Voxels> build) {
        return Placement.rect(name, kind, lot.x0(), lot.z0(), lot.x1(), lot.z1(), lot.front(), build);
    }

    /**
     * 거점 (hx, hz) 바로 옆에 sizeW×sizeD (정면 너비×깊이) 땅을 찾습니다. 정면이 거점을 봅니다.
     * 못 찾으면 null.
     */
    static LotPlanner.Lot besideHub(BuildMask m, double hx, double hz, int sizeW, int sizeD, int gap) {
        int x = (int) Math.floor(hx), z = (int) Math.floor(hz);
        // 정면이 거점을 보도록: 거점의 서·동·북·남쪽에 놓아 봄
        for (String side : List.of("west", "east", "north", "south")) {
            for (int shift = 0; shift <= 20; shift += 4) {
                for (int sign : new int[]{1, -1}) {
                    int s = shift * sign;
                    int x0, z0, x1, z1;
                    String front;
                    switch (side) {
                        case "west" -> { // 거점 서쪽 건물, 정면은 동쪽
                            x1 = x - gap;
                            x0 = x1 - sizeD + 1;
                            z0 = z - sizeW / 2 + s;
                            z1 = z0 + sizeW - 1;
                            front = "east";
                        }
                        case "east" -> {
                            x0 = x + gap;
                            x1 = x0 + sizeD - 1;
                            z0 = z - sizeW / 2 + s;
                            z1 = z0 + sizeW - 1;
                            front = "west";
                        }
                        case "north" -> {
                            z1 = z - gap;
                            z0 = z1 - sizeD + 1;
                            x0 = x - sizeW / 2 + s;
                            x1 = x0 + sizeW - 1;
                            front = "south";
                        }
                        default -> {
                            z0 = z + gap;
                            z1 = z0 + sizeD - 1;
                            x0 = x - sizeW / 2 + s;
                            x1 = x0 + sizeW - 1;
                            front = "north";
                        }
                    }
                    if (m.rectFree(x0, z0, x1, z1)) {
                        return new LotPlanner.Lot(x0, z0, x1, z1, front);
                    }
                }
            }
        }
        return null;
    }

    private Plans() {
    }
}
