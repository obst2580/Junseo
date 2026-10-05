package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 송파: 테마파크 건설 예정 부지(themepark), 초고층 타워(skyscraper), 잠실 야구장(ballpark).
 * 거점마다 그 거점이 있는 블록(도로로 둘러싸인 땅) 하나를 통째로 쓰고, 거점은 정문 앞 광장·진입 마당에 둡니다.
 * 블록 크기는 땅에서 재므로, 설계도에서 길을 없애 블록이 커지면 건물도 실제 크기 쪽으로 커집니다.
 */
final class SongpaPlan {
    static final String DISTRICT = "songpa";
    /** 블록을 이보다 크게는 찾지 않음 (길을 없애 블록이 합쳐져도) */
    static final int MAX_SIDE = 200;

    static List<Placement> plan(CityTerrain t) {
        List<Placement> out = new ArrayList<>();
        if (Plans.district(t, DISTRICT) == null) {
            return out;
        }
        Random rnd = Plans.random(t, DISTRICT + "-landmarks");
        Layout.Hub park = Plans.hub(t, "themepark");
        if (park != null) {
            long seed = rnd.nextLong();
            site(t, park, MAX_SIDE, (land, hub) -> ThemeParkSite.build(land, hub[0], hub[1], new Random(seed)),
                    "테마파크 건설 예정 부지", "site", out);
        }
        Layout.Hub ball = Plans.hub(t, "ballpark");
        if (ball != null) {
            long seed = rnd.nextLong();
            site(t, DISTRICT, ball, MAX_SIDE, (land, hub) -> Ballpark.build(land, hub[0], hub[1], new Random(seed)),
                    "잠실 야구장", "ballpark", (land, hub) -> Ballpark.footprint(land, hub[0], hub[1]), out);
        }
        return out;
    }

    interface SiteBuilder {
        Voxels build(SiteLand land, int[] hub);
    }

    /** 건물이 땅을 차지하는 모양 (건물 좌표 다각형) */
    interface SiteFootprint {
        List<double[]> of(SiteLand land, int[] hub);
    }

    /**
     * 거점이 있는 블록 하나에 건물 하나. 정면은 거점에 가까운 변, 건물 상자는 블록 전체.
     * maxSide 보다 큰 블록은 거점 둘레로 잘라 씁니다.
     */
    static Placement site(CityTerrain t, Layout.Hub hub, int maxSide, SiteBuilder builder, String name, String kind, List<Placement> out) {
        return site(t, DISTRICT, hub, maxSide, builder, name, kind, null, out);
    }

    static Placement site(CityTerrain t, String district, Layout.Hub hub, int maxSide, SiteBuilder builder, String name, String kind,
                          SiteFootprint footprint, List<Placement> out) {
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] box = SiteLand.blockAround(t, district, hx, hz, maxSide);
        String front = SiteLand.sideNearest(box, hx, hz);
        int x0 = box[0], z0 = box[1], x1 = box[2], z1 = box[3];
        java.util.function.BiFunction<Integer, Integer, List<double[]>> fp = footprint == null ? null : (bw, bd) -> {
            SiteLand land = SiteLand.of(t, district, x0, z0, x1, z1, front, bw, bd);
            return footprint.of(land, land.local(hx, hz));
        };
        Placement p = Placement.rect(name, kind, x0, z0, x1, z1, front, fp, (bw, bd) -> {
            SiteLand land = SiteLand.of(t, district, x0, z0, x1, z1, front, bw, bd);
            int[] local = land.local(hx, hz);
            return builder.build(land, local);
        });
        out.add(p);
        return p;
    }

    /** 거점의 건물 상자 좌표 {i, j} (검사용: site 와 같은 계산) */
    static int[] hubLocal(CityTerrain t, String district, String hubId, int maxSide) {
        Layout.Hub hub = Plans.hub(t, hubId);
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] box = SiteLand.blockAround(t, district, hx, hz, maxSide);
        String front = SiteLand.sideNearest(box, hx, hz);
        boolean ns = front.equals("north") || front.equals("south");
        int sx = box[2] - box[0] + 1, sz = box[3] - box[1] + 1;
        SiteLand land = SiteLand.of(t, district, box[0], box[1], box[2], box[3], front, ns ? sx : sz, ns ? sz : sx);
        return land.local(hx, hz);
    }

    private SongpaPlan() {
    }
}
