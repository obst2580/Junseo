package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 청라: 쇼핑몰·돔구장 거점(mall)이 있는 블록에 청라돔을 짓고, 거점 쪽에 광장을 둡니다.
 * 스타필드(쇼핑몰)는 보류라서 다른 블록은 비워 둡니다.
 */
final class CheongnaPlan {

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "cheongna");
        Layout.Hub mall = Plans.hub(t, "mall");
        if (area == null || mall == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        Random rnd = Plans.random(t, "cheongna");
        int hx = (int) Math.floor(mall.x()), hz = (int) Math.floor(mall.z());
        int[] blk = m.componentNear(hx, hz, 30);
        if (blk == null) {
            return List.of();
        }
        List<Placement> out = new ArrayList<>();
        int x0 = blk[0], x1 = blk[2];
        // 거점이 블록 북쪽이면 돔은 거점 남쪽, 정문은 북쪽(거점)
        boolean hubNorth = hz < (blk[1] + blk[3]) / 2;
        int z0 = hubNorth ? hz + 9 : blk[1];
        int z1 = hubNorth ? blk[3] : hz - 9;
        if (z1 - z0 < 60 || x1 - x0 < 50) {
            return List.of();
        }
        long seed = rnd.nextLong();
        out.add(Placement.rect("청라돔", "dome", x0, z0, x1, z1, hubNorth ? "north" : "south", Dome::footprint,
                (w, d) -> Dome.build(w, d, new Random(seed))));
        // 정문 앞 광장 (도로 쪽 여유 2칸까지)
        int pz0 = hubNorth ? blk[1] - 2 : z1 + 1, pz1 = hubNorth ? z0 - 1 : blk[3] + 2;
        String front = hubNorth ? "north" : "south";
        Ground ground = Ground.rect(t, x0 - 2, pz0, x1 + 2, pz1, front);
        out.add(Placement.rect("청라돔 광장", "plaza", x0 - 2, pz0, x1 + 2, pz1, front, (w, d) -> plaza(w, d, ground)));
        return out;
    }

    /** 돔 앞 광장: 포장, 가운데 길, 나무와 가로등 */
    static Voxels plaza(int w, int d, Ground ground) {
        Voxels v = new Voxels(w, d, -1, 7);
        int mid = w / 2;
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                boolean path = Math.abs(i + 0.5 - mid) < 5;
                v.set(i, -1, j, path ? POLISHED_ANDESITE : j % 8 == 0 ? SMOOTH_STONE : LIGHT_GRAY_CONCRETE);
            }
        }
        for (int i = 4; i < w - 4; i += 9) {
            if (Math.abs(i - mid) < 8) {
                continue;
            }
            // 돔 쪽 줄 (도로에서 먼 쪽)
            int j = 3;
            if (ground.clear(i, j, 3)) {
                MarketPlan.tree(v, i, j);
            }
            if (ground.clear(i + 4, j, 1)) {
                MarketPlan.lampPost(v, i + 4, j);
            }
        }
        v.connect();
        return v;
    }

    private CheongnaPlan() {
    }
}
