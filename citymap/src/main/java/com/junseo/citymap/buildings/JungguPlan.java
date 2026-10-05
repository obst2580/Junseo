package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 중구 랜드마크: 시청과 서울광장(cityhall 거점), 시청 차고지, 서울역(station 거점), 명동 거리와 보석상(jewelry 거점).
 * 거점이 있는 도로 블록을 찾아 거점이 광장·앞마당·보행 거리 위에 오도록 놓습니다.
 * 나머지 땅은 동네 채우기({@link DistrictFill})가 사무 빌딩·상가로 채웁니다.
 */
final class JungguPlan {

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "junggu");
        if (area == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        List<Placement> out = new ArrayList<>();
        Layout.Hub ch = Plans.hub(t, "cityhall");
        if (ch != null) {
            cityHall(t, area, m, ch, out);
        }
        Layout.Hub st = Plans.hub(t, "station");
        if (st != null) {
            SeoulStation.plan(t, area, m, st, out);
        }
        Layout.Hub jw = Plans.hub(t, "jewelry");
        if (jw != null) {
            Myeongdong.plan(t, area, m, jw, out);
        }
        return out;
    }

    // ------------------------------------------------------------------ 시청

    /**
     * 거점 블록 전체는 서울광장, 길 건너 북쪽 블록에 옛 청사(앞)와 새 청사(뒤), 서쪽 블록에 시청 차고지.
     */
    private static void cityHall(CityTerrain t, Polygon area, BuildMask m, Layout.Hub hub, List<Placement> out) {
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] sq = m.componentNear(hx, hz, 30);
        if (sq == null) {
            return;
        }
        Random rnd = Plans.random(t, "서울시청");
        int cx = (sq[0] + sq[2]) / 2, cz = (sq[1] + sq[3]) / 2;
        // 서울광장
        int px0 = sq[0] - 3, pz0 = sq[1] - 3, px1 = sq[2] + 3, pz1 = sq[3] + 3;
        out.add(Placement.rect("서울광장", "plaza", px0, pz0, px1, pz1, "south", (w, d) -> {
            Site s = new Site(t, area, px0, pz0, px1, pz1);
            return CityHall.plaza(s, cx - px0, cz - pz0, hx - px0, hz - pz0);
        }));
        // 북쪽 블록: 옛 청사 (광장을 봄)와 새 청사
        int[] nb = m.componentNear(cx, sq[1] - 20, 25);
        if (nb == null || nb[3] >= sq[1]) {
            return;
        }
        int ox0 = cx - CityHall.OLD_W / 2, ox1 = ox0 + CityHall.OLD_W - 1;
        int oz1 = nb[3], oz0 = oz1 - CityHall.OLD_D + 1;
        if (!m.rectFree(ox0, oz0, ox1, oz1)) {
            return;
        }
        long s1 = rnd.nextLong();
        out.add(Placement.rect("서울도서관 (옛 서울시청)", "cityhall", ox0, oz0, ox1, oz1, "south",
                (w, d) -> CityHall.oldHall(w, d, new Random(s1))));
        int nw = Math.min(CityHall.NEW_W, nb[2] - nb[0] + 1);
        int nx0 = cx - nw / 2, nx1 = nx0 + nw - 1;
        int nz1 = oz0 - 1, nz0 = nz1 - CityHall.NEW_D + 1;
        boolean newOk = nz0 >= nb[1] && m.rectFree(nx0, nz0, nx1, nz1);
        long s2 = rnd.nextLong();
        if (newOk) {
            out.add(Placement.rect("서울시청 (새 청사)", "cityhall", nx0, nz0, nx1, nz1, "south",
                    (w, d) -> CityHall.newHall(w, d, new Random(s2))));
        }
        // 두 청사 둘레 마당
        int yx0 = nb[0] - 3, yz0 = nb[1] - 3, yx1 = nb[2] + 3, yz1 = nb[3] + 3;
        int[][] keep = newOk
                ? new int[][]{{ox0 - yx0, oz0 - yz0, ox1 - yx0, oz1 - yz0}, {nx0 - yx0, nz0 - yz0, nx1 - yx0, nz1 - yz0}}
                : new int[][]{{ox0 - yx0, oz0 - yz0, ox1 - yx0, oz1 - yz0}};
        out.add(Placement.rect("시청 마당", "plaza", yx0, yz0, yx1, yz1, "south", (w, d) -> {
            Site s = new Site(t, area, yx0, yz0, yx1, yz1);
            return CityHall.yard(s, keep);
        }));
        // 서쪽 블록: 시청 차고지 (정문이 동쪽 길을 봄)
        int[] wb = m.componentNear(nb[0] - 30, (nb[1] + nb[3]) / 2, 25);
        if (wb != null && wb[2] < nb[0]) {
            int gx1 = wb[2], gx0 = gx1 - CityGarage.D + 1;
            int gz0 = (wb[1] + wb[3]) / 2 - CityGarage.W / 2, gz1 = gz0 + CityGarage.W - 1;
            for (int shift = 0; shift <= 20 && !m.rectFree(gx0, gz0, gx1, gz1); shift++) {
                gz0 += (shift % 2 == 0 ? shift : -shift);
                gz1 = gz0 + CityGarage.W - 1;
            }
            if (m.rectFree(gx0, gz0, gx1, gz1)) {
                long s3 = rnd.nextLong();
                out.add(Placement.rect("시청 차고지", "garage", gx0, gz0, gx1, gz1, "east",
                        (w, d) -> CityGarage.build(w, d, new Random(s3))));
            }
        }
    }

    private JungguPlan() {
    }
}
