package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 여의도 랜드마크: 메인 광장(plaza 거점), 63빌딩 = 은행 본점·금융 타워(bank_hq 거점), 방송국(broadcast 거점).
 * 거점마다 그 거점이 있는 도로 블록을 찾아서, 거점이 앞마당·광장 위에 오도록 건물을 놓습니다.
 * 나머지 땅은 동네 채우기({@link DistrictFill})가 사무 빌딩·아파트로 채웁니다.
 */
final class YeouidoPlan {

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "yeouido");
        if (area == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        List<Placement> out = new ArrayList<>();
        Layout.Hub bank = Plans.hub(t, "bank_hq");
        if (bank != null) {
            sixtyThree(t, area, m, bank, out);
        }
        Layout.Hub plaza = Plans.hub(t, "plaza");
        if (plaza != null) {
            plaza(t, area, m, plaza, out);
        }
        Layout.Hub bc = Plans.hub(t, "broadcast");
        if (bc != null) {
            broadcast(t, area, m, bc, out);
        }
        return out;
    }

    // ------------------------------------------------------------------ 63빌딩

    /** 63빌딩은 정면(넓은 면)이 동쪽 거점을 보고, 그 앞에 차를 대는 길과 앞마당, 뒤에 주차장 */
    private static void sixtyThree(CityTerrain t, Polygon area, BuildMask m, Layout.Hub hub, List<Placement> out) {
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] blk = m.componentNear(hx, hz, 30);
        if (blk == null) {
            return;
        }
        int depth = SixtyThree.D, width = SixtyThree.W;
        int tx0 = 0, tz0 = 0;
        boolean found = false;
        search:
        for (int gap = 25; gap <= 34; gap++) {
            for (int dz = 0; dz <= 12; dz++) {
                for (int sign : new int[]{1, -1}) {
                    int x1 = hx - gap, x0 = x1 - depth + 1, z0 = hz - width / 2 + sign * dz;
                    if (m.rectFree(x0, z0, x1, z0 + width - 1)) {
                        tx0 = x0;
                        tz0 = z0;
                        found = true;
                        break search;
                    }
                }
            }
        }
        if (!found) {
            return;
        }
        int tx1 = tx0 + depth - 1, tz1 = tz0 + width - 1;
        long seed = Plans.random(t, "63빌딩").nextLong();
        out.add(Placement.rect("63빌딩 (준서은행 본점)", "bank", tx0, tz0, tx1, tz1, "east",
                (w, d) -> SixtyThree.build(w, d, new Random(seed))));
        // 앞마당·주차장: 블록 전체 (도로 쪽 여유 3칸까지, 도로는 덮지 않음)
        int gx0 = blk[0] - 3, gz0 = blk[1] - 3, gx1 = blk[2] + 3, gz1 = blk[3] + 3;
        int ftx0 = tx0, ftz0 = tz0;
        out.add(Placement.rect("63빌딩 앞마당", "plaza", gx0, gz0, gx1, gz1, "south", (w, d) -> {
            Site s = new Site(t, area, gx0, gz0, gx1, gz1);
            return forecourt(s, ftx0 - gx0, ftz0 - gz0, depth, width, hx - gx0, hz - gz0);
        }));
    }

    /**
     * 63빌딩 앞마당 (상자 좌표 = 월드 - 원점). 타워 자리 (ti0.., tj0.., 깊이 td, 너비 tw) 는 비워 둡니다.
     * 정면 앞 차로(드롭오프)와 도로로 나가는 두 갈래, 그 사이 보행 앞마당(거점), 차양, 표석, 뒤쪽 주차장.
     */
    static Voxels forecourt(Site s, int ti0, int tj0, int td, int tw, int hi, int hj) {
        Voxels v = new Voxels(s.w, s.d, -1, 8);
        int ti1 = ti0 + td - 1, tj1 = tj0 + tw - 1;
        int lane0 = ti1 + 1, lane1 = ti1 + 7;          // 정면 앞 차로
        int armN0 = tj0 + 3, armN1 = tj0 + 9, armS0 = tj1 - 9, armS1 = tj1 - 3;
        Block asphalt = Block.of("gray_concrete", 0x36393D);
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (!s.land(i, j) || (i >= ti0 && i <= ti1 && j >= tj0 && j <= tj1)) {
                    continue;
                }
                boolean lane = i >= lane0 && i <= lane1 && j >= armN0 && j <= armS1;
                boolean arm = i > lane1 && ((j >= armN0 && j <= armN1) || (j >= armS0 && j <= armS1));
                Block b;
                if (lane || arm) {
                    boolean edge = (lane && (i == lane0 || i == lane1) && !(j >= armN0 && j <= armN1) && !(j >= armS0 && j <= armS1))
                            || (arm && (j == armN0 || j == armN1 || j == armS0 || j == armS1));
                    b = edge ? WHITE_CONCRETE : asphalt;
                } else if (i < ti0) {
                    b = asphalt; // 뒤쪽 주차장
                } else {
                    b = Math.floorMod(i + j, 6) == 0 ? Block.of("light_gray_concrete", 0x7D7D73) : POLISHED_ANDESITE;
                }
                v.set(i, -1, j, b);
            }
        }
        // 횡단보도 (앞마당 → 정문)
        for (int j = hj - 3; j <= hj + 3; j++) {
            for (int i = lane0; i <= lane1; i++) {
                if (s.land(i, j) && (j - hj) % 2 == 0) {
                    v.set(i, -1, j, WHITE_CONCRETE);
                }
            }
        }
        // 정문 차양: 차로 위, 금빛 테두리, 아래 조명, 앞면에 건물 이름
        int cj0 = tj0 + tw / 2 - 7, cj1 = tj0 + tw / 2 + 7;
        for (int j = cj0; j <= cj1; j++) {
            for (int i = lane0; i <= lane1 + 1; i++) {
                boolean rim = j == cj0 || j == cj1 || i == lane1 + 1;
                v.set(i, 5, j, rim ? SixtyThree.GOLD_MULLION : Block.of("smooth_quartz", 0xECE6DF));
                if (!rim && (i - lane0) % 3 == 1 && (j - cj0) % 4 == 2) {
                    v.set(i, 5, j, SEA_LANTERN);
                }
            }
        }
        for (int j : new int[]{cj0, cj1}) {
            v.fill(lane1 + 1, 0, j, lane1 + 1, 4, j, POLISHED_GRANITE);
        }
        v.set(lane1 + 2, 5, (cj0 + cj1) / 2, Blocks.wallSign("dark_oak", "east", "white", true, "", "63빌딩", "준서은행 본점"));
        // 표석 (북쪽 갈래 옆, 도로 쪽)
        int mi = s.w - 9, mj = armN1 + 3;
        if (s.solid(mi, mj, 1) && s.solid(mi, mj + 3, 1)) {
            v.fill(mi, 0, mj, mi, 1, mj + 3, POLISHED_GRANITE);
            v.set(mi + 1, 1, mj + 1, Blocks.wallSign("dark_oak", "east", "yellow", false, "63빌딩", "준서은행 본점", "금융 타워"));
        }
        // 앞마당: 화단 나무, 의자, 보행등, 작은 분수 (거점 자리는 비움)
        for (int j = armN1 + 4; j <= armS0 - 4; j += 7) {
            for (int i = lane1 + 5; i < s.w - 4; i += 8) {
                if (s.solid(i, j, 2) && Math.abs(j - hj) > 6) {
                    Site.planter(v, i, j);
                }
            }
        }
        for (int j = armN1 + 3; j <= armS0 - 3; j += 6) {
            if (s.solid(lane1 + 2, j)) {
                Site.lamp(v, lane1 + 2, j);
            }
        }
        // 타워 옆·뒤 가로수
        for (int i = ti0 - 2; i <= ti1 + 6; i += 6) {
            for (int j : new int[]{tj0 - 4, tj1 + 4}) {
                if (s.solid(i, j, 2) && !(i >= lane0 && i <= lane1 + 1)) {
                    Site.tree(v, i, j, false);
                }
            }
        }
        // 뒤쪽 주차장: 흰 주차선 (차는 없음)
        for (int j = tj0 + 2; j + 5 < tj1 - 2; j += 13) {
            for (int i = 2; i + 3 < ti0 - 2; i += 3) {
                if (s.land(i, j) && s.land(i, j + 4)) {
                    v.fill(i, -1, j, i, -1, j + 4, WHITE_CONCRETE);
                }
                if (s.land(i, j + 8) && s.land(i, j + 12)) {
                    v.fill(i, -1, j + 8, i, -1, j + 12, WHITE_CONCRETE);
                }
            }
        }
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 메인 광장

    private static void plaza(CityTerrain t, Polygon area, BuildMask m, Layout.Hub hub, List<Placement> out) {
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int[] blk = m.componentNear(hx, hz, 30);
        if (blk == null) {
            return;
        }
        int x0 = blk[0] - 3, z0 = blk[1] - 3, x1 = blk[2] + 3, z1 = blk[3] + 3;
        long seed = Plans.random(t, "여의도 광장").nextLong();
        out.add(Placement.rect("여의도 메인 광장", "plaza", x0, z0, x1, z1, "south", (w, d) -> {
            Site s = new Site(t, area, x0, z0, x1, z1);
            return YeouidoPlaza.build(s, hx - x0, hz - z0, new Random(seed));
        }));
    }

    // ------------------------------------------------------------------ 방송국

    private static void broadcast(CityTerrain t, Polygon area, BuildMask m, Layout.Hub hub, List<Placement> out) {
        Broadcast.plan(t, area, m, hub, out);
    }

    private YeouidoPlan() {
    }
}
