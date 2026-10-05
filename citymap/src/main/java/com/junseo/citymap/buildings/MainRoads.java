package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polyline;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;

/**
 * 큰길(대로)과 이면도로 구분. 설계도의 이름 있는 길(세종대로·테헤란로 같은 A·R·H 길)이 큰길이고,
 * 구역 격자로 낸 길("S-" 로 시작)이 이면도로입니다. 길 칸에서 가장 가까운 길 중심선이 어느 쪽인지로 정합니다.
 */
final class MainRoads {
    private final List<Polyline> main = new ArrayList<>(), side = new ArrayList<>();

    private MainRoads(CityTerrain t) {
        for (Layout.Road r : t.layout().roads()) {
            if (r.kind().equals("runway") || r.kind().equals("tunnel")) {
                continue;
            }
            (r.id().startsWith("S-") ? side : main).add(new Polyline(r.line()));
        }
    }

    private static MainRoads cached;
    private static CityTerrain cachedFor;

    static synchronized MainRoads of(CityTerrain t) {
        if (cachedFor != t) {
            cached = new MainRoads(t);
            cachedFor = t;
        }
        return cached;
    }

    /** 길 칸 (x, z) 가 큰길에 속하는지 */
    boolean isMain(double x, double z) {
        return distance(main, x, z) <= distance(side, x, z);
    }

    /** 가장 가까운 큰길 중심선까지 거리 */
    double toMain(double x, double z) {
        return distance(main, x, z);
    }

    private static double distance(List<Polyline> lines, double x, double z) {
        double best = Double.MAX_VALUE;
        double[] out = new double[2];
        for (Polyline p : lines) {
            p.nearest(x, z, out);
            best = Math.min(best, out[0]);
        }
        return best;
    }
}
