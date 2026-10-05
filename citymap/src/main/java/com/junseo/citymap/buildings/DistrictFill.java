package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 랜드마크를 짓고 남은 동네 땅을 그 동네다운 건물로 빽빽하게 채웁니다.
 * <ul>
 *   <li>블록마다 아파트 단지 또는 도심 블록({@link BlockLayout}: 길가 맞벽 건물 줄, 안쪽 골목, 골목 안 필지).</li>
 *   <li>높이는 길의 위계를 따름: 큰길(대로)변이 가장 높고(업무 구역은 타워), 이면도로변은 중간, 골목 안은 낮게.</li>
 *   <li>구역마다 짓는 방식·층수·건물 종류가 다름 ({@link #STYLES}).</li>
 *   <li>걸어서 닿을 만한 간격(약 200m)마다 공영 차고(공영주차장·주차타워, kind "garage"): 차를 꺼내 타는 곳.</li>
 *   <li>블록 안 자투리는 쌈지공원·놀이터로, 그래도 남는 땅은 작은 필지로 메움.</li>
 * </ul>
 * 청라는 분양 필지로 비워 둡니다 (부동산 회사가 플레이어에게 파는 땅).
 */
final class DistrictFill {
    /**
     * 구역 성격.
     *
     * @param main       큰길변 층수 {최소, 최대} (타워가 아닐 때)
     * @param street     이면도로변 층수
     * @param inner      골목 안 층수
     * @param tower      큰길변 타워 층수 (spec.mainTower 일 때)
     * @param innerHouse 골목 안이 주택이면 true, 상가면 false
     * @param complexPct 블록이 아파트 단지가 될 확률(%)
     * @param apt        아파트 층수
     * @param parkPct    블록 안 쌈지공원 확률(%)
     * @param garageGap  공영 차고 사이 거리
     * @param garageTower 차고를 주차타워로 (땅이 비싼 도심)
     */
    record Style(BlockLayout.Spec spec, int[] main, int[] street, int[] inner, int[] tower, boolean innerHouse,
                 int complexPct, int[] apt, int parkPct, int garageGap, boolean garageTower) {
    }

    private static BlockLayout.Spec spec(int front, int lotMin, int lotMax, boolean mainTower, int innerMin, int innerMax, int innerGap) {
        BlockLayout.Spec s = new BlockLayout.Spec();
        s.front = front;
        s.lotMin = lotMin;
        s.lotMax = lotMax;
        s.mainTower = mainTower;
        s.innerMin = innerMin;
        s.innerMax = innerMax;
        s.innerGap = innerGap;
        return s;
    }

    private static int[] r(int a, int b) {
        return new int[]{a, b};
    }

    static final Map<String, Style> STYLES = Map.ofEntries(
            // 여의도: 대로변 초고층 업무 타워, 이면 오피스텔·근린생활, 아파트 단지(시범아파트)
            Map.entry("yeouido", new Style(spec(18, 12, 18, true, 10, 14, 0), r(8, 12), r(6, 10), r(4, 6), r(25, 45), false, 40, r(12, 13), 15, 220, true)),
            // 중구: 큰길 업무 빌딩, 명동·을지로 맞벽 상가가 골목 안까지 빽빽
            Map.entry("junggu", new Style(spec(14, 8, 13, true, 7, 11, 0), r(6, 10), r(4, 7), r(2, 4), r(15, 28), false, 0, r(0, 0), 10, 200, true)),
            // 용산: 이태원 상가, 언덕 쪽 다가구, 큰길 주상복합
            Map.entry("yongsan", new Style(spec(14, 9, 14, true, 8, 12, 1), r(4, 6), r(3, 5), r(2, 3), r(30, 40), true, 20, r(15, 25), 25, 220, false)),
            // 강남: 테헤란로 유리 타워, 이면 근린생활 5~8층, 골목 안 빌라, 대단지
            Map.entry("gangnam", new Style(spec(16, 12, 18, true, 10, 14, 1), r(8, 12), r(5, 8), r(4, 4), r(20, 40), true, 35, r(25, 35), 25, 220, true)),
            // 송파: 잠실 대단지, 방이동 먹자골목
            Map.entry("songpa", new Style(spec(15, 10, 16, true, 9, 13, 1), r(5, 8), r(3, 5), r(4, 4), r(10, 20), true, 60, r(25, 33), 30, 240, false)),
            // 마포: 공덕 아파트, 망원동 붉은 벽돌 다세대
            Map.entry("mapo", new Style(spec(14, 9, 14, true, 8, 12, 1), r(5, 8), r(3, 5), r(2, 4), r(15, 22), true, 45, r(20, 25), 30, 240, false)),
            // 광진: 건대 먹자골목, 구의·자양 필로티 빌라
            Map.entry("gwangjin", new Style(spec(13, 8, 13, false, 8, 12, 1), r(5, 8), r(3, 5), r(4, 4), r(0, 0), true, 15, r(15, 25), 25, 220, false)),
            // 홍대: 상가 4~6층, 연남동 2층 벽돌집·카페
            Map.entry("hongdae", new Style(spec(13, 8, 13, false, 8, 12, 2), r(5, 7), r(4, 6), r(2, 3), r(0, 0), true, 0, r(0, 0), 25, 220, true)),
            // 대학가: 녹두거리 상가, 원룸 필로티
            Map.entry("university", new Style(spec(13, 8, 12, false, 8, 11, 1), r(4, 6), r(3, 5), r(4, 5), r(0, 0), true, 0, r(0, 0), 20, 220, false)),
            // 남산 아래: 낡은 2~3층 다가구
            Map.entry("namsan", new Style(spec(12, 8, 12, false, 8, 11, 1), r(2, 3), r(2, 3), r(2, 3), r(0, 0), true, 0, r(0, 0), 30, 260, false)),
            // 북한산 아래: 평창동 큰 단독주택, 등산로 입구 식당
            Map.entry("bukhansan", new Style(spec(14, 12, 18, false, 12, 18, 3), r(2, 2), r(2, 2), r(2, 2), r(0, 0), true, 0, r(0, 0), 30, 260, false)));

    /** 채우는 구역 (청라·공항·구인천·대형시장은 따로) */
    static final String[] DISTRICTS = {"yeouido", "junggu", "yongsan", "gangnam", "songpa", "mapo", "gwangjin", "hongdae",
            "university", "namsan", "bukhansan"};

    private static final String[] COMPLEX = {"한빛", "푸른숲", "청솔", "은하수", "무지개", "한마음", "새마을", "솔빛", "다솜", "햇살"};
    private static final Block[][] GLASS = {
            {Block.of("light_blue_stained_glass", 0x6699D8), Block.of("light_gray_concrete", 0x7D7D73), Block.of("gray_concrete", 0x36393D)},
            {Block.of("cyan_stained_glass", 0x4C7F99), Block.of("light_gray_concrete", 0x7D7D73), Block.of("light_gray_concrete", 0x7D7D73)},
            {Block.of("gray_stained_glass", 0x4C4C4C), Block.of("black_concrete", 0x080A0F), Block.of("gray_concrete", 0x36393D)},
            {Block.of("black_stained_glass", 0x191919), Block.of("polished_deepslate", 0x484849), Block.of("polished_deepslate", 0x484849)},
            {Block.of("glass", 0xC8DCE4), Block.of("smooth_stone", 0x9E9E9E), Block.of("white_concrete", 0xCFD5D6)},
            {Block.of("light_blue_stained_glass", 0x6699D8), Block.of("white_concrete", 0xCFD5D6), Block.of("white_concrete", 0xCFD5D6)},
            {Block.of("blue_stained_glass", 0x334CB2), Block.of("polished_andesite", 0x848685), Block.of("polished_andesite", 0x848685)},
    };

    private final CityTerrain t;
    private final String id;
    private final Style st;
    private final BuildMask m;
    private final Random rnd;
    private final MainRoads roads;
    private final List<Placement> out = new ArrayList<>();
    private final List<double[]> garages = new ArrayList<>();
    private final int[] dong = {101};
    private int garageNo = 1;

    private DistrictFill(CityTerrain t, String id, Style st, BuildMask m, List<Placement> existing) {
        this.t = t;
        this.id = id;
        this.st = st;
        this.m = m;
        this.rnd = Plans.random(t, "fill-" + id);
        this.roads = MainRoads.of(t);
        for (Placement p : existing) {
            if (p.kind.equals("garage")) {
                double[] b = p.boundsRef();
                garages.add(new double[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2});
            }
        }
    }

    static List<Placement> fill(CityTerrain t, String id, List<Placement> existing) {
        Polygon area = Plans.district(t, id);
        Style st = STYLES.get(id);
        if (area == null || st == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        GuincheonPlan.claimAll(m, existing.stream().filter(p -> !CityBuildings.isGround(p)).toList());
        DistrictFill f = new DistrictFill(t, id, st, m, existing);
        f.run();
        return f.out;
    }

    private void run() {
        String complex = COMPLEX[rnd.nextInt(COMPLEX.length)];
        for (int[] block : m.blocks()) {
            if (block[4] < 150) {
                continue;
            }
            int bw = block[2] - block[0] + 1, bd = block[3] - block[1] + 1;
            double ratio = block[4] / (double) (bw * bd);
            boolean regular = ratio >= 0.55 && bw >= 26 && bd >= 26;
            if (!regular) {
                continue;
            }
            if (rnd.nextInt(100) < st.complexPct && bw >= 45 && bd >= 40) {
                int before = out.size();
                apartments(block, complex);
                complex = COMPLEX[rnd.nextInt(COMPLEX.length)];
                if (out.size() > before) {
                    continue;
                }
            }
            streetBlock(block);
        }
        // 남은 땅: 골목 안 건물 크기의 작은 필지, 그다음 쌈지공원
        for (int pass = 0; pass < 2; pass++) {
            for (int[] block : m.blocks()) {
                if (block[4] < 80) {
                    continue;
                }
                for (LotPlanner.Lot lot : new LotPlanner(m, rnd, 9, 16, 1, 2).split(block)) {
                    BlockLayout.Lot l = new BlockLayout.Lot(lot.x0(), lot.z0(), lot.x1(), lot.z1(), lot.front(), BlockLayout.Tier.INNER, false, false);
                    place(l, garageWanted(l.cx(), l.cz()) && fitsGarage(l) ? Use.GARAGE : Use.BUILD);
                }
            }
        }
        for (int[] block : m.blocks()) {
            if (block[4] < 49) {
                continue;
            }
            for (LotPlanner.Lot lot : new LotPlanner(m, rnd, 7, 24, 1, 1).split(block)) {
                BlockLayout.Lot l = new BlockLayout.Lot(lot.x0(), lot.z0(), lot.x1(), lot.z1(), lot.front(), BlockLayout.Tier.INNER, false, false);
                place(l, Use.PARK);
            }
        }
    }

    private enum Use { BUILD, PARK, GARAGE }

    /** 도심 블록: 길가 맞벽 줄 + 골목 + 골목 안 필지 */
    private void streetBlock(int[] block) {
        BlockLayout.Plan plan = BlockLayout.plan(m, t, roads, block, st.spec, rnd);
        if (plan.lots().isEmpty()) {
            return;
        }
        // 이 블록의 차고 하나, 쌈지공원 하나
        BlockLayout.Lot garage = null, park = null;
        double cx = (block[0] + block[2]) / 2.0, cz = (block[1] + block[3]) / 2.0;
        if (garageWanted(cx, cz)) {
            for (BlockLayout.Lot l : plan.lots()) {
                if (!l.tower() && fitsGarage(l) && (garage == null || better(l, garage))) {
                    garage = l;
                }
            }
        }
        if (rnd.nextInt(100) < st.parkPct) {
            for (BlockLayout.Lot l : plan.lots()) {
                if (l.tier() == BlockLayout.Tier.INNER && l != garage && l.area() >= 80 && (park == null || l.area() > park.area())) {
                    park = l;
                }
            }
        }
        for (BlockLayout.Lot l : plan.lots()) {
            place(l, l == garage ? Use.GARAGE : l == park ? Use.PARK : Use.BUILD);
        }
        for (int[] a : plan.alleys()) {
            if (m.count(a[0], a[1], a[2], a[3]) * 2 < (a[2] - a[0] + 1) * (a[3] - a[1] + 1)) {
                continue;
            }
            out.add(Placement.rect("골목", "plaza", a[0], a[1], a[2], a[3], "south", DistrictFill::alley));
            m.claim(a[0], a[1], a[2], a[3]);
        }
    }

    /** 차고로 더 나은 필지: 주차타워면 이면도로변, 공영주차장이면 넓은 것 */
    private boolean better(BlockLayout.Lot a, BlockLayout.Lot b) {
        if (st.garageTower) {
            int ta = a.tier() == BlockLayout.Tier.STREET ? 2 : a.tier() == BlockLayout.Tier.INNER ? 1 : 0;
            int tb = b.tier() == BlockLayout.Tier.STREET ? 2 : b.tier() == BlockLayout.Tier.INNER ? 1 : 0;
            return ta != tb ? ta > tb : a.area() < b.area();
        }
        return fitsLot(a) != fitsLot(b) ? fitsLot(a) : a.area() > b.area();
    }

    private boolean fitsGarage(BlockLayout.Lot l) {
        return fitsLot(l) || l.width() >= Garage.TOWER_W && l.depth() >= Garage.TOWER_D;
    }

    /** 공영주차장(노상)이 들어가는 필지 */
    private static boolean fitsLot(BlockLayout.Lot l) {
        return l.width() >= 12 && l.depth() >= 12;
    }

    private boolean garageWanted(double x, double z) {
        for (double[] g : garages) {
            if (Math.hypot(g[0] - x, g[1] - z) < st.garageGap) {
                return false;
            }
        }
        return true;
    }

    /** 필지 하나에 짓고 땅을 표시 */
    private void place(BlockLayout.Lot l, Use use) {
        if (!m.rectFree(l.x0(), l.z0(), l.x1(), l.z1())) {
            return;
        }
        long seed = rnd.nextLong();
        int w = l.width(), d = l.depth();
        Placement p;
        if (use == Use.GARAGE) {
            String dn = t.districtName((int) l.cx(), (int) l.cz());
            String name = (dn == null ? "" : dn + " ") + "제" + garageNo++;
            if (st.garageTower || !fitsLot(l)) {
                int[] rect = towerRect(l);
                p = Placement.rect(name + " 주차타워", "garage", rect[0], rect[1], rect[2], rect[3], l.front(),
                        (bw, bd) -> Garage.tower(4 + (int) (seed & 3), name, new Random(seed)));
            } else {
                p = Placement.rect(name + " 공영주차장", "garage", l.x0(), l.z0(), l.x1(), l.z1(), l.front(),
                        (bw, bd) -> Garage.lot(bw, bd, name, new Random(seed)));
            }
            garages.add(new double[]{l.cx(), l.cz()});
        } else if (use == Use.PARK) {
            boolean play = w >= 11 && d >= 11 && rnd.nextBoolean();
            p = Placement.rect(play ? "어린이 놀이터" : "쌈지공원", "park", l.x0(), l.z0(), l.x1(), l.z1(), l.front(),
                    (bw, bd) -> PocketPark.build(bw, bd, new Random(seed)));
        } else if (l.tower()) {
            int floors = between(st.tower);
            boolean podium = w >= 30 && d >= 28;
            p = Placement.rect(officeName(), "office", l.x0(), l.z0(), l.x1(), l.z1(), l.front(),
                    (bw, bd) -> office(id, bw, bd, floors, podium, new Random(seed)));
        } else {
            BlockLayout.Tier tier = l.tier();
            int floors = between(tier == BlockLayout.Tier.MAIN ? st.main : tier == BlockLayout.Tier.STREET ? st.street : st.inner);
            boolean house = tier == BlockLayout.Tier.INNER && st.innerHouse;
            boolean party = l.partyWall();
            p = house
                    ? Placement.rect(houseName(), "house", l.x0(), l.z0(), l.x1(), l.z1(), l.front(),
                    (bw, bd) -> house(id, bw, bd, floors, new Random(seed)))
                    : Placement.rect(shopName(tier), "shop", l.x0(), l.z0(), l.x1(), l.z1(), l.front(),
                    (bw, bd) -> shop(id, party, bw, bd, floors, new Random(seed)));
        }
        out.add(p);
        double[] b = p.boundsRef();
        m.claim((int) Math.floor(b[0]), (int) Math.floor(b[1]), (int) Math.ceil(b[2]) - 1, (int) Math.ceil(b[3]) - 1);
    }

    /** 주차타워 자리: 필지 정면 가운데에 붙인 TOWER_W × TOWER_D */
    private static int[] towerRect(BlockLayout.Lot l) {
        int W = Garage.TOWER_W, D = Garage.TOWER_D;
        return switch (l.front()) {
            case "south" -> {
                int x0 = (l.x0() + l.x1() + 1 - W) / 2;
                yield new int[]{x0, l.z1() - D + 1, x0 + W - 1, l.z1()};
            }
            case "north" -> {
                int x0 = (l.x0() + l.x1() + 1 - W) / 2;
                yield new int[]{x0, l.z0(), x0 + W - 1, l.z0() + D - 1};
            }
            case "east" -> {
                int z0 = (l.z0() + l.z1() + 1 - W) / 2;
                yield new int[]{l.x1() - D + 1, z0, l.x1(), z0 + W - 1};
            }
            default -> {
                int z0 = (l.z0() + l.z1() + 1 - W) / 2;
                yield new int[]{l.x0(), z0, l.x0() + D - 1, z0 + W - 1};
            }
        };
    }

    private int between(int[] range) {
        return range[0] + rnd.nextInt(Math.max(1, range[1] - range[0] + 1));
    }

    // ------------------------------------------------------------------ 건물 (구역별 건축은 각 건물 클래스가 맡음)

    static Voxels shop(String district, boolean partyWall, int w, int d, int floors, Random r) {
        return ShopHouse.build(w, d, r, floors >= 4 ? ShopHouse.Style.COMMERCIAL : ShopHouse.Style.MIXED);
    }

    static Voxels house(String district, int w, int d, int floors, Random r) {
        return ShopHouse.build(w, d, r, ShopHouse.Style.VILLA);
    }

    /** 사무 빌딩 (앞 공개공지 3칸, 둘레 2칸 띄움). podium 이면 아래 4층은 땅을 더 채움 */
    static Voxels office(String district, int w, int d, int floors, boolean podium, Random r) {
        Block[] skin = GLASS[r.nextInt(GLASS.length)];
        boolean setback = floors >= 18 && r.nextBoolean();
        Tower.Spec s = new Tower.Spec(w, d, floors);
        int top = floors - 3;
        s.shape = (i, j, k) -> {
            int in = podium && k < 4 ? 1 : setback && k >= top ? 5 : podium ? 4 : 2;
            return i >= in && i < w - in && j >= in && j < d - Math.max(in, 3);
        };
        s.glass = skin[0];
        s.mullion = skin[1];
        s.spandrel = skin[2];
        s.lobbyH = floors >= 15 ? Floors.HALL : Floors.GROUND;
        s.elevators = w >= 44 ? 2 : 1;
        s.restrooms = w >= 40 ? 2 : 1;
        s.helipad = floors >= 20;
        s.name = KoreanNames.prefix(r) + (r.nextBoolean() ? "빌딩" : "타워");
        Tower tw = Tower.build(s, r);
        tw.v.fill(0, -1, 0, w - 1, -1, d - 1, LIGHT_GRAY_CONCRETE);
        for (int i = 1; i < w - 1; i += 6) {
            if (tw.v.get(i, 0, d - 1) == null) {
                MarketPlan.lampPost(tw.v, i, d - 1);
            }
        }
        return tw.v;
    }

    private String officeName() {
        return KoreanNames.prefix(rnd) + (rnd.nextBoolean() ? "빌딩" : "타워");
    }

    private String houseName() {
        return "다세대 주택";
    }

    private String shopName(BlockLayout.Tier tier) {
        return tier == BlockLayout.Tier.INNER ? "골목 상가" : "상가 건물";
    }

    /** 골목 바닥: 시멘트 포장, 가운데 배수 줄 */
    static Voxels alley(int w, int d) {
        Voxels v = new Voxels(w, d, -1, 0);
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("andesite", 0x888888));
        if (w <= d) {
            v.fill(w / 2, -1, 0, w / 2, -1, d - 1, SMOOTH_STONE);
        } else {
            v.fill(0, -1, d / 2, w - 1, -1, d / 2, SMOOTH_STONE);
        }
        return v;
    }

    // ------------------------------------------------------------------ 아파트 단지

    /** 아파트 단지: 남향 동을 줄지어 세우고, 동 사이는 주차장 */
    private void apartments(int[] block, String complex) {
        int depth = Apartment.depth(), gap = 12;
        for (int z = block[1]; z + depth - 1 <= block[3]; z += depth + gap) {
            int x = block[0];
            while (x + 30 <= block[2]) {
                int units = Math.min(6, Apartment.unitsFor(block[2] - x + 1));
                int w = Apartment.CORE + units * Apartment.UNIT + 1;
                int x1 = x + w - 1, z1 = z + depth - 1;
                if (x1 <= block[2] && m.rectFree(x, z, x1, z1)) {
                    int floors = between(st.apt);
                    int no = dong[0]++;
                    long seed = rnd.nextLong();
                    out.add(Placement.rect(complex + "아파트 " + no + "동", "apartment", x, z, x1, z1, "south",
                            (bw, bd) -> Apartment.build(bw, bd, units, floors, no, complex + "아파트", new Random(seed))));
                    m.claim(x - 1, z - 1, x1 + 1, z1 + 1);
                    // 동 앞 주차장 (다음 줄과 사이)
                    int pz0 = z1 + 2, pz1 = z1 + gap - 1;
                    if (pz1 - pz0 >= 8 && m.rectFree(x, pz0, x1, pz1)) {
                        long ps = rnd.nextLong();
                        out.add(Placement.rect("아파트 주차장", "parking", x, pz0, x1, pz1, "south", (pw, pd) -> ParkingLot.build(pw, pd, new Random(ps))));
                        m.claim(x - 1, pz0 - 1, x1 + 1, pz1 + 1);
                    }
                    x = x1 + 8;
                } else {
                    x += 4;
                }
            }
        }
    }

    private DistrictFill() {
        throw new UnsupportedOperationException();
    }
}
