package com.junseo.citymap.terrain;

import com.junseo.citymap.geo.Polyline;
import com.junseo.citymap.layout.Layout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 도시 도로망 검사: 막다른 길이 없는지, 한 덩어리로 이어지는지, 다리가 제대로 놓였는지, 주요 장소에 길이 닿는지.
 * 설계도(map/layout.json)를 고치면 이 검사가 먼저 잡아 줍니다.
 */
class RoadNetworkTest {
    static CityTerrain terrain;
    static List<Layout.Road> roads;
    /** 길이 안 닿아도 되는 장소: 산속(채석장·은신처·남산타워)과 섬(교도소) */
    static final Set<String> OFF_ROAD_HUBS = Set.of("quarry", "hideout", "tower", "prison");

    @BeforeAll
    static void setUp() throws IOException {
        terrain = CityTerrainTest.load();
        roads = terrain.layout().roads().stream().filter(r -> !r.kind().equals("runway")).toList();
    }

    static double distanceToRoad(Layout.Road r, double x, double z) {
        double[] out = new double[2];
        new Polyline(r.line()).nearest(x, z, out);
        return out[0];
    }

    /** 길 끝이 다른 길(또는 자기 자신의 다른 부분, 고리 모양 길) 위에 닿는지 */
    static boolean endTouches(Layout.Road r, double[] end) {
        for (Layout.Road o : roads) {
            if (o == r) {
                continue;
            }
            if (distanceToRoad(o, end[0], end[1]) <= CityTerrain.halfWidth(o.kind()) - 2) {
                return true;
            }
        }
        // 고리: 끝이 같은 길의 다른 쪽 끝과 같은 점
        double[] first = r.line().get(0), last = r.line().get(r.line().size() - 1);
        return Math.hypot(first[0] - last[0], first[1] - last[1]) < 1;
    }

    @Test
    void noDeadEnds() {
        List<String> bad = new ArrayList<>();
        for (Layout.Road r : roads) {
            for (double[] end : List.of(r.line().get(0), r.line().get(r.line().size() - 1))) {
                if (!endTouches(r, end)) {
                    bad.add(r.id() + " (" + Math.round(end[0]) + ", " + Math.round(end[1]) + ")");
                }
            }
        }
        assertTrue(bad.isEmpty(), "막다른 길: " + bad);
    }

    @Test
    void wholeNetworkIsConnected() {
        int n = roads.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (meet(roads.get(i), roads.get(j))) {
                    parent[find(parent, i)] = find(parent, j);
                }
            }
        }
        List<String> lonely = new ArrayList<>();
        int root = find(parent, 0);
        for (int i = 0; i < n; i++) {
            if (find(parent, i) != root) {
                lonely.add(roads.get(i).id());
            }
        }
        assertTrue(lonely.isEmpty(), "나머지 도로와 이어지지 않은 길: " + lonely);
    }

    /** 두 길이 만나는지 (가로지르거나 한쪽 끝이 다른 쪽 위에 닿음) */
    static boolean meet(Layout.Road a, Layout.Road b) {
        for (double[] end : List.of(a.line().get(0), a.line().get(a.line().size() - 1))) {
            if (distanceToRoad(b, end[0], end[1]) <= CityTerrain.halfWidth(b.kind())) {
                return true;
            }
        }
        for (double[] end : List.of(b.line().get(0), b.line().get(b.line().size() - 1))) {
            if (distanceToRoad(a, end[0], end[1]) <= CityTerrain.halfWidth(a.kind())) {
                return true;
            }
        }
        for (int i = 0; i + 1 < a.line().size(); i++) {
            for (int j = 0; j + 1 < b.line().size(); j++) {
                if (cross(a.line().get(i), a.line().get(i + 1), b.line().get(j), b.line().get(j + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean cross(double[] p, double[] p2, double[] q, double[] q2) {
        double rx = p2[0] - p[0], rz = p2[1] - p[1], sx = q2[0] - q[0], sz = q2[1] - q[1];
        double den = rx * sz - rz * sx;
        if (Math.abs(den) < 1e-9) {
            return false;
        }
        double t = ((q[0] - p[0]) * sz - (q[1] - p[1]) * sx) / den;
        double u = ((q[0] - p[0]) * rz - (q[1] - p[1]) * rx) / den;
        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }

    static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** 물 위를 지나는 곳은 길 전체 폭이 다리 상판이어야 함 (길 가운데는 땅인데 한쪽만 물에 걸치면 안 됨) */
    @Test
    void roadsCrossWaterAsWholeBridges() {
        List<String> bad = new ArrayList<>();
        for (Layout.Road r : terrain.layout().roads()) {
            if (r.kind().equals("tunnel")) {
                continue;
            }
            Polyline line = new Polyline(r.line());
            double half = CityTerrain.halfWidth(r.kind());
            int wetEdge = 0;
            for (int i = 0; i + 1 < r.line().size(); i++) {
                double[] a = r.line().get(i), b = r.line().get(i + 1);
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
                double ux = (b[0] - a[0]) / len, uz = (b[1] - a[1]) / len;
                for (double t = 0; t < len; t += 1) {
                    double x = a[0] + ux * t, z = a[1] + uz * t;
                    boolean centerWet = wet(x, z);
                    for (double side : new double[]{-half + 1, half - 1}) {
                        if (!centerWet && wet(x - uz * side, z + ux * side)) {
                            wetEdge++;
                        }
                    }
                }
            }
            // 다리 양 끝(물가)에서는 몇 칸 걸칠 수 있음
            if (wetEdge > 4 * half) {
                bad.add(r.id() + " 물가에 " + wetEdge + "칸 걸침");
            }
        }
        assertTrue(bad.isEmpty(), "물가를 따라 반쯤 물에 걸친 길: " + bad);
    }

    static boolean wet(double x, double z) {
        Column c = terrain.column((int) Math.floor(x), (int) Math.floor(z));
        return c.isWater();
    }

    /** 물을 건너는 길은 상판에 차선이 그대로 이어짐 */
    @Test
    void bridgesKeepTheirLaneMarkings() {
        int decks = 0, lines = 0;
        for (Layout.Road r : terrain.layout().roads()) {
            for (int i = 0; i + 1 < r.line().size(); i++) {
                double[] p = r.line().get(i), q = r.line().get(i + 1);
                double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
                for (double t = 0; t < len; t += 1) {
                    for (int dx = -1; dx <= 0; dx++) {
                        Column c = terrain.column((int) Math.floor(p[0] + (q[0] - p[0]) * t / len) + dx,
                                (int) Math.floor(p[1] + (q[1] - p[1]) * t / len));
                        if (c.deck) {
                            decks++;
                            if (c.surface == Surface.LINE_YELLOW) {
                                lines++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(decks > 500, "다리 상판 " + decks);
        assertTrue(lines > decks / 4, "다리 가운데 중앙선: " + lines + "/" + decks);
    }

    @Test
    void everyPlaceIsNextToARoad() {
        List<String> bad = new ArrayList<>();
        for (Layout.Hub h : terrain.layout().hubs()) {
            if (OFF_ROAD_HUBS.contains(h.id())) {
                continue;
            }
            double best = Double.MAX_VALUE;
            for (Layout.Road r : roads) {
                double edge = distanceToRoad(r, h.x(), h.z()) - CityTerrain.halfWidth(r.kind());
                best = Math.min(best, edge);
            }
            if (best < 3 || best > 30) {
                bad.add(h.id() + " (길 가장자리에서 " + Math.round(best) + "m)");
            }
        }
        assertTrue(bad.isEmpty(), "길 바로 옆(3~30m)에 있지 않은 장소: " + bad);
    }

    @Test
    void tunnelAndRunwayStayAsDesigned() {
        assertEquals(1, terrain.layout().roads().stream().filter(r -> r.kind().equals("tunnel")).count());
        assertEquals(1, terrain.layout().roads().stream().filter(r -> r.kind().equals("runway")).count());
    }
}
