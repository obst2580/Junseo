package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 마포: 축구장 거점(football)이 있는 블록에 서울월드컵경기장. 거점은 정면 광장(매표소 사이)에 둡니다.
 * 블록이 실제 경기장(약 170×130m)보다 작으면 피치와 관중석을 블록에 맞춰 줄입니다.
 */
final class MapoPlan {
    static final String DISTRICT = "mapo";
    /** 블록을 이보다 크게는 찾지 않음 */
    static final int MAX_SIDE = 200;

    static List<Placement> plan(CityTerrain t) {
        List<Placement> out = new ArrayList<>();
        Layout.Hub hub = Plans.hub(t, "football");
        if (Plans.district(t, DISTRICT) == null || hub == null) {
            return out;
        }
        long seed = Plans.random(t, DISTRICT + "-stadium").nextLong();
        SongpaPlan.site(t, DISTRICT, hub, MAX_SIDE,
                (land, h) -> FootballStadium.build(land, h[0], h[1], new Random(seed)),
                "서울월드컵경기장", "stadium", (land, h) -> FootballStadium.footprint(land, h[0], h[1]), out);
        return out;
    }

    private MapoPlan() {
    }
}
