package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 남산: 정상(남산타워 거점)에 N서울타워, 타워 밑 N서울타워 플라자, 정상 광장(팔각정·봉수대).
 * 산 위라서 BuildMask 대신 지형 높이를 직접 읽어 땅을 고릅니다 ({@link SeoulTower}).
 * 나머지 남산 평지는 동네 채우기가 채웁니다.
 */
final class NamsanPlan {

    static List<Placement> plan(CityTerrain t) {
        Layout.Hub hub = Plans.hub(t, "tower");
        if (hub == null) {
            return List.of();
        }
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int x0 = hx - SeoulTower.HUB_I, z0 = hz - SeoulTower.HUB_J;
        int x1 = x0 + SeoulTower.W - 1, z1 = z0 + SeoulTower.D - 1;
        Ground ground = Ground.rect(t, x0, z0, x1, z1, "south");
        long seed = Plans.random(t, "namsan-tower").nextLong();
        List<Placement> out = new ArrayList<>();
        out.add(Placement.rect("남산타워", "landmark", x0, z0, x1, z1, "south", NamsanPlan::footprint,
                (w, d) -> SeoulTower.build(w, d, ground, new Random(seed))));
        return out;
    }

    /** 미니맵에 건물로 그릴 곳: 타워 플라자 건물 바닥판 (광장은 땅으로) */
    static List<double[]> footprint(int w, int d) {
        List<double[]> pts = new ArrayList<>();
        double cx = SeoulTower.BI0 + SeoulTower.BW / 2.0, cz = SeoulTower.BJ0 + SeoulTower.BD / 2.0;
        double rx = SeoulTower.BW / 2.0, rz = SeoulTower.BD / 2.0;
        for (int k = 0; k < 32; k++) {
            double a = 2 * Math.PI * k / 32;
            double c = Math.cos(a), s = Math.sin(a);
            // 모서리가 둥근 직사각형에 가깝게 (초타원)
            double px = Math.signum(c) * Math.pow(Math.abs(c), 0.4), pz = Math.signum(s) * Math.pow(Math.abs(s), 0.4);
            pts.add(new double[]{cx + rx * px, cz + rz * pz});
        }
        return pts;
    }

    private NamsanPlan() {
    }
}
