package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 홍대 클럽 거리: 클럽 거리 거점(club)이 있는 블록 한가운데를 남북으로 지나는 걷는 길(차 없는 거리)을 내고,
 * 양옆에 3~5층 건물을 빽빽이 세웁니다. 지하 클럽(B1·B2 댄스 플로어), 라이브 공연장, PC방, 편의점, 카페,
 * 포차·고기집, 코인노래방, 옷가게. 거점 옆은 걷고싶은거리 같은 버스킹 자리(낮은 무대와 앉는 단).
 * 블록의 나머지 땅은 동네 채우기가 상가·빌라로 채웁니다.
 */
final class HongdaePlan {
    /** 걷는 길 폭 (거점 가운데 ± 5) */
    private static final int HALF_STREET = 5;
    /** 길가 건물 깊이 */
    private static final int LOT_DEPTH = 20;

    private static StreetBuilding.Shop s(StreetBuilding.Use u, String name, String sub) {
        return new StreetBuilding.Shop(u, name, sub);
    }

    private static StreetBuilding.Shop s(StreetBuilding.Use u, String name, String sub, String food) {
        return new StreetBuilding.Shop(u, name, sub, food);
    }

    /** 건물 한 채의 가게들: {지하(아래부터), 1층, 위층들}, 옥상 바, 바깥 재료, 폭 */
    private record Lot(StreetBuilding.Shop[] basement, StreetBuilding.Shop ground, StreetBuilding.Shop[] upper,
                       String rooftop, int skin, int width) {
    }

    private static final StreetBuilding.Use CLUB = StreetBuilding.Use.CLUB, LIVE = StreetBuilding.Use.LIVE,
            CAFE = StreetBuilding.Use.CAFE, BAR = StreetBuilding.Use.BAR, PC = StreetBuilding.Use.PC,
            STORE = StreetBuilding.Use.STORE, NORAE = StreetBuilding.Use.NORAE, CLOTHES = StreetBuilding.Use.CLOTHES,
            FOOD = StreetBuilding.Use.RESTAURANT, OFFICE = StreetBuilding.Use.OFFICE;

    private static final StreetBuilding.Shop[] NONE = {};

    /** 서쪽 줄 (북쪽부터) */
    private static final Lot[] WEST = {
            new Lot(new StreetBuilding.Shop[]{s(CLUB, "클럽 노바", "CLUB NOVA"), s(CLUB, "클럽 노바", "CLUB NOVA")},
                    s(STORE, "24시 편의점", "24h Store"),
                    new StreetBuilding.Shop[]{s(PC, "준서 PC방", "PC Cafe"), s(NORAE, "코인노래방", "Coin Karaoke"),
                            s(CAFE, "보드게임카페", "Board Game")}, null, 3, 16),
            new Lot(NONE, s(FOOD, "홍대포차", "Pocha", "pocha"),
                    new StreetBuilding.Shop[]{s(FOOD, "연탄 고기집", "Korean BBQ", "bbq"), s(BAR, "와인 바", "Wine Bar", "wine")},
                    null, 0, 13),
            new Lot(new StreetBuilding.Shop[]{s(LIVE, "라이브홀 소리", "LIVE HALL")}, s(CAFE, "카페 소리", "Coffee"),
                    new StreetBuilding.Shop[]{s(CLOTHES, "빈티지 옷가게", "Vintage"), s(OFFICE, "실용음악학원", "Music Academy")},
                    "루프탑 홍대", 2, 16),
            new Lot(NONE, s(CLOTHES, "스트릿 패션", "Street Fashion"),
                    new StreetBuilding.Shop[]{s(CAFE, "디저트 카페", "Dessert"), s(NORAE, "노래연습장", "Karaoke")},
                    null, 5, 12),
            new Lot(new StreetBuilding.Shop[]{s(CLUB, "클럽 블랙", "CLUB BLACK")}, s(BAR, "크래프트 펍", "Craft Beer", "pub"),
                    new StreetBuilding.Shop[]{s(FOOD, "라멘집", "Ramen"), s(FOOD, "닭갈비", "Dakgalbi", "bbq")},
                    null, 3, 14),
            new Lot(NONE, s(STORE, "화장품 가게", "Cosmetics"),
                    new StreetBuilding.Shop[]{s(CAFE, "카페 2층", "Cafe"), s(CLOTHES, "편집숍", "Select Shop")},
                    null, 1, 13),
    };

    /** 동쪽 줄 북쪽 (버스킹 자리보다 북쪽, 북쪽부터) */
    private static final Lot[] EAST = {
            new Lot(NONE, s(FOOD, "떡볶이", "Tteokbokki", "pocha"),
                    new StreetBuilding.Shop[]{s(PC, "레벨업 PC방", "PC Cafe"), s(BAR, "라운지 바", "Lounge Bar", "lounge")},
                    null, 4, 13),
            new Lot(new StreetBuilding.Shop[]{s(LIVE, "언더그라운드", "LIVE CLUB")}, s(CAFE, "에스프레소 바", "Espresso"),
                    new StreetBuilding.Shop[]{s(CLOTHES, "액세서리", "Accessory"), s(NORAE, "코인노래방", "Coin Karaoke")},
                    null, 0, 13),
            new Lot(new StreetBuilding.Shop[]{s(CLUB, "클럽 문", "CLUB MOON"), s(CLUB, "클럽 문", "CLUB MOON")},
                    s(BAR, "칵테일 바", "Cocktail Bar", "lounge"),
                    new StreetBuilding.Shop[]{s(FOOD, "실내포차", "Pocha", "pocha"), s(BAR, "펍 홍대", "Pub", "pub")},
                    null, 3, 15),
            new Lot(NONE, s(FOOD, "수제버거", "Burger", "burger"),
                    new StreetBuilding.Shop[]{s(CAFE, "북카페", "Book Cafe"), s(OFFICE, "사진 스튜디오", "Photo Studio")},
                    "옥상 펍", 6, 12),
    };

    /** 동쪽 줄 남쪽 (버스킹 자리와 큰길 사이) */
    private static final Lot SOUTH_EAST = new Lot(NONE, s(STORE, "24시 편의점", "24h Store"),
            new StreetBuilding.Shop[]{s(PC, "홍대 PC방", "PC Cafe"), s(NORAE, "코인노래방", "Coin Karaoke"), s(CAFE, "카페 루프", "Cafe")},
            "루프탑 바", 2, 0);

    static List<Placement> plan(CityTerrain t) {
        Polygon area = Plans.district(t, "hongdae");
        Layout.Hub hub = Plans.hub(t, "club");
        if (area == null || hub == null) {
            return List.of();
        }
        Random rnd = Plans.random(t, "hongdae-club");
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        // 거점이 있는 블록의 땅 끝 (도로 바로 안쪽)
        int bx0 = edge(t, hx, hz, -1, 0), bx1 = edge(t, hx, hz, 1, 0);
        int bz0 = edge(t, hx, hz, 0, -1), bz1 = edge(t, hx, hz, 0, 1);
        if (hx - HALF_STREET - LOT_DEPTH < bx0 || hx + HALF_STREET + LOT_DEPTH > bx1 || bz1 - bz0 < 60) {
            return List.of();
        }
        List<Placement> out = new ArrayList<>();
        int sx0 = hx - HALF_STREET, sx1 = hx + HALF_STREET;
        // 걷는 길 (남북으로 블록을 가로지름, 양 끝이 인도와 이어짐)
        int busk0 = hz - 11, busk1 = hz + 5;
        out.add(Placement.rect("홍대 클럽 거리", "plaza", sx0, bz0, sx1, bz1, "south",
                (w, d) -> street(w, d, hz - bz0)));
        // 서쪽 줄 (정면 동쪽)
        int z = bz0;
        int wx0 = sx0 - LOT_DEPTH, wx1 = sx0 - 1;
        for (Lot lot : WEST) {
            int z1 = Math.min(bz1, z + lot.width() - 1);
            if (z1 - z + 1 < 10) {
                break;
            }
            add(t, area, out, rnd, lot, wx0, z, wx1, z1, "east", lot.width() >= 14);
            z = z1 + 1;
        }
        if (bz1 - z + 1 >= 10) {
            add(t, area, out, rnd, new Lot(NONE, s(CAFE, "카페 홍대", "Cafe"),
                    new StreetBuilding.Shop[]{s(CLOTHES, "옷가게", "Fashion")}, null, 7, bz1 - z + 1), wx0, z, wx1, bz1, "east", false);
        }
        // 동쪽 줄 (정면 서쪽): 북쪽 건물들, 거점 옆 버스킹 자리, 남쪽 건물
        int ex0 = sx1 + 1, ex1 = sx1 + LOT_DEPTH;
        z = bz0;
        for (Lot lot : EAST) {
            int z1 = Math.min(busk0 - 1, z + lot.width() - 1);
            if (z1 - z + 1 < 10) {
                break;
            }
            add(t, area, out, rnd, lot, ex0, z, ex1, z1, "west", false);
            z = z1 + 1;
        }
        if (busk0 - z >= 10) {
            add(t, area, out, rnd, new Lot(NONE, s(FOOD, "분식집", "Snack Bar", "pocha"),
                    new StreetBuilding.Shop[]{s(CAFE, "만화카페", "Comic Cafe")}, null, 8, busk0 - z), ex0, z, ex1, busk0 - 1, "west", false);
        }
        if (PoliceStation.landOk(t, area, ex0, busk0, ex1, busk1)) {
            long seed = rnd.nextLong();
            out.add(Placement.rect("버스킹 존", "plaza", ex0, busk0, ex1, busk1, "west", (w, d) -> busking(w, d, new Random(seed))));
        }
        if (bz1 - busk1 >= 10) {
            Lot se = SOUTH_EAST;
            add(t, area, out, rnd, new Lot(se.basement(), se.ground(), se.upper(), se.rooftop(), se.skin(), bz1 - busk1),
                    ex0, busk1 + 1, ex1, bz1, "west", true);
        }
        return out;
    }

    /** (x, z) 에서 (dx, dz) 쪽으로 가다가 도로·물 바로 앞 칸 */
    static int edge(CityTerrain t, int x, int z, int dx, int dz) {
        for (int s = 1; s < 200; s++) {
            com.junseo.citymap.terrain.Column c = t.column(x + dx * s, z + dz * s);
            if (c.isRoad() || c.isWater() || c.deck || c.tunnel) {
                return dx != 0 ? x + dx * (s - 1) : z + dz * (s - 1);
            }
        }
        return dx != 0 ? x + dx * 199 : z + dz * 199;
    }

    private static void add(CityTerrain t, Polygon area, List<Placement> out, Random rnd, Lot lot,
                            int x0, int z0, int x1, int z1, String front, boolean sideWindows) {
        if (!PoliceStation.landOk(t, area, x0, z0, x1, z1)) {
            return;
        }
        StreetBuilding.Spec spec = new StreetBuilding.Spec();
        spec.basement = lot.basement();
        spec.ground = lot.ground();
        spec.upper = lot.upper();
        spec.rooftop = lot.rooftop();
        spec.skin = lot.skin();
        spec.coreLeft = rnd.nextBoolean();
        spec.sideWindows = sideWindows;
        long seed = rnd.nextLong();
        String name = lot.basement().length > 0 ? lot.basement()[0].name() + " 건물" : lot.ground().name() + " 건물";
        out.add(Placement.rect(name, "nightlife", x0, z0, x1, z1, front, (w, d) -> StreetBuilding.build(w, d, new Random(seed), spec)));
    }

    /**
     * 걷는 길 바닥: 회색·붉은 벽돌 포장(가운데 줄무늬), 양옆 보행등과 작은 화분, 길 위로 건너지른 줄 조명.
     * hubJ = 거점 줄 (그 둘레는 비움).
     */
    static Voxels street(int w, int d, int hubJ) {
        Voxels v = new Voxels(w, d, -1, 9);
        int mid = w / 2;
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                Block b;
                if (Math.abs(i - mid) <= 1) {
                    b = j % 4 == 0 ? Block.of("polished_andesite", 0x848685) : Block.of("bricks", 0x966153);
                } else {
                    b = (i + 2 * j) % 5 == 0 ? Block.of("stone_bricks", 0x7A7979) : Block.of("light_gray_concrete", 0x7D7D73);
                }
                v.set(i, -1, j, b);
            }
        }
        for (int j = 6; j < d - 3; j += 12) {
            if (Math.abs(j - hubJ) <= 4) {
                continue;
            }
            // 보행등 (양쪽 가장자리, 엇갈리게)
            MarketPlan.lampPost(v, 0, j);
            if (j + 6 < d - 3 && Math.abs(j + 6 - hubJ) > 4) {
                MarketPlan.lampPost(v, w - 1, j + 6);
            }
        }
        // 길가 화분 몇 개 (가장자리)
        for (int j = 3; j < d - 3; j += 9) {
            if (Math.abs(j - hubJ) > 4 && v.get(0, 0, j) == null) {
                v.set(0, 0, j, Block.of("potted_azalea_bush", 0x63753A));
            }
        }
        // 이정표 (북쪽·남쪽 입구)
        v.fill(1, 0, d - 2, 1, 1, d - 2, StreetPlan.POST);
        v.set(1, 2, d - 2, Blocks.wallSign("dark_oak", "south", "white", true, "", "홍대 클럽 거리", "Club Street"));
        v.fill(w - 2, 0, 1, w - 2, 1, 1, StreetPlan.POST);
        v.set(w - 2, 2, 1, Blocks.wallSign("dark_oak", "north", "white", true, "", "홍대 클럽 거리", "Club Street"));
        v.connect();
        return v;
    }

    /**
     * 버스킹 자리 (길 동쪽, 거점 옆): 뒤쪽에 낮은 무대(반 칸 단 두 개), 무대 앞 비운 마당, 옆에 계단식 앉는 단,
     * 가장자리 나무와 보행등, 「버스킹 존」 안내판. 정면(남쪽 = 길 쪽)이 열려 있음.
     */
    static Voxels busking(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 9);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                v.set(i, -1, j, (i + j) % 6 == 0 ? Block.of("bricks", 0x966153) : Block.of("light_gray_concrete", 0x7D7D73));
            }
        }
        int mid = w / 2;
        // 무대: 뒤쪽 가운데 (반 칸 + 한 칸 높이 단)
        int s0 = mid - 4, s1 = mid + 4;
        v.fill(s0, 0, 1, s1, 0, 4, Block.of("polished_andesite", 0x848685));
        v.fill(s0, 0, 5, s1, 0, 5, Block.of("polished_andesite_slab[type=bottom,waterlogged=false]", 0x848685));
        v.fill(s0, 1, 0, s1, 3, 0, Block.of("dark_oak_planks", 0x432B14)); // 무대 뒷벽
        v.set(mid, 2, 1, Blocks.wallSign("dark_oak", "south", "white", true, "걷고싶은거리", "버스킹 존", "Busking Zone"));
        // 앰프와 마이크 스탠드
        v.set(s0 + 1, 1, 2, Block.of("black_concrete", 0x080A0F));
        v.set(s1 - 1, 1, 2, Block.of("black_concrete", 0x080A0F));
        v.set(mid, 1, 3, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0x5A5A5A));
        // 무대 조명 기둥과 지붕 틀
        for (int i : new int[]{s0, s1}) {
            v.fill(i, 1, 1, i, 5, 1, StreetPlan.POST);
        }
        v.fill(s0, 6, 1, s1, 6, 1, Block.of("iron_bars", 0x888888));
        for (int i = s0 + 2; i <= s1 - 2; i += 2) {
            v.set(i, 5, 1, Block.of("end_rod[facing=down]", 0xE8E2D8));
        }
        // 양옆 계단식 앉는 단 (무대를 봄)
        for (int side : new int[]{0, w - 3}) {
            for (int j = 2; j <= d - 6; j++) {
                v.set(side, 0, j, Block.of("smooth_stone", 0x9E9E9E));
                v.set(side + 1, 0, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                v.set(side == 0 ? side : side + 2, 1, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            }
        }
        // 나무와 등
        MarketPlan.tree(v, 1, d - 3);
        MarketPlan.tree(v, w - 2, d - 3);
        MarketPlan.lampPost(v, mid - 6 < 2 ? 2 : mid - 6, d - 1);
        MarketPlan.lampPost(v, mid + 6 > w - 3 ? w - 3 : mid + 6, d - 1);
        v.connect();
        return v;
    }

    private HongdaePlan() {
    }
}
