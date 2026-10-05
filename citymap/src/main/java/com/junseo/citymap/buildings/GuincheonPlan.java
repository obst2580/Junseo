package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 구인천: 낮고 싼 건물(다세대·상가주택)과 공장들.
 * 블록마다 공장 블록인지 주택 블록인지 정하고, 필지로 나눠 채웁니다.
 * 암시장 후보 거점(blackmarket) 옆에는 폐공장을 둡니다.
 */
final class GuincheonPlan {

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "guincheon");
        if (area == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        Random rnd = Plans.random(t, "guincheon");
        List<Placement> out = new ArrayList<>();

        Layout.Hub bm = Plans.hub(t, "blackmarket");
        if (bm != null) {
            LotPlanner.Lot lot = Plans.besideHub(m, bm.x(), bm.z(), 40, 46, 8);
            if (lot != null) {
                long seed = rnd.nextLong();
                out.add(Plans.lot("폐공장 (암시장)", "factory", lot, (w, d) -> Factory.build(w, d, new Random(seed), true)));
                m.claim(lot.x0() - 2, lot.z0() - 2, lot.x1() + 2, lot.z1() + 2);
            }
        }

        // 1차: 블록마다 공장 블록·주택 블록을 정해서 나눔
        for (int[] block : m.blocks()) {
            if (block[4] < 150) {
                continue;
            }
            int bw = block[2] - block[0] + 1, bd = block[3] - block[1] + 1;
            boolean factories = bw >= 50 && bd >= 50 && rnd.nextInt(10) < 5;
            LotPlanner planner = factories
                    ? new LotPlanner(m, rnd, 26, 48, 3, 5)
                    : new LotPlanner(m, rnd, 10, 19, 2, 3);
            for (LotPlanner.Lot lot : planner.split(block)) {
                out.add(build(lot, factories, rnd));
            }
            claimAll(m, out);
        }
        // 2차: 남은 땅을 작은 건물로 메움
        for (int[] block : m.blocks()) {
            if (block[4] < 100) {
                continue;
            }
            for (LotPlanner.Lot lot : new LotPlanner(m, rnd, 10, 17, 2, 3).split(block)) {
                out.add(build(lot, false, rnd));
            }
            claimAll(m, out);
        }
        return out;
    }

    private static Placement build(LotPlanner.Lot lot, boolean factories, Random rnd) {
        long seed = rnd.nextLong();
        int roll = rnd.nextInt(100);
        if (factories && lot.width() >= 22 && lot.depth() >= 22) {
            return Plans.lot("공장", "factory", lot, (w, d) -> Factory.build(w, d, new Random(seed), false));
        }
        if (roll < 8 && lot.width() >= 12 && lot.depth() >= 12) {
            return Plans.lot("주차장", "parking", lot, (w, d) -> ParkingLot.build(w, d, new Random(seed)));
        }
        ShopHouse.Style style = roll < 65 ? ShopHouse.Style.VILLA : ShopHouse.Style.MIXED;
        return Plans.lot(style == ShopHouse.Style.VILLA ? "다세대 주택" : "상가주택", "house", lot,
                (w, d) -> ShopHouse.build(w, d, new Random(seed), style));
    }

    /** 놓은 건물 자리를 지도에서 지움 (둘레 1칸 골목 포함) */
    static void claimAll(BuildMask m, List<Placement> placed) {
        for (Placement p : placed) {
            double[] b = p.boundsRef();
            m.claim((int) Math.floor(b[0]) - 1, (int) Math.floor(b[1]) - 1, (int) Math.ceil(b[2]), (int) Math.ceil(b[3]));
        }
    }

    private GuincheonPlan() {
    }
}
