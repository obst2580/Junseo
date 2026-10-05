package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 강남: 경찰서(강남) 거점(police_south)에 강남경찰서 (유리 커튼월과 흰 돌 틀, 2층 높이 유리 로비).
 * 거점 앞 도로 쪽이 정면이고 거점은 앞마당에 있습니다. 나머지는 동네 채우기가 사무 빌딩으로 채웁니다.
 */
final class GangnamPlan {

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "gangnam");
        Layout.Hub police = Plans.hub(t, "police_south");
        if (area == null || police == null) {
            return List.of();
        }
        List<Placement> out = new ArrayList<>();
        Placement p = PoliceStation.place(t, area, police.x(), police.z(), 95, "강남경찰서", PoliceStation.Style.MODERN,
                Plans.random(t, "gangnam-police"));
        if (p != null) {
            out.add(p);
        }
        return out;
    }

    private GangnamPlan() {
    }
}
