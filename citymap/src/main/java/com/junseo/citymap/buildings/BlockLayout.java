package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 도로로 둘러싸인 블록 하나를 한국 도심 블록처럼 나눕니다.
 * <ul>
 *   <li>길에 닿는 네 변: 건물끼리 붙여 지은(맞벽) 가로변 건물 줄. 큰길(대로)에 닿는 변은 더 깊은 터(타워 터)로.</li>
 *   <li>그 안쪽 둘레: 폭 3칸 골목. 가로변 줄 가운데에 골목 입구(폭 3)를 뚫어 길과 잇습니다.</li>
 *   <li>골목 안: 골목을 보고 선 작은 필지 줄 (안이 깊으면 가운데 골목을 하나 더).</li>
 * </ul>
 * 블록 땅이 고르지 않으면(대각선 길, 랜드마크) 필지를 지을 수 있는 땅까지 깎고, 너무 작아진 필지는 버립니다
 * (남은 땅은 동네 채우기가 작은 필지·쌈지공원으로 메움).
 */
final class BlockLayout {
    /** 필지가 닿는 길의 위계: 대로변, 이면도로변, 골목 안 */
    enum Tier { MAIN, STREET, INNER }

    /** 필지 [x0..x1]×[z0..z1], 정면 방향, 위계, 맞벽 여부, 타워 터 여부 */
    record Lot(int x0, int z0, int x1, int z1, String front, Tier tier, boolean partyWall, boolean tower) {
        int width() {
            return front.equals("south") || front.equals("north") ? x1 - x0 + 1 : z1 - z0 + 1;
        }

        int depth() {
            return front.equals("south") || front.equals("north") ? z1 - z0 + 1 : x1 - x0 + 1;
        }

        int area() {
            return (x1 - x0 + 1) * (z1 - z0 + 1);
        }

        double cx() {
            return (x0 + x1 + 1) / 2.0;
        }

        double cz() {
            return (z0 + z1 + 1) / 2.0;
        }

        LotPlanner.Lot lot() {
            return new LotPlanner.Lot(x0, z0, x1, z1, front);
        }
    }

    /** 짜는 방식 (구역마다 다름) */
    static final class Spec {
        int front = 14;          // 가로변 건물 깊이
        int lotMin = 9, lotMax = 14;   // 가로변 필지 너비
        boolean mainTower;       // 대로변을 타워 터로
        int towerDepth = 32, towerMin = 30, towerMax = 44, towerGap = 5;
        int innerDepthMin = 12, innerDepthMax = 17;
        int innerMin = 8, innerMax = 13, innerGap = 1;
        int alley = 3;
    }

    record Plan(List<Lot> lots, List<int[]> alleys) {
    }

    private static final String[] SIDES = {"south", "north", "west", "east"};

    private final BuildMask m;
    private final CityTerrain t;
    private final MainRoads roads;
    private final Spec s;
    private final Random r;
    private final List<Lot> lots = new ArrayList<>();
    private final List<int[]> alleys = new ArrayList<>();

    private BlockLayout(BuildMask m, CityTerrain t, MainRoads roads, Spec s, Random r) {
        this.m = m;
        this.t = t;
        this.roads = roads;
        this.s = s;
        this.r = r;
    }

    static Plan plan(BuildMask m, CityTerrain t, MainRoads roads, int[] block, Spec s, Random r) {
        BlockLayout b = new BlockLayout(m, t, roads, s, r);
        b.run(block[0], block[1], block[2], block[3]);
        return new Plan(b.lots, b.alleys);
    }

    private void run(int X0, int Z0, int X1, int Z1) {
        // 변마다: 길에 닿는지, 큰길인지
        boolean[] road = new boolean[4], main = new boolean[4];
        for (int k = 0; k < 4; k++) {
            int[] hit = sideRoad(SIDES[k], X0, Z0, X1, Z1);
            road[k] = hit[0] * 2 >= hit[2];
            main[k] = road[k] && hit[1] * 2 > hit[0];
        }
        int[] depth = new int[4];
        for (int k = 0; k < 4; k++) {
            depth[k] = !road[k] ? 0 : main[k] && s.mainTower ? s.towerDepth : s.front;
        }
        int A = s.alley;
        // 남북으로: 앞줄 둘 + 골목 둘 + 안쪽이 안 들어가면 골목 없이 앞줄끼리 등을 맞댐
        int dz = Z1 - Z0 + 1, dx = X1 - X0 + 1;
        boolean innerZ = dz - depth[0] - depth[1] - (depth[0] > 0 ? A : 0) - (depth[1] > 0 ? A : 0) >= s.innerDepthMin;
        boolean innerX = dx - depth[2] - depth[3] - (depth[2] > 0 ? A : 0) - (depth[3] > 0 ? A : 0) >= s.innerMin + 2;
        if (!innerZ) {
            fitBackToBack(depth, 0, 1, dz);
        }
        if (!innerX) {
            fitBackToBack(depth, 2, 3, dx);
        }
        boolean inner = innerZ && innerX;
        int ds = depth[0], dn = depth[1], dw = depth[2], de = depth[3];
        // 안쪽 (골목 둘레 안)
        int ix0 = X0 + dw + (inner && dw > 0 ? A : 0), ix1 = X1 - de - (inner && de > 0 ? A : 0);
        int iz0 = Z0 + dn + (inner && dn > 0 ? A : 0), iz1 = Z1 - ds - (inner && ds > 0 ? A : 0);

        // 골목 둘레 (가로변 줄 바로 안쪽)
        if (inner) {
            if (ds > 0) {
                alley(X0 + dw, Z1 - ds - A + 1, X1 - de, Z1 - ds);
            }
            if (dn > 0) {
                alley(X0 + dw, Z0 + dn, X1 - de, Z0 + dn + A - 1);
            }
            if (dw > 0) {
                alley(X0 + dw, Z0 + dn, X0 + dw + A - 1, Z1 - ds);
            }
            if (de > 0) {
                alley(X1 - de - A + 1, Z0 + dn, X1 - de, Z1 - ds);
            }
        }
        // 가로변 줄 (모퉁이 건물은 남·북 줄이 가짐). 골목 입구 자리 (골목 둘레와 이어지는 곳)
        if (ds > 0) {
            row(X0, Z1 - ds + 1, X1, Z1, "south", main[0], inner ? gaps(ix0, ix1, X1 - X0 + 1) : new int[0]);
        }
        if (dn > 0) {
            row(X0, Z0, X1, Z0 + dn - 1, "north", main[1], inner ? gaps(ix0, ix1, X1 - X0 + 1) : new int[0]);
        }
        int cz0 = Z0 + dn, cz1 = Z1 - ds;
        if (dw > 0 && cz1 - cz0 + 1 >= s.lotMin) {
            row(X0, cz0, X0 + dw - 1, cz1, "west", main[2], inner ? gaps(iz0, iz1, cz1 - cz0 + 1) : new int[0]);
        }
        if (de > 0 && cz1 - cz0 + 1 >= s.lotMin) {
            row(X1 - de + 1, cz0, X1, cz1, "east", main[3], inner ? gaps(iz0, iz1, cz1 - cz0 + 1) : new int[0]);
        }
        if (!inner && (ds == 0 || dn == 0) && ds + dn < dz) {
            // 한쪽만 길: 남은 깊이는 안쪽 필지로
            ix0 = X0 + dw;
            ix1 = X1 - de;
            iz0 = dn > 0 ? Z0 + dn : Z0;
            iz1 = ds > 0 ? Z1 - ds : Z1;
            inner = iz1 - iz0 + 1 >= s.innerDepthMin && ix1 - ix0 + 1 >= s.innerMin;
        }
        if (inner) {
            interior(ix0, iz0, ix1, iz1, X0 + dw, X1 - de, ds > 0, dn > 0);
        }
    }

    /** 골목이 들어갈 자리가 없으면 마주 보는 두 앞줄이 등을 맞대게 깊이를 나눔 */
    private static void fitBackToBack(int[] depth, int a, int b, int span) {
        if (depth[a] > 0 && depth[b] > 0) {
            depth[a] = span / 2;
            depth[b] = span - span / 2;
        } else if (depth[a] > 0) {
            depth[a] = Math.min(span, Math.max(depth[a], span));
        } else if (depth[b] > 0) {
            depth[b] = Math.min(span, Math.max(depth[b], span));
        }
    }

    /** 가로변 줄의 골목 입구 가운데 위치들: 안쪽 범위 [lo, hi] 안, 길이 70 넘으면 둘 */
    private int[] gaps(int lo, int hi, int length) {
        if (hi - lo < 6) {
            return new int[0];
        }
        if (length > 70) {
            return new int[]{lo + (hi - lo) / 3, lo + 2 * (hi - lo) / 3};
        }
        return new int[]{(lo + hi) / 2};
    }

    /**
     * 가로변 줄 하나 (직사각형 [x0..x1]×[z0..z1], 정면 front). gapAt: 골목 입구 가운데 좌표 (줄 방향).
     */
    private void row(int x0, int z0, int x1, int z1, String front, boolean main, int[] gapAt) {
        boolean alongX = front.equals("south") || front.equals("north");
        int lo = alongX ? x0 : z0, hi = alongX ? x1 : z1;
        int half = s.alley / 2;
        int start = lo;
        List<int[]> segs = new ArrayList<>();
        for (int g : gapAt) {
            if (g - half - 1 >= start) {
                segs.add(new int[]{start, g - half - 1});
            }
            if (alongX) {
                alley(g - half, z0, g - half + s.alley - 1, z1);
            } else {
                alley(x0, g - half, x1, g - half + s.alley - 1);
            }
            start = g - half + s.alley;
        }
        if (start <= hi) {
            segs.add(new int[]{start, hi});
        }
        Tier tier = main ? Tier.MAIN : Tier.STREET;
        for (int[] seg : segs) {
            int a = seg[0];
            if (main && s.mainTower) {
                // 타워 터: 너비 towerMin..towerMax, 사이는 공개공지
                while (seg[1] - a + 1 >= s.towerMin) {
                    int wdt = Math.min(seg[1] - a + 1, s.towerMin + r.nextInt(s.towerMax - s.towerMin + 1));
                    if (seg[1] - (a + wdt) + 1 < s.towerMin && seg[1] - (a + wdt) + 1 > 0 && seg[1] - a + 1 <= s.towerMax + 8) {
                        wdt = seg[1] - a + 1;
                    }
                    addLot(alongX, a, a + wdt - 1, x0, z0, x1, z1, front, tier, false, true);
                    a += wdt + s.towerGap;
                }
                if (a > seg[1]) {
                    continue;
                }
            }
            // 맞벽 필지: 너비 lotMin..lotMax, 틈 없이
            while (seg[1] - a + 1 >= s.lotMin) {
                int wdt = s.lotMin + r.nextInt(s.lotMax - s.lotMin + 1);
                int rest = seg[1] - (a + wdt) + 1;
                if (rest < s.lotMin) {
                    wdt = seg[1] - a + 1; // 끝 조각은 앞 필지에 붙임
                    if (wdt > s.lotMax + s.lotMin) {
                        wdt = (seg[1] - a + 1) / 2;
                    }
                }
                addLot(alongX, a, a + wdt - 1, x0, z0, x1, z1, front, tier, true, false);
                a += wdt;
            }
        }
    }

    private void addLot(boolean alongX, int a, int b, int x0, int z0, int x1, int z1, String front, Tier tier, boolean party, boolean tower) {
        int lx0 = alongX ? a : x0, lx1 = alongX ? b : x1, lz0 = alongX ? z0 : a, lz1 = alongX ? z1 : b;
        int[] cut = trim(lx0, lz0, lx1, lz1, front, tower ? s.towerMin - 6 : Math.max(DistrictFill.SHOP_MIN_W, s.lotMin - 2), tower ? 20 : DistrictFill.SHOP_MIN_D);
        if (cut != null) {
            lots.add(new Lot(cut[0], cut[1], cut[2], cut[3], front, tier, party, tower));
        }
    }

    /** 골목 안: 골목을 보는 필지 줄들. 깊으면 가운데 골목 */
    private void interior(int ix0, int iz0, int ix1, int iz1, int ax0, int ax1, boolean southAlley, boolean northAlley) {
        int H = iz1 - iz0 + 1;
        int pair = 2 * s.innerDepthMax + s.alley;
        int pairs = Math.max(1, (H + s.alley) / pair + ((H + s.alley) % pair > s.innerDepthMax ? 1 : 0));
        // 두 줄씩(등 맞댐) 묶고 묶음 사이에 가로 골목
        int z = iz0;
        for (int p = 0; p < pairs; p++) {
            int left = iz1 - z + 1 - (pairs - p - 1) * pair;
            int span = p == pairs - 1 ? iz1 - z + 1 : Math.min(left, 2 * s.innerDepthMax);
            if (span < s.innerDepthMin) {
                break;
            }
            boolean twoRows = span >= 2 * s.innerDepthMin;
            if (twoRows) {
                int north = span / 2;
                innerRow(ix0, z, ix1, z + north - 1, p == 0 && !northAlley && !southAlley ? "south" : "north");
                innerRow(ix0, z + north, ix1, z + span - 1, "south");
            } else {
                innerRow(ix0, z, ix1, z + span - 1, p == pairs - 1 && southAlley || !northAlley ? "south" : "north");
            }
            z += span;
            if (p < pairs - 1) {
                alley(ax0, z, ax1, z + s.alley - 1);
                z += s.alley;
            }
        }
    }

    private void innerRow(int x0, int z0, int x1, int z1, String front) {
        int a = x0;
        while (x1 - a + 1 >= s.innerMin) {
            int wdt = s.innerMin + r.nextInt(s.innerMax - s.innerMin + 1);
            if (x1 - (a + wdt + s.innerGap) + 1 < s.innerMin) {
                wdt = x1 - a + 1;
                if (wdt > s.innerMax + s.innerMin) {
                    wdt = (x1 - a + 1 - s.innerGap) / 2;
                }
            }
            int[] cut = trim(a, z0, a + wdt - 1, z1, front, Math.max(DistrictFill.SHOP_MIN_W, s.innerMin - 1), DistrictFill.SHOP_MIN_D);
            if (cut != null) {
                lots.add(new Lot(cut[0], cut[1], cut[2], cut[3], front, Tier.INNER, s.innerGap == 0, false));
            }
            a += wdt + s.innerGap;
        }
    }

    private void alley(int x0, int z0, int x1, int z1) {
        if (x1 >= x0 && z1 >= z0) {
            alleys.add(new int[]{x0, z0, x1, z1});
        }
    }

    /**
     * 지을 수 없는 칸이 없을 때까지 정면이 아닌 변부터 깎음. 정면 너비 minW, 깊이 minD 보다 작아지면 null.
     */
    private int[] trim(int xa, int za, int xb, int zb, String front, int minW, int minD) {
        boolean alongX = front.equals("south") || front.equals("north");
        while (true) {
            int w = alongX ? xb - xa + 1 : zb - za + 1, d = alongX ? zb - za + 1 : xb - xa + 1;
            if (w < minW || d < minD) {
                return null;
            }
            int area = (xb - xa + 1) * (zb - za + 1);
            int free = m.count(xa, za, xb, zb);
            if (free == area) {
                return new int[]{xa, za, xb, zb};
            }
            if (free < area / 2) {
                return null;
            }
            int north = (xb - xa + 1) - m.count(xa, za, xb, za);
            int south = (xb - xa + 1) - m.count(xa, zb, xb, zb);
            int west = (zb - za + 1) - m.count(xa, za, xa, zb);
            int east = (zb - za + 1) - m.count(xb, za, xb, zb);
            int worst = Math.max(Math.max(north, south), Math.max(west, east));
            if (worst == 0) {
                return null;
            }
            if (worst == north) {
                za++;
            } else if (worst == south) {
                zb--;
            } else if (worst == west) {
                xa++;
            } else {
                xb--;
            }
        }
    }

    /** 변 바로 바깥(6칸까지)을 훑어 {길 닿은 표본 수, 그중 큰길, 표본 수} */
    private int[] sideRoad(String side, int X0, int Z0, int X1, int Z1) {
        int hits = 0, mains = 0, n = 0;
        boolean alongX = side.equals("south") || side.equals("north");
        int lo = alongX ? X0 + 2 : Z0 + 2, hi = alongX ? X1 - 2 : Z1 - 2;
        for (int a = lo; a <= hi; a += 4) {
            n++;
            for (int k = 1; k <= 6; k++) {
                int x = switch (side) {
                    case "west" -> X0 - k;
                    case "east" -> X1 + k;
                    default -> a;
                };
                int z = switch (side) {
                    case "north" -> Z0 - k;
                    case "south" -> Z1 + k;
                    default -> a;
                };
                if (t.column(x, z).isRoad()) {
                    hits++;
                    if (roads.isMain(x, z)) {
                        mains++;
                    }
                    break;
                }
            }
        }
        return new int[]{hits, mains, Math.max(1, n)};
    }
}
