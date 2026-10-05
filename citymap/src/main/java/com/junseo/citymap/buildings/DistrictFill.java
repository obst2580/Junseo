package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 랜드마크를 짓고 남은 동네 땅을 그 동네다운 일반 건물로 채웁니다.
 * 도로로 둘러싸인 블록마다 아파트 단지·사무 빌딩·상가·빌라 중 하나로 정하고, 구역마다 섞는 비율이 다릅니다
 * (여의도·강남은 사무 빌딩, 송파·마포는 아파트 단지, 홍대·광진·대학가는 빌라와 상가).
 * 청라는 분양 필지로 비워 둡니다 (부동산 회사가 플레이어에게 파는 땅).
 */
final class DistrictFill {
    /** 블록 종류 비율(%)과 층수 범위 */
    record Mix(int apartment, int office, int commercial, int mixed, int villa,
               int aptMin, int aptMax, int officeMin, int officeMax) {
    }

    static final Map<String, Mix> MIX = Map.ofEntries(
            Map.entry("yeouido", new Mix(30, 55, 10, 5, 0, 12, 15, 15, 32)),
            Map.entry("junggu", new Mix(0, 45, 40, 15, 0, 0, 0, 8, 20)),
            Map.entry("yongsan", new Mix(20, 15, 30, 20, 15, 10, 15, 6, 14)),
            Map.entry("gangnam", new Mix(30, 45, 20, 5, 0, 12, 20, 12, 30)),
            Map.entry("songpa", new Mix(65, 10, 15, 10, 0, 12, 20, 10, 20)),
            Map.entry("mapo", new Mix(55, 5, 20, 10, 10, 10, 18, 6, 12)),
            Map.entry("gwangjin", new Mix(10, 0, 20, 25, 45, 10, 15, 0, 0)),
            Map.entry("hongdae", new Mix(0, 0, 45, 35, 20, 0, 0, 0, 0)),
            Map.entry("university", new Mix(0, 0, 25, 25, 50, 0, 0, 0, 0)),
            Map.entry("namsan", new Mix(0, 0, 10, 20, 70, 0, 0, 0, 0)),
            Map.entry("bukhansan", new Mix(0, 0, 10, 20, 70, 0, 0, 0, 0)));

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

    static List<Placement> fill(CityTerrain t, String id, List<Placement> existing) {
        Polygon area = Plans.district(t, id);
        Mix mix = MIX.get(id);
        if (area == null || mix == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        GuincheonPlan.claimAll(m, existing.stream().filter(p -> !CityBuildings.isGround(p)).toList());
        Random rnd = Plans.random(t, "fill-" + id);
        List<Placement> out = new ArrayList<>();
        int[] dong = {101};
        String complex = COMPLEX[rnd.nextInt(COMPLEX.length)];
        for (int[] block : m.blocks()) {
            if (block[4] < 150) {
                continue;
            }
            int bw = block[2] - block[0] + 1, bd = block[3] - block[1] + 1;
            int roll = rnd.nextInt(100);
            if (roll < mix.apartment && bw >= 45 && bd >= 40) {
                apartments(m, block, mix, rnd, out, dong, complex);
                complex = COMPLEX[rnd.nextInt(COMPLEX.length)];
            } else if (roll < mix.apartment + mix.office && bw >= 34 && bd >= 30) {
                for (LotPlanner.Lot lot : new LotPlanner(m, rnd, 30, 48, 5, 8).split(block)) {
                    if (lot.width() >= 28 && lot.depth() >= 26) {
                        out.add(office(lot, mix, rnd));
                    }
                }
            }
            GuincheonPlan.claimAll(m, out);
        }
        // 남은 땅: 상가·빌라 (2번)
        for (int pass = 0; pass < 2; pass++) {
            for (int[] block : m.blocks()) {
                if (block[4] < 100) {
                    continue;
                }
                for (LotPlanner.Lot lot : new LotPlanner(m, rnd, 10, 18, 2, 3).split(block)) {
                    out.add(small(lot, mix, rnd));
                }
                GuincheonPlan.claimAll(m, out);
            }
        }
        return out;
    }

    /** 아파트 단지: 남향 동을 줄지어 세우고, 동 사이는 주차장 */
    private static void apartments(BuildMask m, int[] block, Mix mix, Random rnd, List<Placement> out, int[] dong, String complex) {
        int depth = Apartment.depth(), gap = 12;
        for (int z = block[1]; z + depth - 1 <= block[3]; z += depth + gap) {
            int x = block[0];
            while (x + 30 <= block[2]) {
                int units = Math.min(6, Apartment.unitsFor(block[2] - x + 1));
                int w = Apartment.CORE + units * Apartment.UNIT + 1;
                int x1 = x + w - 1, z1 = z + depth - 1;
                if (x1 <= block[2] && m.rectFree(x, z, x1, z1)) {
                    int floors = mix.aptMin + rnd.nextInt(mix.aptMax - mix.aptMin + 1);
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

    /** 사무 빌딩 (앞 공개공지 3칸, 둘레 2칸 띄움) */
    private static Placement office(LotPlanner.Lot lot, Mix mix, Random rnd) {
        int floors = mix.officeMin + rnd.nextInt(Math.max(1, mix.officeMax - mix.officeMin + 1));
        Block[] skin = GLASS[rnd.nextInt(GLASS.length)];
        long seed = rnd.nextLong();
        boolean setback = floors >= 18 && rnd.nextBoolean();
        String name = KoreanNames.prefix(rnd) + (rnd.nextBoolean() ? "빌딩" : "타워");
        return Plans.lot(name, "office", lot, (w, d) -> {
            Tower.Spec s = new Tower.Spec(w, d, floors);
            int top = floors - 3;
            s.shape = (i, j, k) -> {
                int in = setback && k >= top ? 4 : 2;
                return i >= in && i < w - in && j >= in && j < d - Math.max(in, 3);
            };
            s.glass = skin[0];
            s.mullion = skin[1];
            s.spandrel = skin[2];
            s.lobbyH = floors >= 15 ? Floors.HALL : Floors.GROUND;
            s.elevators = w >= 44 ? 2 : 1;
            s.restrooms = w >= 40 ? 2 : 1;
            s.helipad = floors >= 20;
            s.name = name;
            Tower t = Tower.build(s, new Random(seed));
            // 앞 공개공지 포장
            t.v.fill(0, -1, 0, w - 1, -1, d - 1, LIGHT_GRAY_CONCRETE);
            for (int i = 1; i < w - 1; i += 6) {
                if (t.v.get(i, 0, d - 1) == null) {
                    MarketPlan.lampPost(t.v, i, d - 1);
                }
            }
            return t.v;
        });
    }

    private static Placement small(LotPlanner.Lot lot, Mix mix, Random rnd) {
        long seed = rnd.nextLong();
        int total = mix.commercial + mix.mixed + mix.villa;
        int roll = rnd.nextInt(Math.max(1, total));
        if (rnd.nextInt(100) < 6 && lot.width() >= 12 && lot.depth() >= 12) {
            return Plans.lot("주차장", "parking", lot, (w, d) -> ParkingLot.build(w, d, new Random(seed)));
        }
        ShopHouse.Style style = roll < mix.commercial ? ShopHouse.Style.COMMERCIAL
                : roll < mix.commercial + mix.mixed ? ShopHouse.Style.MIXED : ShopHouse.Style.VILLA;
        String kind = style == ShopHouse.Style.VILLA ? "house" : "shop";
        String name = style == ShopHouse.Style.VILLA ? "다세대 주택" : style == ShopHouse.Style.MIXED ? "상가주택" : "상가 건물";
        return Plans.lot(name, kind, lot, (w, d) -> ShopHouse.build(w, d, new Random(seed), style));
    }

    private DistrictFill() {
    }
}
