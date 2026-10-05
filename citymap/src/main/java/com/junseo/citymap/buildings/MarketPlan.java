package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 대형시장 구역: 물류센터·수산시장 거점(depot)이 있는 블록에 시장 건물(광장시장처럼 건물 안에 시장)을 짓고,
 * 거점 쪽에 앞 광장을 둡니다. 나머지 블록은 간판이 많은 한국 상가 건물로 채웁니다.
 */
final class MarketPlan {

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "market");
        if (area == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        Random rnd = Plans.random(t, "market");
        List<Placement> out = new ArrayList<>();

        Layout.Hub depot = Plans.hub(t, "depot");
        if (depot != null) {
            hall(t, m, depot, rnd, out);
        }
        GuincheonPlan.claimAll(m, out);

        for (int pass = 0; pass < 2; pass++) {
            for (int[] block : m.blocks()) {
                if (block[4] < 100) {
                    continue;
                }
                for (LotPlanner.Lot lot : new LotPlanner(m, rnd, 10, 18, 2, 3).split(block)) {
                    long seed = rnd.nextLong();
                    if (rnd.nextInt(100) < 7 && lot.width() >= 12 && lot.depth() >= 12) {
                        out.add(Plans.lot("시장 주차장", "parking", lot, (w, d) -> ParkingLot.build(w, d, new Random(seed))));
                    } else {
                        out.add(Plans.lot("상가 건물", "shop", lot, (w, d) -> ShopHouse.build(w, d, new Random(seed), ShopHouse.Style.COMMERCIAL)));
                    }
                }
                GuincheonPlan.claimAll(m, out);
            }
        }
        return out;
    }

    /** 거점이 있는 블록에 본관과 앞 광장 */
    private static void hall(CityTerrain t, BuildMask m, Layout.Hub depot, Random rnd, List<Placement> out) {
        int hx = (int) Math.floor(depot.x()), hz = (int) Math.floor(depot.z());
        int[] blk = m.componentNear(hx, hz, 30);
        if (blk == null) {
            return;
        }
        int x0 = blk[0], x1 = blk[2];
        boolean southFront = hz > (blk[1] + blk[3]) / 2;
        int zTop, zBottom;
        if (southFront) {
            zTop = blk[1];
            while (zTop < blk[3] && !m.rectFree(x0, zTop, x1, zTop)) {
                zTop++;
            }
            zBottom = hz - 10;
        } else {
            zBottom = blk[3];
            while (zBottom > blk[1] && !m.rectFree(x0, zBottom, x1, zBottom)) {
                zBottom--;
            }
            zTop = hz + 10;
        }
        if (zBottom - zTop < 40 || x1 - x0 < 60) {
            return;
        }
        long seed = rnd.nextLong();
        out.add(Placement.rect("대형시장 본관", "market", x0, zTop, x1, zBottom, southFront ? "south" : "north",
                (w, d) -> MarketHall.build(w, d, new Random(seed))));
        // 앞 광장: 본관과 도로 사이 (도로 쪽 여유 2칸까지 깔아도 도로는 덮지 않음)
        int pz0 = southFront ? zBottom + 1 : blk[1] - 2, pz1 = southFront ? blk[3] + 2 : zTop - 1;
        long pseed = rnd.nextLong();
        String front = southFront ? "south" : "north";
        Ground ground = Ground.rect(t, x0 - 2, pz0, x1 + 2, pz1, front);
        out.add(Placement.rect("시장 앞 광장", "plaza", x0 - 2, pz0, x1 + 2, pz1, front,
                (w, d) -> plaza(w, d, new Random(pseed), ground)));
    }

    /** 시장 앞 광장: 포장, 정문 앞 길, 가장자리 나무·가로등·의자 (노점은 두지 않음, 시장은 건물 안) */
    static Voxels plaza(int w, int d, Random r, Ground ground) {
        Voxels v = new Voxels(w, d, -1, 6);
        int mid = w / 2;
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                boolean path = Math.abs(i + 0.5 - mid) < 6;
                Block floor = path ? (j % 3 == 0 ? POLISHED_GRANITE : SMOOTH_STONE)
                        : ((i / 2 + j / 2) % 2 == 0 ? POLISHED_ANDESITE : SMOOTH_STONE);
                v.set(i, -1, j, floor);
            }
        }
        for (int i = 4; i < w - 4; i += 10) {
            if (Math.abs(i - mid) < 9) {
                continue;
            }
            int j = d / 2;
            if (ground.clear(i, j, 3)) {
                tree(v, i, j);
                v.set(i - 2, 0, j, SPRUCE_SLAB);
                v.set(i + 2, 0, j, SPRUCE_SLAB);
            }
            if (ground.clear(i + 5, j, 1)) {
                v.fill(i + 5, 0, j, i + 5, 3, j, SPRUCE_FENCE);
                v.set(i + 5, 4, j, LANTERN);
            }
        }
        v.connect();
        return v;
    }

    static void tree(Voxels v, int i, int j) {
        v.set(i, -1, j, GRASS);
        v.fill(i, 0, j, i, 3, j, OAK_LOG);
        v.ellipsoid(i + 0.5, 5, j + 0.5, 2.4, 1.8, 2.4, OAK_LEAVES);
        v.set(i, 4, j, OAK_LOG);
    }

    private MarketPlan() {
    }
}
