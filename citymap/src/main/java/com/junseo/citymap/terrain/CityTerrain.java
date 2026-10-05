package com.junseo.citymap.terrain;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.geo.Polyline;
import com.junseo.citymap.layout.Layout;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 도시 설계도(layout.json)로 지도 한 칸 한 칸이 어떻게 생겼는지 계산합니다.
 * 마인크래프트와 상관없는 순수 계산이라서 테스트와 미리보기 그림에 그대로 씁니다.
 * 한 번 만들면 바뀌지 않으므로 여러 스레드에서 동시에 써도 안전합니다.
 */
public final class CityTerrain {
    /** 공간 색인 칸 크기 (블록) */
    private static final int CELL = 64;
    /** 이 높이보다 산이 높아야 터널에 천장이 생김 */
    private static final int TUNNEL_COVER = 10;
    private static final int MAX_MOUNTAIN = 300;
    private static final double OFFSET_X = 0.0137;
    private static final double OFFSET_Z = -0.0129;
    /** 횡단보도 폭 (도로를 따라) */
    private static final double CROSSWALK = 4;

    record DistrictShape(String id, String name, int phase, Polygon polygon) {
    }

    record RiverShape(String id, Polyline line, double half, int maxDepth) {
    }

    record RoadShape(String id, String kind, Polyline line, double half) {
    }

    record MountainShape(Layout.Mountain mountain, Polygon polygon) {
    }

    /** 섬: 원이면 polygon 이 null */
    record IslandShape(Layout.Island island, Polygon polygon) {
        boolean contains(double x, double z) {
            return polygon != null ? polygon.contains(x, z)
                    : Math.hypot(x - island.cx(), z - island.cz()) <= island.radius();
        }

        /** 섬 안에서 해안선까지 거리 */
        double shoreDistance(double x, double z) {
            return polygon != null ? polygon.edgeDistance(x, z)
                    : island.radius() - Math.hypot(x - island.cx(), z - island.cz());
        }

        double[] bounds() {
            return polygon != null ? polygon.bounds()
                    : new double[]{island.cx() - island.radius(), island.cz() - island.radius(),
                    island.cx() + island.radius(), island.cz() + island.radius()};
        }
    }

    /** 공간 색인 한 칸. 이 칸 근처에 있는 것들만 모아 둡니다. segs 는 (번호, 선분) 쌍을 펼친 배열 */
    private static final class Cell {
        int[] river = new int[0];
        int[] road = new int[0];
        int[] roadMask = new int[0];
        int[] districts = new int[0];
        int[] mountains = new int[0];
        int[] islands = new int[0];
        int[] hubs = new int[0];
        boolean sea;
    }

    private final Layout layout;
    private final int groundY;
    private final int waterY;
    /** 도로에서 이 거리(가장자리 기준)까지는 산을 깎아 평평하게 */
    private final double roadClear;
    /** 도로에서 이 거리부터는 산 높이를 그대로 */
    private final double roadFade;
    /** 산 구역 테두리에서 이 거리만큼 안쪽으로 들어가야 산이 제 높이가 됨 */
    private final double mountainEdgeFade;
    /** 바다의 가장 깊은 곳 */
    private final int seaDepth;
    private final double bx0, bz0, bx1, bz1;
    private final List<DistrictShape> districts = new ArrayList<>();
    private final List<RiverShape> rivers = new ArrayList<>();
    private final Polygon sea;
    private final List<IslandShape> islands = new ArrayList<>();
    private final List<RoadShape> roads = new ArrayList<>();
    /** 도로마다 교차로 위치(선을 따라 잰 값)와, 그 교차로에서 횡단보도가 시작되는 거리 */
    private double[][] junctionAt;
    private double[][] junctionClear;
    private final List<MountainShape> mountains = new ArrayList<>();
    private final List<Layout.Hub> hubs;
    private final Noise noise = new Noise(20261004L);
    private final int gx0, gz0, gw, gh;
    private final Cell[] cells;

    public static CityTerrain load(Reader reader) {
        return new CityTerrain(Layout.parse(reader));
    }

    public CityTerrain(Layout layout) {
        this.layout = layout;
        this.groundY = layout.terrain().groundY();
        this.waterY = layout.terrain().waterY();
        this.roadClear = layout.terrain().roadClear();
        this.roadFade = layout.terrain().roadFade();
        this.mountainEdgeFade = layout.terrain().mountainEdgeFade();
        bx0 = layout.borderMin()[0];
        bz0 = layout.borderMin()[1];
        bx1 = layout.borderMax()[0];
        bz1 = layout.borderMax()[1];

        for (Layout.District d : layout.districts()) {
            Polygon p = d.ellipse() != null
                    ? Polygon.ellipse(d.ellipse().cx(), d.ellipse().cz(), d.ellipse().rx(), d.ellipse().rz(), d.ellipse().angle(), 64)
                    : new Polygon(d.polygon());
            districts.add(new DistrictShape(d.id(), d.name(), d.openPhase(), p));
        }
        Polygon seaPolygon = null;
        int seaMax = 24;
        for (Layout.Water w : layout.water()) {
            if ("sea".equals(w.kind())) {
                seaPolygon = new Polygon(w.polygon());
                if (w.depth() > 0) {
                    seaMax = w.depth();
                }
            } else {
                int depth = w.depth() > 0 ? w.depth() : (w.width() >= 200 ? 12 : 5);
                rivers.add(new RiverShape(w.id(), new Polyline(w.line()), w.width() / 2, depth));
            }
        }
        sea = seaPolygon;
        seaDepth = seaMax;
        for (Layout.Island is : layout.islands()) {
            Layout.Ellipse e = is.ellipse();
            islands.add(new IslandShape(is, e == null ? null : Polygon.ellipse(e.cx(), e.cz(), e.rx(), e.rz(), e.angle(), 64)));
        }
        for (Layout.Road r : layout.roads()) {
            roads.add(new RoadShape(r.id(), r.kind(), new Polyline(r.line()), halfWidth(r.kind())));
        }
        // 예전 설계도의 따로 그린 다리: 이제는 물을 건너는 도로가 저절로 다리가 되므로 보통 도로로 바꿔 읽음
        for (Layout.Road r : layout.bridges()) {
            String kind = "bridge-highway".equals(r.kind()) ? "highway" : "arterial";
            roads.add(new RoadShape(r.id(), kind, new Polyline(r.line()), halfWidth(kind)));
        }
        for (Layout.Mountain m : layout.mountains()) {
            for (DistrictShape d : districts) {
                if (d.id().equals(m.district())) {
                    mountains.add(new MountainShape(m, d.polygon()));
                }
            }
        }
        hubs = layout.hubs();

        gx0 = Math.floorDiv((int) Math.floor(bx0), CELL);
        gz0 = Math.floorDiv((int) Math.floor(bz0), CELL);
        gw = Math.floorDiv((int) Math.ceil(bx1), CELL) - gx0 + 1;
        gh = Math.floorDiv((int) Math.ceil(bz1), CELL) - gz0 + 1;
        cells = new Cell[gw * gh];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = new Cell();
        }
        buildIndex();
        buildJunctions();
    }

    /** 도로 종류별 절반 폭 (블록). 1차로 = 4블록 */
    public static double halfWidth(String kind) {
        return switch (kind) {
            case "highway" -> 14;     // 왕복 6차로 24 + 중앙선 2 + 갓길
            case "arterial" -> 12;    // 왕복 4차로 16 + 인도 4씩
            case "street" -> 7;       // 왕복 2차로 8 + 인도 3씩 (중로)
            case "tunnel" -> 6;       // 왕복 2차로 8 + 보행로 2씩
            case "runway" -> 22.5;
            default -> 8;
        };
    }

    /** 차가 다니는 부분의 절반 폭 (인도 제외) */
    private static double carriageHalf(String kind) {
        return switch (kind) {
            case "arterial" -> 8;
            case "street", "tunnel" -> 4;
            default -> halfWidth(kind);
        };
    }

    /** 횡단보도를 그리는 도로 (고속도로·활주로·터널은 없음) */
    private static boolean hasCrosswalks(String kind) {
        return "arterial".equals(kind) || "street".equals(kind);
    }

    /** 다리 상판이 될 수 있는 도로 */
    private static boolean canBridge(String kind) {
        return !"tunnel".equals(kind) && !"runway".equals(kind);
    }

    // ------------------------------------------------------------------ 공간 색인

    private void buildIndex() {
        List<List<Integer>> river = lists(), road = lists(), roadMask = lists(),
                dist = lists(), mount = lists(), isl = lists(), hub = lists();
        for (int f = 0; f < rivers.size(); f++) {
            RiverShape r = rivers.get(f);
            addSegments(river, f, r.line(), r.half() + 4);
        }
        for (int f = 0; f < roads.size(); f++) {
            RoadShape r = roads.get(f);
            addSegments(road, f, r.line(), r.half() + 1);
            if (!"tunnel".equals(r.kind())) {
                addSegments(roadMask, f, r.line(), r.half() + roadFade + 4);
            }
        }
        for (int i = 0; i < districts.size(); i++) {
            addBox(dist, i, districts.get(i).polygon().bounds(), 2);
        }
        for (int i = 0; i < mountains.size(); i++) {
            addBox(mount, i, mountains.get(i).polygon().bounds(), 2);
        }
        for (int i = 0; i < islands.size(); i++) {
            addBox(isl, i, islands.get(i).bounds(), 8);
        }
        for (int i = 0; i < hubs.size(); i++) {
            Layout.Hub h = hubs.get(i);
            addBox(hub, i, new double[]{h.x(), h.z(), h.x(), h.z()}, 4);
        }
        if (sea != null) {
            double[] b = sea.bounds();
            forCells(new double[]{b[0] - 130, b[1] - 130, b[2] + 130, b[3] + 130}, idx -> cells[idx].sea = true);
        }
        for (int i = 0; i < cells.length; i++) {
            Cell c = cells[i];
            c.river = toArray(river.get(i));
            c.road = toArray(road.get(i));
            c.roadMask = toArray(roadMask.get(i));
            c.districts = toArray(dist.get(i));
            c.mountains = toArray(mount.get(i));
            c.islands = toArray(isl.get(i));
            c.hubs = toArray(hub.get(i));
        }
    }

    private List<List<Integer>> lists() {
        List<List<Integer>> out = new ArrayList<>(cells.length);
        for (int i = 0; i < cells.length; i++) {
            out.add(new ArrayList<>(0));
        }
        return out;
    }

    private void addSegments(List<List<Integer>> target, int feature, Polyline line, double margin) {
        for (int s = 0; s < line.segmentCount(); s++) {
            double[] b = line.segmentBounds(s);
            int seg = s;
            forCells(new double[]{b[0] - margin, b[1] - margin, b[2] + margin, b[3] + margin}, idx -> {
                target.get(idx).add(feature);
                target.get(idx).add(seg);
            });
        }
    }

    private void addBox(List<List<Integer>> target, int item, double[] b, double margin) {
        forCells(new double[]{b[0] - margin, b[1] - margin, b[2] + margin, b[3] + margin}, idx -> target.get(idx).add(item));
    }

    private void forCells(double[] box, IntConsumer action) {
        int cx0 = Math.max(0, Math.floorDiv((int) Math.floor(box[0]), CELL) - gx0);
        int cz0 = Math.max(0, Math.floorDiv((int) Math.floor(box[1]), CELL) - gz0);
        int cx1 = Math.min(gw - 1, Math.floorDiv((int) Math.floor(box[2]), CELL) - gx0);
        int cz1 = Math.min(gh - 1, Math.floorDiv((int) Math.floor(box[3]), CELL) - gz0);
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                action.accept(cz * gw + cx);
            }
        }
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private Cell cellAt(double x, double z) {
        int cx = Math.floorDiv((int) Math.floor(x), CELL) - gx0;
        int cz = Math.floorDiv((int) Math.floor(z), CELL) - gz0;
        cx = Math.max(0, Math.min(gw - 1, cx));
        cz = Math.max(0, Math.min(gh - 1, cz));
        return cells[cz * gw + cx];
    }

    // ------------------------------------------------------------------ 바깥에서 쓰는 것

    public Layout layout() {
        return layout;
    }

    public int groundY() {
        return groundY;
    }

    public int waterY() {
        return waterY;
    }

    public boolean insideBorder(double x, double z) {
        return x >= bx0 && x < bx1 && z >= bz0 && z < bz1;
    }

    /** 사람이 서 있을 수 있는 높이 (맨 위 블록 바로 위) */
    public int standY(int x, int z) {
        Column c = column(x, z);
        if (c.isWater() && !c.deck) {
            return c.waterTop + 1;
        }
        if (c.tunnel) {
            return c.groundY + c.mountainHeight + 1;
        }
        return c.groundY + 1;
    }

    /** 그 자리의 구역 이름 (없으면 null) */
    public String districtName(int x, int z) {
        double px = x + 0.5, pz = z + 0.5;
        if (!insideBorder(px, pz)) {
            return null;
        }
        for (int i : cellAt(px, pz).districts) {
            DistrictShape d = districts.get(i);
            if (d.polygon().contains(px, pz)) {
                return d.name();
            }
        }
        return null;
    }

    /** 구역 id 로 그 구역 경계 상자의 가운데를 찾습니다 */
    public double[] districtCenter(String idOrName) {
        for (DistrictShape d : districts) {
            if (d.id().equalsIgnoreCase(idOrName) || d.name().equals(idOrName)) {
                double[] b = d.polygon().bounds();
                return new double[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
            }
        }
        return null;
    }

    public List<String> districtIds() {
        return districts.stream().map(DistrictShape::id).toList();
    }

    // ------------------------------------------------------------------ 한 칸 계산

    /**
     * 블록 (x, z) 세로줄이 어떻게 생겼는지 계산합니다.
     * 블록 한가운데에서 아주 조금 비켜서 잽니다. 정확히 가로·세로로 놓인 도로에서
     * 양쪽 칸까지의 거리가 똑같아져 중앙선이 사라지는 일을 막기 위해서입니다.
     */
    public Column column(int x, int z) {
        double px = x + 0.5 + OFFSET_X, pz = z + 0.5 + OFFSET_Z;
        Column c = new Column();
        if (!insideBorder(px, pz)) {
            // 도시 밖은 깊은 바다 (섬 도시)
            c.waterTop = waterY;
            c.groundY = waterY - 30;
            c.surface = Surface.SEA_BED;
            c.bed = Surface.SEA_BED;
            c.biome = Column.Biome.OCEAN;
            return c;
        }
        Cell cell = cellAt(px, pz);
        double[] tmp = new double[2];

        // 섬
        IslandShape island = null;
        for (int i : cell.islands) {
            IslandShape is = islands.get(i);
            if (is.contains(px, pz)) {
                island = is;
            }
        }

        // 바다
        boolean inSea = false;
        double coast = Double.MAX_VALUE;
        if (cell.sea && sea != null) {
            inSea = sea.contains(px, pz);
            coast = sea.edgeDistance(px, pz);
        }

        // 한강·샛강: riverEdge < 0 이면 물 속. 물길이 겹치는 곳은 더 깊은 쪽 깊이를 씁니다
        double riverEdge = Double.MAX_VALUE;
        int riverDepth = 0;
        for (int k = 0; k < cell.river.length; k += 2) {
            RiverShape r = rivers.get(cell.river[k]);
            r.line().distanceToSegment(cell.river[k + 1], px, pz, tmp);
            double edge = tmp[0] - r.half();
            riverEdge = Math.min(riverEdge, edge);
            if (edge <= 0) {
                riverDepth = Math.max(riverDepth, (int) Math.round(1 + (r.maxDepth() - 1) * smooth(0, 20, -edge)));
            }
        }
        boolean inRiver = riverEdge <= 0;

        // 도로 (물 위를 지나면 다리 상판이 됨)
        List<Hit> hits = roadHits(cell, px, pz, tmp);

        // 구역
        DistrictShape district = null;
        for (int i : cell.districts) {
            DistrictShape d = districts.get(i);
            if (d.polygon().contains(px, pz)) {
                district = d;
                break;
            }
        }
        c.district = district == null ? null : district.id();

        if (island == null && (inSea || inRiver)) {
            return water(c, inSea, coast, inRiver, riverDepth, hits);
        }

        c.groundY = groundY;

        // 산
        int mh = 0;
        MountainShape inMountain = null;
        for (int i : cell.mountains) {
            MountainShape m = mountains.get(i);
            int h = mountainHeight(m, px, pz, cell, tmp);
            if (h > mh) {
                mh = h;
                inMountain = m;
            }
        }

        Hit tunnelHit = null;
        for (Hit h : hits) {
            if ("tunnel".equals(h.kind)) {
                tunnelHit = h;
            }
        }
        if (tunnelHit != null) {
            int cover = tunnelCover(px, pz, cell, tmp);
            c.surface = roadSurface(tunnelHit.kind, tunnelHit.a, tunnelHit.s);
            if (cover >= TUNNEL_COVER) {
                c.tunnel = true;
                c.mountainHeight = cover;
                c.tunnelWall = tunnelHit.a >= tunnelHit.half - 1;
                c.tunnelLight = tunnelHit.a < 1 && (tunnelHit.s % 12) < 1;
                c.biome = Column.Biome.FOREST;
            }
            return c;
        }

        if (!hits.isEmpty()) {
            c.surface = combinedRoadSurface(hits);
        } else {
            c.mountainHeight = mh;
            c.groundY = groundY + mh;
            if (mh > 0) {
                c.surface = mountainSurface(inMountain, px, pz, mh, cell, tmp);
            } else if (riverEdge > 0 && riverEdge <= 2) {
                c.surface = Surface.EMBANKMENT;
            } else if (cell.sea && coast < 8 && island == null) {
                c.surface = Surface.SAND;
            } else if (island != null && island.shoreDistance(px, pz) < 6) {
                c.surface = Surface.SAND;
            } else if (district != null && district.polygon().edgeDistance(px, pz) < 1.0) {
                c.surface = switch (district.phase()) {
                    case 1 -> Surface.BORDER_1;
                    case 2 -> Surface.BORDER_2;
                    default -> Surface.BORDER_3;
                };
            }
        }

        if (c.surface == Surface.SAND) {
            c.biome = Column.Biome.BEACH;
        } else if (mh > 20) {
            c.biome = Column.Biome.FOREST;
        }

        // 거점 표시: 5×5 흰 바닥, 가운데 기둥
        for (int i : cell.hubs) {
            Layout.Hub h = hubs.get(i);
            int dx = x - (int) Math.floor(h.x()), dz = z - (int) Math.floor(h.z());
            if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                c.surface = Surface.PAD;
                if (dx == 0 && dz == 0) {
                    c.hubPillar = 7;
                }
            }
        }
        return c;
    }

    private Column water(Column c, boolean inSea, double coast, boolean inRiver, int riverDepth, List<Hit> hits) {
        c.waterTop = waterY;
        int depth = 0;
        if (inSea) {
            depth = (int) Math.round(2 + (seaDepth - 2) * smooth(0, 100, coast));
            c.bed = Surface.SEA_BED;
            c.biome = Column.Biome.OCEAN;
        }
        if (inRiver) {
            if (riverDepth > depth) {
                depth = riverDepth;
                c.bed = Surface.RIVER_BED;
                c.biome = Column.Biome.RIVER;
            }
        }
        c.groundY = waterY - Math.max(1, depth);
        c.surface = c.bed;
        Hit nearest = null;
        boolean edge = true;
        for (Hit h : hits) {
            if (!canBridge(h.kind)) {
                continue;
            }
            if (nearest == null || h.a / h.half < nearest.a / nearest.half) {
                nearest = h;
            }
            edge &= h.a >= h.half - 1;
        }
        if (nearest != null) {
            c.deck = true;
            c.surface = combinedRoadSurface(hits);
            c.railing = edge;
            c.pillar = (nearest.s % 40) < 3 && nearest.a < nearest.half - 3;
        }
        return c;
    }

    // ------------------------------------------------------------------ 도로

    /** 도로 위 한 점: 몇 번 도로, kind, 가운데선에서 거리 a, 선을 따라 잰 위치 s */
    private record Hit(int road, String kind, double a, double s, double half) {
    }

    /** 이 점에 걸친 도로들. 같은 도로는 가장 가까운 선분 하나만 */
    private List<Hit> roadHits(Cell cell, double px, double pz, double[] tmp) {
        List<Hit> out = new ArrayList<>(2);
        int lastFeature = -1;
        Hit lastHit = null;
        for (int k = 0; k < cell.road.length; k += 2) {
            int f = cell.road[k];
            RoadShape r = roads.get(f);
            r.line().distanceToSegment(cell.road[k + 1], px, pz, tmp);
            if (tmp[0] > r.half()) {
                continue;
            }
            if (f == lastFeature && lastHit != null) {
                if (tmp[0] < lastHit.a) {
                    out.set(out.size() - 1, new Hit(f, r.kind(), tmp[0], tmp[1], r.half()));
                    lastHit = out.get(out.size() - 1);
                }
                continue;
            }
            lastHit = new Hit(f, r.kind(), tmp[0], tmp[1], r.half());
            lastFeature = f;
            out.add(lastHit);
        }
        return out;
    }

    /** 여러 도로가 겹치는 교차로는 차선 없이 아스팔트만 */
    private Surface combinedRoadSurface(List<Hit> hits) {
        int carriage = 0;
        Hit only = null;
        for (Hit h : hits) {
            if (h.a < carriageHalf(h.kind)) {
                carriage++;
                only = h;
            }
        }
        if (carriage >= 2) {
            return Surface.ASPHALT;
        }
        if (carriage == 1) {
            if (crosswalk(only)) {
                return ((int) Math.floor(only.a)) % 2 == 0 ? Surface.LINE_WHITE : Surface.ASPHALT;
            }
            return roadSurface(only.kind, only.a, only.s);
        }
        return Surface.SIDEWALK;
    }

    /** 교차로 바로 바깥의 횡단보도 자리인지 (차도 위, 도로를 따라 CROSSWALK 칸) */
    private boolean crosswalk(Hit h) {
        if (!hasCrosswalks(h.kind)) {
            return false;
        }
        double[] at = junctionAt[h.road], clear = junctionClear[h.road];
        for (int k = 0; k < at.length; k++) {
            double d = Math.abs(h.s - at[k]);
            if (d >= clear[k] && d < clear[k] + CROSSWALK) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 교차로

    /**
     * 도로끼리 만나는 곳(가로지름, T자, ㄱ자)을 미리 찾아 둡니다. 횡단보도를 그릴 때 씁니다.
     * 같은 방향으로 끝과 끝이 이어진 곳(한 도로를 둘로 나눈 곳)은 교차로가 아닙니다.
     */
    private void buildJunctions() {
        int n = roads.size();
        List<List<double[]>> found = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            found.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                RoadShape a = roads.get(i), b = roads.get(j);
                if ("runway".equals(a.kind()) || "runway".equals(b.kind())) {
                    continue;
                }
                crossings(i, j, found);
                touches(i, j, found);
                touches(j, i, found);
            }
        }
        junctionAt = new double[n][];
        junctionClear = new double[n][];
        for (int i = 0; i < n; i++) {
            List<double[]> list = found.get(i);
            list.sort((p, q) -> Double.compare(p[0], q[0]));
            List<double[]> unique = new ArrayList<>();
            for (double[] j : list) {
                if (unique.isEmpty() || Math.abs(unique.get(unique.size() - 1)[0] - j[0]) > 1) {
                    unique.add(j);
                }
            }
            junctionAt[i] = new double[unique.size()];
            junctionClear[i] = new double[unique.size()];
            for (int k = 0; k < unique.size(); k++) {
                junctionAt[i][k] = unique.get(k)[0];
                junctionClear[i][k] = unique.get(k)[1];
            }
        }
    }

    /** 두 도로의 가운데선이 서로 가로지르는 곳 */
    private void crossings(int i, int j, List<List<double[]>> found) {
        Polyline a = roads.get(i).line(), b = roads.get(j).line();
        for (int p = 0; p < a.segmentCount(); p++) {
            for (int q = 0; q < b.segmentCount(); q++) {
                double ax = a.x(p), az = a.z(p), adx = a.x(p + 1) - ax, adz = a.z(p + 1) - az;
                double bx = b.x(q), bz = b.z(q), bdx = b.x(q + 1) - bx, bdz = b.z(q + 1) - bz;
                double den = adx * bdz - adz * bdx;
                if (Math.abs(den) < 1e-9) {
                    continue;
                }
                double t = ((bx - ax) * bdz - (bz - az) * bdx) / den;
                double u = ((bx - ax) * adz - (bz - az) * adx) / den;
                if (t < 0 || t > 1 || u < 0 || u > 1) {
                    continue;
                }
                double sa = a.along(p) + t * Math.hypot(adx, adz), sb = b.along(q) + u * Math.hypot(bdx, bdz);
                if (isEnd(a, sa) && isEnd(b, sb) && straight(a, sa, b, sb)) {
                    continue; // 끝과 끝이 나란히 이어짐
                }
                found.get(i).add(new double[]{sa, roads.get(j).half() + 1});
                found.get(j).add(new double[]{sb, roads.get(i).half() + 1});
            }
        }
    }

    /** b 의 끝점이 a 위에 닿는 곳 (T자·ㄱ자) */
    private void touches(int ia, int ib, List<List<double[]>> found) {
        RoadShape a = roads.get(ia), b = roads.get(ib);
        double[] out = new double[2];
        Polyline bl = b.line();
        for (int end = 0; end < 2; end++) {
            int k = end == 0 ? 0 : bl.pointCount() - 1;
            a.line().nearest(bl.x(k), bl.z(k), out);
            if (out[0] > a.half()) {
                continue;
            }
            double sb = bl.along(k);
            if (isEnd(a.line(), out[1]) && straight(a.line(), out[1], bl, sb)) {
                continue;
            }
            found.get(ia).add(new double[]{out[1], b.half() + 1});
            found.get(ib).add(new double[]{sb, a.half() + 1});
        }
    }

    private static boolean isEnd(Polyline line, double s) {
        return s < 1 || s > line.length() - 1;
    }

    /** 끝과 끝에서 두 도로의 방향이 거의 같은지 (30° 안) */
    private static boolean straight(Polyline a, double sa, Polyline b, double sb) {
        double[] da = endDirection(a, sa), db = endDirection(b, sb);
        // 한쪽은 끝에서 나가는 방향, 다른 쪽은 들어오는 방향이라 서로 반대여야 이어진 것
        return da[0] * db[0] + da[1] * db[1] < -Math.cos(Math.toRadians(30));
    }

    /** 끝점에서 도로 안쪽으로 향하는 방향 */
    private static double[] endDirection(Polyline line, double s) {
        int n = line.pointCount();
        double dx, dz;
        if (s < line.length() / 2) {
            dx = line.x(1) - line.x(0);
            dz = line.z(1) - line.z(0);
        } else {
            dx = line.x(n - 2) - line.x(n - 1);
            dz = line.z(n - 2) - line.z(n - 1);
        }
        double len = Math.hypot(dx, dz);
        return new double[]{dx / len, dz / len};
    }

    /** 도로 단면: 가운데선에서 거리 a, 도로를 따라 잰 위치 s */
    static Surface roadSurface(String kind, double a, double s) {
        switch (kind) {
            case "highway", "bridge-highway" -> {
                if (a < 1) {
                    return Surface.LINE_YELLOW;              // 중앙선 (2칸)
                }
                if (a >= 12.5 && a < 13.5) {
                    return Surface.LINE_WHITE;               // 바깥 실선
                }
                if ((Math.abs(a - 5) < 0.5 || Math.abs(a - 9) < 0.5) && dash(s, 6, 6)) {
                    return Surface.LINE_WHITE;               // 차선 점선
                }
                return Surface.ASPHALT;
            }
            case "arterial", "bridge" -> {
                if (a >= 8) {
                    return Surface.SIDEWALK;
                }
                if (a < 0.5) {
                    return Surface.LINE_YELLOW;
                }
                if (a >= 7) {
                    return Surface.LINE_WHITE;
                }
                if (Math.abs(a - 4) < 0.5 && dash(s, 6, 6)) {
                    return Surface.LINE_WHITE;
                }
                return Surface.ASPHALT;
            }
            case "street" -> {
                if (a >= 4) {
                    return Surface.SIDEWALK;                 // 인도 3칸
                }
                return a < 0.5 ? Surface.LINE_YELLOW : Surface.ASPHALT;
            }
            case "tunnel" -> {
                if (a >= 4) {
                    return Surface.SIDEWALK;
                }
                return a < 0.5 ? Surface.LINE_YELLOW : Surface.ASPHALT;
            }
            case "runway" -> {
                if (a < 0.75 && dash(s, 30, 20)) {
                    return Surface.LINE_WHITE;
                }
                if (a >= 20.5 && a < 21.5) {
                    return Surface.LINE_WHITE;
                }
                return Surface.ASPHALT;
            }
            default -> {
                return Surface.ASPHALT;
            }
        }
    }

    private static boolean dash(double s, double on, double off) {
        return (s % (on + off)) < on;
    }

    // ------------------------------------------------------------------ 산

    /** 도로 근처를 깎고 채석장을 평평하게 한 최종 산 높이 */
    private int mountainHeight(MountainShape m, double px, double pz, Cell cell, double[] tmp) {
        if (!m.polygon().contains(px, pz)) {
            return 0;
        }
        double h = rawMountain(m, px, pz);
        if (h > 0) {
            h *= roadMask(cell, px, pz, tmp);
        }
        h = flatten(m, px, pz, h);
        return (int) Math.round(Math.max(0, Math.min(MAX_MOUNTAIN, h)));
    }

    /** 도로를 깎지 않은 산 높이 (구역 테두리 쪽은 낮아짐) */
    private double rawMountain(MountainShape m, double px, double pz) {
        double inside = m.polygon().insideDistance(px, pz);
        if (inside <= 0) {
            return 0;
        }
        double base = 0;
        for (Layout.Peak p : m.mountain().peaks()) {
            double u = Math.hypot(px - p.x(), pz - p.z()) / p.radius();
            if (u < 1) {
                base = Math.max(base, p.height() * (Math.cos(Math.PI * u) + 1) / 2);
            }
        }
        if (base <= 0) {
            return 0;
        }
        double variation = 1 + 0.22 * noise.fbm(px / 380, pz / 380, 3);
        double detail = 7 * noise.fbm(px / 70 + 100, pz / 70 + 100, 3);
        double h = (base * variation + detail * Math.min(1, base / 30)) * smooth(0, mountainEdgeFade, inside);
        return Math.max(0, Math.min(MAX_MOUNTAIN, h));
    }

    /** 채석장 같은 평평한 곳 */
    private double flatten(MountainShape m, double px, double pz, double h) {
        for (Layout.Flat f : m.mountain().flats()) {
            double d = Math.hypot(px - f.x(), pz - f.z());
            if (d < f.radius()) {
                double w = 1 - smooth(f.radius() * 0.6, f.radius(), d);
                h = h * (1 - w) + f.height() * w;
            }
        }
        return h;
    }

    /** 도로 근처 0 → 멀어지면 1 */
    private double roadMask(Cell cell, double px, double pz, double[] tmp) {
        double edge = Double.MAX_VALUE;
        for (int k = 0; k < cell.roadMask.length; k += 2) {
            int f = cell.roadMask[k];
            RoadShape r = roads.get(f);
            r.line().distanceToSegment(cell.roadMask[k + 1], px, pz, tmp);
            edge = Math.min(edge, tmp[0] - r.half());
        }
        return edge == Double.MAX_VALUE ? 1 : smooth(roadClear, roadFade, edge);
    }

    /** 터널 위를 덮는 산 높이 (도로로 깎지 않은 높이) */
    private int tunnelCover(double px, double pz, Cell cell, double[] tmp) {
        double h = 0;
        for (int i : cell.mountains) {
            h = Math.max(h, rawMountain(mountains.get(i), px, pz));
        }
        return (int) Math.round(h);
    }

    private Surface mountainSurface(MountainShape m, double px, double pz, int h, Cell cell, double[] tmp) {
        for (Layout.Flat f : m.mountain().flats()) {
            if (Math.hypot(px - f.x(), pz - f.z()) < f.radius() * 0.8) {
                return Surface.ROCK;                       // 채석장 바닥
            }
        }
        if (h > 240) {
            return Surface.ROCK;                           // 높은 봉우리는 바위
        }
        double hx = mountainHeight(m, px + 2, pz, cell, tmp) - mountainHeight(m, px - 2, pz, cell, tmp);
        double hz = mountainHeight(m, px, pz + 2, cell, tmp) - mountainHeight(m, px, pz - 2, cell, tmp);
        double slope = Math.max(Math.abs(hx), Math.abs(hz)) / 4;
        return slope > 1.3 ? Surface.ROCK : Surface.GRASS;
    }

    // ------------------------------------------------------------------ 바이옴 (가볍게)

    /** 생물군계만 빠르게 (도로·거점은 안 봄) */
    public Column.Biome biomeAt(int x, int z) {
        double px = x + 0.5, pz = z + 0.5;
        if (!insideBorder(px, pz)) {
            return Column.Biome.OCEAN;
        }
        Cell cell = cellAt(px, pz);
        boolean island = false;
        for (int i : cell.islands) {
            if (islands.get(i).contains(px, pz)) {
                island = true;
            }
        }
        if (!island && cell.sea && sea != null && sea.contains(px, pz)) {
            return Column.Biome.OCEAN;
        }
        double[] tmp = new double[2];
        for (int k = 0; k < cell.river.length; k += 2) {
            RiverShape r = rivers.get(cell.river[k]);
            r.line().distanceToSegment(cell.river[k + 1], px, pz, tmp);
            if (!island && tmp[0] <= r.half()) {
                return Column.Biome.RIVER;
            }
        }
        for (int i : cell.mountains) {
            if (mountains.get(i).polygon().insideDistance(px, pz) > 80) {
                return Column.Biome.FOREST;
            }
        }
        return Column.Biome.PLAINS;
    }

    // ------------------------------------------------------------------ 도움 함수

    static double smooth(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
