package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 용산: 이태원 거리 (경찰서는 강남 한 곳뿐이라 용산에는 두지 않음 [확정: 운영자]).
 * <ul>
 *   <li>이태원 거리: 용산 북쪽 블록을 남북으로 지나는 큰길(세종대로) 양옆에 3~5층 건물을 빽빽이 세웁니다.
 *       케밥·할랄·타코·버거·커리 식당, 펍과 와인 바, 옥상 바, 맞춤 양복점, 빅사이즈 옷가게, 외국 식품점,
 *       간판은 한글과 영어(「Kebab」「Pub」「Halal Food」 …). 동쪽 가운데에 호텔과 작은 광장(거점 자리 제안)</li>
 * </ul>
 */
final class YongsanPlan {
    /** 이태원 거리가 지나는 도로 */
    static final String ITAEWON_ROAD = "A4";
    private static final int LOT_DEPTH = 18;

    private static StreetBuilding.Shop s(StreetBuilding.Use u, String name, String sub) {
        return new StreetBuilding.Shop(u, name, sub);
    }

    private static StreetBuilding.Shop s(StreetBuilding.Use u, String name, String sub, String food) {
        return new StreetBuilding.Shop(u, name, sub, food);
    }

    private record Lot(StreetBuilding.Shop ground, StreetBuilding.Shop[] upper, String rooftop, int skin, int width) {
    }

    private static final StreetBuilding.Use FOOD = StreetBuilding.Use.RESTAURANT, BAR = StreetBuilding.Use.BAR,
            CAFE = StreetBuilding.Use.CAFE, STORE = StreetBuilding.Use.STORE, CLOTHES = StreetBuilding.Use.CLOTHES,
            TAILOR = StreetBuilding.Use.TAILOR, OFFICE = StreetBuilding.Use.OFFICE;

    /** 서쪽 줄 (북쪽부터, 정면 동쪽) */
    private static final Lot[] WEST = {
            new Lot(s(FOOD, "할랄 키친", "Halal Food", "halal"),
                    new StreetBuilding.Shop[]{s(FOOD, "네팔 커리", "Nepal Curry", "curry"), s(BAR, "아이리시 펍", "Irish Pub", "pub")},
                    null, 0, 13),
            new Lot(s(CLOTHES, "빅사이즈 옷", "Big Size"),
                    new StreetBuilding.Shop[]{s(TAILOR, "맞춤 양복", "Custom Suits"), s(FOOD, "그리스 식당", "Greek Food")},
                    null, 6, 12),
            new Lot(s(BAR, "스포츠 펍", "Sports Pub", "pub"),
                    new StreetBuilding.Shop[]{s(BAR, "재즈 바", "Jazz Bar", "lounge"), s(FOOD, "멕시칸 식당", "Mexican", "taco")},
                    "루프탑 펍", 7, 14),
            new Lot(s(FOOD, "케밥 & 피타", "Doner Kebab", "kebab"),
                    new StreetBuilding.Shop[]{s(OFFICE, "환전·여행사", "Exchange"), s(OFFICE, "외국어 학원", "Language")},
                    null, 4, 12),
            new Lot(s(CAFE, "베이커리 카페", "Bakery"),
                    new StreetBuilding.Shop[]{s(FOOD, "태국 음식", "Thai Food"), s(FOOD, "스테이크하우스", "Steak House"),
                            s(BAR, "라운지 바", "Lounge Bar", "lounge"), s(BAR, "재즈 클럽", "Jazz Club", "lounge")},
                    null, 1, 14),
            new Lot(s(STORE, "외국 식품점", "World Foods"),
                    new StreetBuilding.Shop[]{s(FOOD, "아랍 식당", "Arabic Food", "halal"), s(BAR, "와인 바", "Wine Bar", "wine")},
                    "옥상 테라스", 8, 0),
    };

    /** 동쪽 줄 북쪽 (광장보다 북쪽) */
    private static final Lot[] EAST_NORTH = {
            new Lot(s(FOOD, "이스탄불 케밥", "Kebab", "kebab"),
                    new StreetBuilding.Shop[]{s(BAR, "런던 펍", "British Pub", "pub"), s(FOOD, "인디아 커리", "Indian Curry", "curry")},
                    "루프탑 바", 0, 13),
            new Lot(s(FOOD, "버거 스탠드", "Burger", "burger"),
                    new StreetBuilding.Shop[]{s(BAR, "와인 바", "Wine Bar", "wine"), s(TAILOR, "이태원 양복점", "Tailor Shop"),
                            s(OFFICE, "무역 사무소", "Trading Co.")},
                    null, 5, 12),
    };

    /** 동쪽 줄 남쪽 (호텔과 큰길 사이) */
    private static final Lot EAST_SOUTH = new Lot(s(FOOD, "타코 칸티나", "Taco", "taco"),
            new StreetBuilding.Shop[]{s(BAR, "칵테일 라운지", "Cocktail", "lounge"), s(CAFE, "브런치 카페", "Brunch")},
            "스카이 테라스", 4, 0);

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "yongsan");
        if (area == null) {
            return List.of();
        }
        List<Placement> out = new ArrayList<>();
        Random rnd = Plans.random(t, "yongsan-landmarks");
        itaewon(t, area, rnd, out);
        return out;
    }

    /** 이태원 거리: 도로의 남북 구간 양옆 (구역 북쪽 끝 ~ 큰길 앞) */
    private static void itaewon(CityTerrain t, Polygon area, Random rnd, List<Placement> out) {
        Layout.Road road = Plans.road(t, ITAEWON_ROAD);
        if (road == null) {
            return;
        }
        // 도로가 구역 안을 지나는 남북 구간의 가운데 점
        double rx = Double.NaN, rz = Double.NaN;
        for (int k = 0; k + 1 < road.line().size() && Double.isNaN(rx); k++) {
            double[] a = road.line().get(k), b = road.line().get(k + 1);
            for (double f = 0; f <= 1; f += 0.01) {
                double x = a[0] + (b[0] - a[0]) * f, z = a[1] + (b[1] - a[1]) * f;
                if (area.contains(x + 12, z) && area.contains(x - 14, z) && area.contains(x + 12, z - 30) && area.contains(x + 12, z + 30)) {
                    rx = x;
                    rz = z;
                    break;
                }
            }
        }
        if (Double.isNaN(rx)) {
            return;
        }
        int cx = (int) Math.floor(rx), cz = (int) Math.floor(rz);
        // 남쪽 끝: 큰길 앞 (길에서 떨어진 땅에서 잼), 북쪽 끝: 구역 경계
        int zs = HongdaePlan.edge(t, cx + 20, cz, 0, 1) - 1;
        // 길가 건물 앞선: 도로가 조금씩 비스듬하므로 구간 전체에서 가장 바깥
        int xw = Integer.MAX_VALUE, xe = Integer.MIN_VALUE;
        for (int z = cz - 80; z <= zs; z++) {
            xw = Math.min(xw, beyondRoad(t, cx, z, -1));
            xe = Math.max(xe, beyondRoad(t, cx, z, 1));
        }
        int zn = cz;
        while (area.contains(xe + 0.5, zn - 1 + 0.5) && area.contains(xw - LOT_DEPTH + 0.5, zn - 1 + 0.5)
                && area.contains(xe + 34.5, zn - 1 + 0.5) && zn - 1 > cz - 200) {
            zn--;
        }
        zn += 1;
        if (zs - zn < 70) {
            return;
        }
        // 서쪽 줄 (정면 동쪽)
        int z = zn;
        for (Lot lot : WEST) {
            int width = lot.width() > 0 ? lot.width() : zs - z + 1;
            int z1 = Math.min(zs, z + width - 1);
            if (z1 - z + 1 < 10) {
                break;
            }
            add(t, area, out, rnd, lot, xw - LOT_DEPTH + 1, z, xw, z1, "east");
            z = z1 + 1;
        }
        // 동쪽 줄 (정면 서쪽): 건물 둘, 광장, 호텔, 건물
        z = zn;
        for (Lot lot : EAST_NORTH) {
            int z1 = z + lot.width() - 1;
            add(t, area, out, rnd, lot, xe, z, xe + LOT_DEPTH - 1, z1, "west");
            z = z1 + 1;
        }
        int pz0 = z, pz1 = z + 11;
        if (PoliceStation.landOk(t, area, xe, pz0, xe + 13, pz1)) {
            long seed = rnd.nextLong();
            out.add(Placement.rect("이태원 광장", "plaza", xe, pz0, xe + 13, pz1, "west", (w, d) -> plaza(w, d, new Random(seed))));
        }
        int hz0 = pz1 + 1, hz1 = hz0 + 25, hx1 = xe + 33;
        if (PoliceStation.landOk(t, area, xe, hz0, hx1, hz1)) {
            long seed = rnd.nextLong();
            out.add(Placement.rect("준서호텔", "hotel", xe, hz0, hx1, hz1, "west", (w, d) -> hotel(w, d, new Random(seed))));
        }
        if (zs - hz1 >= 10) {
            Lot e = EAST_SOUTH;
            add(t, area, out, rnd, new Lot(e.ground(), e.upper(), e.rooftop(), e.skin(), zs - hz1), xe, hz1 + 1, xe + LOT_DEPTH - 1, zs, "west");
        }
    }

    /** 도로 가운데 (x, z) 에서 dx 쪽으로 나가 처음 만나는 길 아닌 칸의 x */
    private static int beyondRoad(CityTerrain t, int x, int z, int dx) {
        for (int s = 0; s < 60; s++) {
            com.junseo.citymap.terrain.Column c = t.column(x + dx * s, z);
            if (!(c.isRoad() || c.isWater() || c.deck || c.tunnel)) {
                return x + dx * s;
            }
        }
        return x + dx * 60;
    }

    private static void add(CityTerrain t, Polygon area, List<Placement> out, Random rnd, Lot lot,
                            int x0, int z0, int x1, int z1, String front) {
        if (!PoliceStation.landOk(t, area, x0, z0, x1, z1)) {
            return;
        }
        StreetBuilding.Spec spec = new StreetBuilding.Spec();
        spec.ground = lot.ground();
        spec.upper = lot.upper();
        spec.rooftop = lot.rooftop();
        spec.skin = lot.skin();
        spec.coreLeft = rnd.nextBoolean();
        long seed = rnd.nextLong();
        out.add(Placement.rect(lot.ground().name() + " 건물", "restaurant", x0, z0, x1, z1, front,
                (w, d) -> StreetBuilding.build(w, d, new Random(seed), spec)));
    }

    /** 이태원 호텔: 9층, 로비 1층(5칸)과 객실 층, 앞에 차 대는 곳과 차양, 1970~80년대 호텔처럼 흰 띠와 짙은 유리 */
    static Voxels hotel(int w, int d, Random r) {
        Tower.Spec s = new Tower.Spec(w, d, 9);
        s.lobbyH = Floors.GROUND;
        s.use = k -> k == 0 ? Tower.Use.LOBBY : Tower.Use.HOTEL;
        s.shape = (i, j, k) -> j < d - 4;
        s.glass = Block.of("brown_stained_glass", 0x664C33);
        s.mullion = WHITE_TERRACOTTA;
        s.spandrel = WHITE_TERRACOTTA;
        s.podium = POLISHED_GRANITE;
        s.mullionEvery = 2;
        s.elevators = 1;
        s.restrooms = 1;
        s.name = "준서호텔";
        s.directory = new String[]{"준서호텔", "JUNSEO HOTEL", "프런트 1F", "객실 2F~9F"};
        Tower t = Tower.build(s, r);
        Voxels v = t.v;
        // 앞 차 대는 곳 포장과 화분
        v.fill(0, -1, d - 4, w - 1, -1, d - 1, POLISHED_ANDESITE);
        for (int i = 1; i < w - 1; i += 5) {
            if (v.get(i, 0, d - 1) == null) {
                v.set(i, 0, d - 1, Block.of("potted_azalea_bush", 0x63753A));
            }
        }
        // 옥상 세로 간판 틀 (호텔 이름, 표지판)
        int top = t.roofLevel;
        int mid = w / 2;
        v.fill(mid - 3, top, d - 6, mid + 3, top + 3, d - 6, WHITE_TERRACOTTA);
        v.set(mid - 1, top + 2, d - 5, Blocks.wallSign("dark_oak", "south", "white", true, "", "준서호텔"));
        v.set(mid + 1, top + 2, d - 5, Blocks.wallSign("dark_oak", "south", "white", true, "", "HOTEL"));
        // 객실 층 정면 위 돌출 간판
        v.set(0, 7, d - 6, Blocks.wallHangingSign("dark_oak", "west", "white", true, "", "준서호텔", "HOTEL"));
        return v;
    }

    /** 이태원 광장: 포장, 가장자리 나무·의자·보행등, 관광 안내판 (가운데는 비워 둠, 거점 자리) */
    static Voxels plaza(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 9);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                v.set(i, -1, j, (i + j) % 4 == 0 ? Block.of("stone_bricks", 0x7A7979) : Block.of("smooth_stone", 0x9E9E9E));
            }
        }
        // 뒤쪽(안쪽) 나무 줄과 의자
        for (int i = 2; i < w - 1; i += 4) {
            MarketPlan.tree(v, i, 1);
            if (i + 2 < w - 1) {
                v.set(i + 2, 0, 1, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430));
            }
        }
        MarketPlan.lampPost(v, 0, d / 2);
        MarketPlan.lampPost(v, w - 1, d / 2);
        v.fill(w - 1, 0, 3, w - 1, 1, 3, StreetPlan.POST);
        v.set(w - 1, 2, 3, Blocks.wallSign("dark_oak", "south", "white", true, "이태원", "관광 안내", "Itaewon", "Tourist Info"));
        v.connect();
        return v;
    }

    private YongsanPlan() {
    }
}
