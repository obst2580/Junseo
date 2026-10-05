package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 산비탈 동네. 산 기슭 큰길에서 비탈을 따라 층층이 올라가는 마을을 짓습니다 (BuildMask 는 산을 빼므로 지형을 직접 읽음).
 * <ul>
 *   <li>남산 서쪽·남쪽 비탈 (용산 쪽): 해방촌·후암동·보광동 — 다닥다닥 붙은 붉은 벽돌 다가구, 석축, 계단길, 동네 가게</li>
 *   <li>북한산 남쪽 기슭: 평창동 — 높은 석축 위 큰 단독주택 필지; 등산로 입구 — 식당·등산복 가게, 등산로 문, 공영주차장, 산길</li>
 * </ul>
 * 자리 찾기: 산 기슭에서 큰길(인도)에 닿는 곳을 찾아, 길을 정면으로 하고 비탈 위로 뻗는 직사각형 터를 고릅니다
 * (이미 있는 랜드마크·거점·도로·터널을 피함). 마을 짓기는 {@link HillVillage}.
 */
final class HillsidePlan {
    /** 남산·북한산에서 터를 찾을 때 쓰는 값 */
    record Spec(String district, int[] widths, int minDepth, int maxDepth, int topMh, int maxSites) {
    }

    /** 고른 터: 월드 직사각형, 정면(길 쪽, 아래쪽) 방향, 점수 */
    record Site(String district, String front, int x0, int z0, int x1, int z1, double score) {
        int width() {
            return front.equals("south") || front.equals("north") ? x1 - x0 + 1 : z1 - z0 + 1;
        }

        int depth() {
            return front.equals("south") || front.equals("north") ? z1 - z0 + 1 : x1 - x0 + 1;
        }

        double cx() {
            return (x0 + x1) / 2.0;
        }

        double cz() {
            return (z0 + z1) / 2.0;
        }
    }

    static final Spec NAMSAN = new Spec("namsan", new int[]{76, 64, 52}, 34, 72, 32, 4);
    static final Spec BUKHANSAN = new Spec("bukhansan", new int[]{96, 84, 72, 60}, 38, 84, 30, 7);

    static List<Placement> plan(CityTerrain t, List<Placement> existing) {
        List<Placement> out = new ArrayList<>();
        for (HillVillage v : villages(t, existing)) {
            out.addAll(v.placements());
        }
        return out;
    }

    /** 마을들 (배치 전 구조: 줄·필지·계단길, 검사용) */
    static List<HillVillage> villages(CityTerrain t, List<Placement> existing) {
        List<HillVillage> out = new ArrayList<>();
        Blocked blocked = new Blocked(t, existing);
        List<Site> nam = findSites(t, NAMSAN, blocked);
        List<Site> buk = findSites(t, BUKHANSAN, blocked);
        double northWest = nam.stream().filter(s -> s.front.equals("west")).mapToDouble(Site::cz).min().orElse(0);
        for (Site s : nam) {
            String name = s.front.equals("west") ? (s.cz() == northWest ? "후암동" : "해방촌") : namsanName(s);
            out.add(new HillVillage(t, s, HillVillage.Style.HAEBANGCHON, name));
        }
        // 북한산: 가운데 봉우리에 가까운 너른 터 하나는 등산로 입구, 나머지는 평창동
        Site trail = null;
        Layout.Mountain m = t.layout().mountains().stream().filter(x -> x.district().equals("bukhansan")).findFirst().orElse(null);
        if (m != null && !buk.isEmpty()) {
            double px = m.peaks().get(0).x();
            trail = buk.stream().filter(s -> s.width() >= 60).min(Comparator.comparingDouble(s -> Math.abs(s.cx() - px))).orElse(buk.get(0));
        }
        int k = 0;
        for (Site s : buk) {
            if (s == trail) {
                out.add(new HillVillage(t, s, HillVillage.Style.TRAILHEAD, "북한산"));
            } else {
                out.add(new HillVillage(t, s, HillVillage.Style.PYEONGCHANG, k++ == 0 ? "평창동" : "평창동 " + k));
            }
        }
        return out;
    }

    /** 남산 마을 이름: 서쪽(용산 쪽) 북편 후암동·남편 해방촌, 남쪽(한강 쪽) 보광동 */
    private static String namsanName(Site s) {
        return switch (s.front) {
            case "west" -> "해방촌";
            case "south" -> "보광동";
            case "east" -> "약수동";
            default -> "회현동";
        };
    }

    // ------------------------------------------------------------------ 피할 곳

    /** 이미 쓴 땅 (랜드마크 상자 둘레 3칸, 거점 둘레 10칸) */
    static final class Blocked {
        private final List<double[]> boxes = new ArrayList<>();

        Blocked(CityTerrain t, List<Placement> existing) {
            for (Placement p : existing) {
                if (CityBuildings.isGround(p)) {
                    continue;
                }
                double[] b = p.bounds();
                boxes.add(new double[]{b[0] - 3, b[1] - 3, b[2] + 3, b[3] + 3});
            }
            for (Layout.Hub h : t.layout().hubs()) {
                boxes.add(new double[]{h.x() - 10, h.z() - 10, h.x() + 10, h.z() + 10});
            }
        }

        boolean at(int x, int z) {
            for (double[] b : boxes) {
                if (x + 1 > b[0] && x < b[2] && z + 1 > b[1] && z < b[3]) {
                    return true;
                }
            }
            return false;
        }

        void add(Site s, int gap) {
            boxes.add(new double[]{s.x0 - gap, s.z0 - gap, s.x1 + 1 + gap, s.z1 + 1 + gap});
        }
    }

    // ------------------------------------------------------------------ 터 찾기

    /** 칸 상태 (미리 읽어 둠) */
    private static final int ROAD = 1, TUNNEL = 2, WATER = 4, INSIDE = 8, BLOCKED = 16;

    private static final class Grid {
        final int x0, z0, w, h;
        final int[] mh;
        final byte[] flags;

        Grid(CityTerrain t, Polygon area, String district, Blocked blocked, int margin) {
            double[] b = area.bounds();
            x0 = (int) Math.floor(b[0]) - margin;
            z0 = (int) Math.floor(b[1]) - margin;
            w = (int) Math.ceil(b[2]) + margin - x0 + 1;
            h = (int) Math.ceil(b[3]) + margin - z0 + 1;
            mh = new int[w * h];
            flags = new byte[w * h];
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int x = x0 + i, z = z0 + j, k = j * w + i;
                    Column c = t.column(x, z);
                    mh[k] = c.mountainHeight;
                    int f = 0;
                    if (c.isRoad() || c.deck) {
                        f |= ROAD;
                    }
                    if (c.tunnel) {
                        f |= TUNNEL;
                    }
                    if (c.isWater()) {
                        f |= WATER;
                    }
                    if (district.equals(c.district)) {
                        f |= INSIDE;
                    }
                    if (blocked.at(x, z)) {
                        f |= BLOCKED;
                    }
                    flags[k] = (byte) f;
                }
            }
        }

        int flags(int x, int z) {
            int i = x - x0, j = z - z0;
            return i < 0 || j < 0 || i >= w || j >= h ? 0 : flags[j * w + i];
        }

        int mh(int x, int z) {
            int i = x - x0, j = z - z0;
            return i < 0 || j < 0 || i >= w || j >= h ? 0 : mh[j * w + i];
        }
    }

    /** 정면 방향별 (u, v) → 월드 (x, z). v 가 커질수록 아래쪽(길 쪽) */
    private static int[] world(String front, int u, int v) {
        return switch (front) {
            case "south" -> new int[]{u, v};
            case "north" -> new int[]{-u, -v};
            case "east" -> new int[]{v, -u};
            default -> new int[]{-v, u};
        };
    }

    static List<Site> findSites(CityTerrain t, Spec spec, Blocked blocked) {
        Polygon area = Plans.district(t, spec.district);
        if (area == null) {
            return List.of();
        }
        Grid g = new Grid(t, area, spec.district, blocked, 40);
        List<Site> cands = new ArrayList<>();
        for (String front : new String[]{"south", "north", "east", "west"}) {
            // (u, v) 범위: 격자 네 귀퉁이를 돌려서
            int umin = Integer.MAX_VALUE, umax = Integer.MIN_VALUE, vmin = Integer.MAX_VALUE, vmax = Integer.MIN_VALUE;
            for (int[] c : new int[][]{{g.x0, g.z0}, {g.x0 + g.w - 1, g.z0}, {g.x0, g.z0 + g.h - 1}, {g.x0 + g.w - 1, g.z0 + g.h - 1}}) {
                int[] uv = switch (front) {
                    case "south" -> new int[]{c[0], c[1]};
                    case "north" -> new int[]{-c[0], -c[1]};
                    case "east" -> new int[]{-c[1], c[0]};
                    default -> new int[]{c[1], -c[0]};
                };
                umin = Math.min(umin, uv[0]);
                umax = Math.max(umax, uv[0]);
                vmin = Math.min(vmin, uv[1]);
                vmax = Math.max(vmax, uv[1]);
            }
            int nu = umax - umin + 1;
            int[] foot = new int[nu];
            Arrays.fill(foot, Integer.MIN_VALUE);
            for (int u = umin; u <= umax; u++) {
                int lastHigh = Integer.MIN_VALUE;
                for (int v = vmin; v < vmax; v++) {
                    int[] p = world(front, u, v), q = world(front, u, v + 1);
                    int f = g.flags(p[0], p[1]);
                    if ((f & INSIDE) != 0 && (g.mh(p[0], p[1]) >= 10 || (f & TUNNEL) != 0)) {
                        lastHigh = v;
                    }
                    if ((f & TUNNEL) != 0) {
                        continue;
                    }
                    if ((f & (ROAD | WATER)) != 0) {
                        lastHigh = Integer.MIN_VALUE;
                        continue;
                    }
                    int fq = g.flags(q[0], q[1]);
                    if (lastHigh != Integer.MIN_VALUE && (fq & ROAD) != 0 && (fq & TUNNEL) == 0 && v - lastHigh <= 60) {
                        foot[u - umin] = v;
                        break;
                    }
                }
            }
            for (int W : spec.widths) {
                for (int u0 = umin; u0 + W - 1 <= umax; u0 += 4) {
                    int vf = Integer.MAX_VALUE, vb = Integer.MIN_VALUE;
                    boolean ok = true;
                    for (int u = u0; u < u0 + W && ok; u++) {
                        int fv = foot[u - umin];
                        ok = fv != Integer.MIN_VALUE;
                        vf = Math.min(vf, fv);
                        vb = Math.max(vb, fv);
                    }
                    if (!ok || vb - vf > W / 3) {
                        continue;
                    }
                    // 정면은 가장 위쪽 기슭 (상자 안에 길이 들어오지 않게; 다른 끝의 길까지는 평평한 땅이 조금 남음)
                    int gap = vb - vf;
                    int depth = 0;
                    double score = 0;
                    for (int k = 0; k < spec.maxDepth; k++) {
                        int v = vf - k;
                        boolean rowOk = true;
                        int[] row = new int[W];
                        double rowScore = 0;
                        for (int u = u0; u < u0 + W && rowOk; u++) {
                            int[] p = world(front, u, v);
                            int f = g.flags(p[0], p[1]);
                            int hh = g.mh(p[0], p[1]);
                            rowOk = (f & (ROAD | WATER | TUNNEL | BLOCKED)) == 0 && hh <= spec.topMh + 14
                                    && ((f & INSIDE) != 0 || k < 14);
                            row[u - u0] = hh;
                            rowScore += hh >= 1 && hh <= spec.topMh ? 1 : hh == 0 ? 0.35 : 0;
                        }
                        if (!rowOk) {
                            break;
                        }
                        depth = k + 1;
                        score += rowScore;
                        Arrays.sort(row);
                        if (row[W / 2] >= spec.topMh) {
                            break;
                        }
                    }
                    if (depth < spec.minDepth) {
                        continue;
                    }
                    int[] a = world(front, u0, vf - depth + 1), b = world(front, u0 + W - 1, vf);
                    cands.add(new Site(spec.district, front, Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                            Math.max(a[0], b[0]), Math.max(a[1], b[1]), score - 20 * gap));
                }
            }
        }
        cands.sort(Comparator.comparingDouble(Site::score).reversed());
        List<Site> out = new ArrayList<>();
        for (Site s : cands) {
            if (out.size() >= spec.maxSites) {
                break;
            }
            boolean free = true;
            for (Site o : out) {
                free &= s.x1 + 6 < o.x0 || o.x1 + 6 < s.x0 || s.z1 + 6 < o.z0 || o.z1 + 6 < s.z0;
            }
            if (free) {
                out.add(s);
            }
        }
        for (Site s : out) {
            blocked.add(s, 4);
        }
        return out;
    }

    private HillsidePlan() {
    }
}
