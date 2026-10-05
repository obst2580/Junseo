package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 청라 외곽 간척지 농지 (농부)와 축산 농장 (축산): 청라·구인천 북서쪽, 한강 하구와 서해 사이 평지 (구역 밖).
 * <ul>
 *   <li>농지: 공항고속도로(H1)에서 서쪽으로 뻗은 시멘트 농로와 옆 용수로. 농로 북쪽은 논(물 댄 논바닥에 벼 줄과 물 줄,
 *       논두렁, 흙 농로), 남쪽은 빌려 쓰는 밭(번호 표지판, 이랑마다 작물, 가운데 물고랑). 농로 들머리에 농협 공판장과 주차장</li>
 *   <li>축산 농장: 한강 둑 아래 따로 난 농장 길. 방역 소독 문, 사무실, 우사(가운데 사료 통로, 양쪽 칸막이 우리, 사료조·물통),
 *       착유실, 사료 탱크, 돈사, 계사, 건초 더미. 동물은 두지 않음 (플러그인이 키움)</li>
 * </ul>
 * 거점 farm 은 공판장 앞마당, ranch 는 농장 들머리 사무실 앞에 있습니다.
 */
final class FarmPlan {

    static List<Placement> plan(CityTerrain t, List<Placement> existing) {
        Layout.Hub farm = Plans.hub(t, "farm"), ranch = Plans.hub(t, "ranch");
        List<Placement> out = new ArrayList<>();
        Random rnd = Plans.random(t, "farm");
        long[] seeds = new long[12];
        for (int k = 0; k < seeds.length; k++) {
            seeds[k] = rnd.nextLong();
        }
        if (farm != null) {
            Fields f = new Fields(t, (int) Math.floor(farm.x()), (int) Math.floor(farm.z()));
            out.add(Placement.rect("청라 농지", "farm", f.x0, f.zN, f.roadEast, f.zS, "south",
                    (w, d) -> List.of(new double[]{0, f.rz0 - f.zN}, new double[]{w, f.rz0 - f.zN}, new double[]{w, f.rz1 + 1 - f.zN}, new double[]{0, f.rz1 + 1 - f.zN}),
                    (w, d) -> f.build(new Random(seeds[0]))));
            out.add(Placement.rect("청라 농산물 공판장", "farm", f.hall.x0(), f.hall.z0(), f.hall.x1(), f.hall.z1(), "north",
                    (w, d) -> Barns.saleHall(w, d, new Random(seeds[1]))));
            out.add(Placement.rect("공판장 주차장", "parking", f.lot.x0(), f.lot.z0(), f.lot.x1(), f.lot.z1(), "west",
                    (w, d) -> ParkingLot.build(w, d, null)));
        }
        if (ranch != null) {
            Ranch r = new Ranch(t, (int) Math.floor(ranch.x()), (int) Math.floor(ranch.z()));
            out.add(Placement.rect("청라 축산 농장", "farm", r.x0, r.z0, r.x1, r.z1, "south",
                    (w, d) -> List.of(new double[]{0, r.rd0 - r.z0}, new double[]{w, r.rd0 - r.z0}, new double[]{w, r.rd1 + 1 - r.z0}, new double[]{0, r.rd1 + 1 - r.z0}),
                    (w, d) -> r.build(new Random(seeds[2]))));
            out.add(rect("우사", r.cattle, "south", (w, d) -> Barns.cattleShed(w, d, new Random(seeds[3]))));
            out.add(rect("착유실", r.milk, "south", (w, d) -> Barns.milkRoom(w, d)));
            out.add(rect("돈사", r.pig, "south", (w, d) -> Barns.pigShed(w, d, new Random(seeds[4]))));
            out.add(rect("계사", r.chicken, "north", (w, d) -> Barns.chickenHouse(w, d, new Random(seeds[5]))));
            out.add(rect("축산 농장 사무실", r.office, "north", (w, d) -> Barns.office(w, d)));
            out.add(Placement.rect("축산 농장 주차장", "parking", r.lot.x0(), r.lot.z0(), r.lot.x1(), r.lot.z1(), "north",
                    (w, d) -> ParkingLot.build(w, d, null)));
        }
        List<Placement> kept = new ArrayList<>();
        for (Placement p : out) {
            if (PortPlan.clear(p, existing, (x, z) -> free(t, x, z))) {
                kept.add(p);
            }
        }
        return kept;
    }

    static Placement rect(String name, PortPlan.R r, String front, java.util.function.BiFunction<Integer, Integer, Voxels> build) {
        return Placement.rect(name, "barn", r.x0(), r.z0(), r.x1(), r.z1(), front, build);
    }

    /** 구역 밖 맨땅 (길·물·산이 아님) */
    static boolean free(CityTerrain t, int x, int z) {
        Column c = t.column(x, z);
        return !c.isRoad() && !c.isWater() && !c.deck && c.mountainHeight == 0 && c.district == null;
    }

    /** x 줄 z 에서 서쪽으로 길이 끝나는 곳 (길 바로 서쪽 첫 땅 x) */
    static int westOfRoad(CityTerrain t, int xStart, int z) {
        int x = xStart;
        while (x > xStart - 80 && !t.column(x, z).isRoad()) {
            x--;
        }
        while (x > xStart - 120 && t.column(x, z).isRoad()) {
            x--;
        }
        return x;
    }

    // ------------------------------------------------------------------ 농지

    static final Block FARMLAND = Block.of("farmland[moisture=7]", 0x5A3E26);
    static final Block RIDGE = GRASS;
    static final Block PATH = Block.of("dirt_path", 0x94794A);
    static final Block CEMENT = Block.of("light_gray_concrete", 0x7D7D73);

    static final class Fields {
        final CityTerrain t;
        final int hx, hz;
        /** 농로 z 범위, 동쪽 끝 (고속도로 바로 서쪽), 서쪽 끝, 논 북쪽 끝, 밭 남쪽 끝 */
        final int rz0, rz1, roadEast, x0, zN, zS;
        final PortPlan.R hall, lot;

        Fields(CityTerrain t, int hx, int hz) {
            this.t = t;
            this.hx = hx;
            this.hz = hz;
            rz0 = hz - 8;
            rz1 = hz - 4;
            roadEast = westOfRoad(t, hx + 40, (rz0 + rz1) / 2 - 0) + 0;
            x0 = hx - 224;
            zN = hz - 115;
            zS = hz + 60;
            hall = new PortPlan.R(hx - 34, hz + 3, hx - 6, hz + 21);
            lot = new PortPlan.R(hx - 1, hz + 3, hx + 14, hz + 19);
        }

        boolean free(int x, int z) {
            return FarmPlan.free(t, x, z);
        }

        Voxels build(Random r) {
            PortPlan.Canvas c = new PortPlan.Canvas(x0, zN, roadEast, zS, -3, 4);
            int cz = rz0 - 2; // 용수로
            // 농로 (시멘트, 가장자리 흰 선 없이 회색 띠)
            for (int x = x0; x <= roadEast; x++) {
                for (int z = rz0; z <= rz1; z++) {
                    if (free(x, z) || t.column(x, z).district != null && !t.column(x, z).isRoad()) {
                        c.set(x, -1, z, z == rz0 || z == rz1 ? Block.of("smooth_stone", 0x9E9E9E) : CEMENT);
                    }
                }
            }
            // 공판장 앞마당 (거점 포함)
            for (int x = hall.x0() - 4; x <= roadEast; x++) {
                for (int z = rz1 + 1; z < hall.z0(); z++) {
                    if (free(x, z)) {
                        c.set(x, -1, z, (x + z) % 7 == 0 ? Block.of("polished_andesite", 0x848685) : CEMENT);
                    }
                }
            }
            // 논: 농로 북쪽. 서쪽에서 동쪽으로 [흙 농로 3][논 24][두렁 1][논 24] 되풀이, 남북은 논 25칸마다 두렁
            int east = hall.x1() + 2;
            for (int z = zN; z <= cz + 1; z++) {
                for (int x = x0; x <= east; x++) {
                    if (!free(x, z)) {
                        continue;
                    }
                    int px = Math.floorMod(x - x0, 52);
                    boolean path = px < 3;
                    boolean ridgeX = px == 27;
                    int pz = z - (zN + 1);
                    boolean ridgeZ = z == zN || Math.floorMod(pz, 26) == 25 || z >= cz - 1;
                    if (path) {
                        c.set(x, -1, z, PATH);
                    } else if (ridgeX || ridgeZ) {
                        c.set(x, -1, z, RIDGE);
                    } else {
                        int paddy = Math.floorDiv(x - x0, 26) * 7 + Math.floorDiv(pz, 26);
                        int age = 2 + Math.floorMod(paddy * 5 + 3, 6);
                        c.set(x, -1, z, AIR);
                        if (Math.floorMod(z, 3) == 0) {
                            c.set(x, -2, z, WATER);
                        } else {
                            c.set(x, -2, z, FARMLAND);
                            c.set(x, -1, z, Block.of("wheat[age=" + age + "]", age >= 7 ? 0xB5A445 : 0x6E9A3A));
                        }
                    }
                }
            }
            // 용수로 (농로 북쪽): 시멘트 도랑에 물, 논 들머리마다 덮개
            for (int x = x0; x <= east; x++) {
                if (!free(x, cz)) {
                    continue;
                }
                c.set(x, -3, cz, Block.of("smooth_stone", 0x9E9E9E));
                c.set(x, -2, cz, WATER);
                c.set(x, -1, cz, Math.floorMod(x - x0, 52) < 3 ? Block.of("smooth_stone_slab[type=top,waterlogged=true]", 0x9E9E9E) : WATER);
                c.set(x, -1, cz - 1, Block.of("smooth_stone", 0x9E9E9E));
                c.set(x, -1, cz + 1, Block.of("smooth_stone", 0x9E9E9E));
            }
            // 밭: 농로 남쪽, 빌려 쓰는 밭 (9 × 14, 사이 2칸 길), 번호 표지판
            String[] crops = {"carrots", "potatoes", "beetroots", "wheat", "", "mulch", "carrots", "potatoes"};
            int no = 1;
            for (int pz0 = rz1 + 4; pz0 + 13 <= zS - 8; pz0 += 16) {
                for (int px0 = x0 + 2; px0 + 8 < hall.x0() - 6; px0 += 11) {
                    boolean ok = true;
                    for (int z = pz0 - 1; z <= pz0 + 13 && ok; z++) {
                        for (int x = px0 - 1; x <= px0 + 9 && ok; x++) {
                            ok = free(x, z);
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    String crop = crops[r.nextInt(crops.length)];
                    plot(c, px0, pz0, crop, r);
                    c.set(px0 + 1, 0, pz0 - 1, Block.of("oak_sign[rotation=8,waterlogged=false]", 0xA2834F)
                            .withText("black", false, "청라 농지", String.format("임대 밭 %02d", no++), crop.isEmpty() ? "임대 가능" : "임대 중"));
                }
            }
            // 밭 사이 길 (흙길)
            for (int z = rz1 + 1; z <= zS - 6; z++) {
                for (int x = x0; x < hall.x0() - 4; x++) {
                    if (free(x, z) && c.get(x, -1, z) == null && c.get(x, -2, z) == null) {
                        c.set(x, -1, z, PATH);
                    }
                }
            }
            // 농지 안내판 (농로 들머리)
            int sx = roadEast - 6, sz = rz0 - 4;
            if (free(sx, sz)) {
                c.fill(sx, 0, sz, sx, 1, sz, Block.of("spruce_fence", 0x725430));
                c.fill(sx + 3, 0, sz, sx + 3, 1, sz, Block.of("spruce_fence", 0x725430));
                c.fill(sx, 2, sz, sx + 3, 3, sz, Block.of("spruce_planks", 0x725430));
                c.set(sx + 1, 3, sz + 1, Blocks.wallSign("spruce", "south", "white", false, "청라 간척지", "농지", "주말농장 임대"));
                c.set(sx + 2, 3, sz + 1, Blocks.wallSign("spruce", "south", "white", false, "농협 공판장", "← 수매·판매"));
            }
            c.v.connect();
            return c.v;
        }

        /** 밭 한 뙈기 (9 × 14): 가운데 물고랑, 양쪽 이랑 넷씩. 작물, 비닐 덮은 고추 이랑, 빈 밭 */
        void plot(PortPlan.Canvas c, int px0, int pz0, String crop, Random r) {
            for (int z = pz0; z <= pz0 + 13; z++) {
                for (int x = px0; x <= px0 + 8; x++) {
                    if (x == px0 + 4) {
                        c.set(x, -2, z, Block.of("dirt", 0x86603F));
                        c.set(x, -1, z, WATER);
                        continue;
                    }
                    c.set(x, -1, z, FARMLAND);
                    switch (crop) {
                        case "" -> {
                        }
                        case "mulch" -> {
                            if (z % 2 == 0) {
                                c.set(x, 0, z, Block.of("sweet_berry_bush[age=" + (1 + r.nextInt(2)) + "]", 0x3F6B2A));
                                c.set(x, -1, z, Block.of("dirt", 0x86603F));
                            } else {
                                c.set(x, 0, z, Block.of("black_carpet", 0x141519));
                            }
                        }
                        default -> {
                            int max = crop.equals("beetroots") ? 3 : 7;
                            int age = Math.min(max, 1 + r.nextInt(max + 1));
                            c.set(x, 0, z, Block.of(crop + "[age=" + age + "]", crop.equals("wheat") ? 0x9DA24A : 0x4C8A2E));
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 축산 농장

    static final class Ranch {
        final CityTerrain t;
        final int hx, hz;
        /** 농장 길 z 범위 */
        final int rd0, rd1;
        final int x0, z0, x1, z1;
        final PortPlan.R cattle, milk, pig, chicken, office, lot, paddock;
        final int siloX, siloZ;

        Ranch(CityTerrain t, int hx, int hz) {
            this.t = t;
            this.hx = hx;
            this.hz = hz;
            rd0 = hz - 8;
            rd1 = hz - 4;
            x1 = westOfRoad(t, hx + 40, (rd0 + rd1) / 2);
            x0 = hx - 165;
            z0 = hz - 52;
            z1 = hz + 9;
            office = new PortPlan.R(hx - 28, hz - 2, hx - 14, hz + 7);
            lot = new PortPlan.R(hx - 52, hz - 2, hx - 32, hz + 8);
            cattle = new PortPlan.R(hx - 122, hz - 27, hx - 88, hz - 13);
            milk = new PortPlan.R(hx - 86, hz - 24, hx - 79, hz - 17);
            siloX = hx - 73;
            siloZ = hz - 20;
            pig = new PortPlan.R(hx - 156, hz - 29, hx - 126, hz - 17);
            chicken = new PortPlan.R(hx - 152, hz - 1, hx - 118, hz + 8);
            paddock = new PortPlan.R(hx - 66, hz - 22, hx - 42, hz - 12);
        }

        boolean free(int x, int z) {
            return FarmPlan.free(t, x, z);
        }

        Voxels build(Random r) {
            PortPlan.Canvas c = new PortPlan.Canvas(x0, z0, x1, z1, -2, 16);
            // 농장 마당 (자갈) 과 농장 길 (시멘트)
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (!free(x, z)) {
                        continue;
                    }
                    if (z >= rd0 && z <= rd1) {
                        c.set(x, -1, z, z == rd0 || z == rd1 ? Block.of("smooth_stone", 0x9E9E9E) : CEMENT);
                    } else if (x < hx - 60 && z >= hz - 40 && z < rd0 || x >= hx - 60 && z >= rd0 - 3 && z < rd0) {
                        c.set(x, -1, z, (x * 7 + z * 3) % 11 == 0 ? COARSE_DIRT : Block.of("gravel", 0x837F7E));
                    }
                }
            }
            // 사무실 앞마당 (거점)
            for (int z = rd1 + 1; z < office.z0(); z++) {
                for (int x = office.x0() - 2; x <= x1; x++) {
                    if (free(x, z)) {
                        c.set(x, -1, z, CEMENT);
                    }
                }
            }
            for (int z = office.z0(); z <= z1; z++) {
                for (int x = office.x1() + 1; x <= x1; x++) {
                    if (free(x, z)) {
                        c.set(x, -1, z, CEMENT);
                    }
                }
            }
            // 방역 소독 문 (농장 길 위): 기둥 둘, 위 보에 분사구, 표지판
            int gx = hx - 58;
            for (int z : new int[]{rd0 - 1, rd1 + 1}) {
                c.fill(gx, 0, z, gx, 5, z, Block.of("light_gray_concrete", 0x7D7D73));
            }
            c.fill(gx, 6, rd0 - 1, gx, 6, rd1 + 1, Block.of("light_gray_concrete", 0x7D7D73));
            for (int z = rd0; z <= rd1; z++) {
                c.set(gx, 5, z, Block.of("end_rod[facing=down]", 0xE8E2D8));
            }
            c.set(gx + 1, 4, rd1 + 1, Blocks.wallSign("birch", "east", "red", false, "방역 중", "차량 소독 후", "출입", "외부인 출입 금지"));
            c.set(gx + 1, 4, rd0 - 1, Blocks.wallSign("birch", "east", "black", false, "", "준서 축산 농장", "우사·돈사·계사"));
            // 사료 탱크 (착유실 동쪽)
            silo(c, siloX, siloZ);
            // 건초 더미 (돈사 남쪽 마당)
            for (int k = 0; k < 6; k++) {
                int x = pig.x0() + 2 + (k % 3) * 3, z = pig.z1() + 3 + (k / 3) * 2;
                if (free(x, z) && free(x + 1, z + 1)) {
                    c.fill(x, 0, z, x + 1, k < 3 ? 1 : 0, z + 1, Block.of("hay_block[axis=" + (k % 2 == 0 ? "x" : "z") + "]", 0xA68B0C));
                }
            }
            // 소 운동장 (울타리, 남쪽 문), 물통
            PortPlan.R p = paddock;
            for (int x = p.x0(); x <= p.x1(); x++) {
                for (int z = p.z0(); z <= p.z1(); z++) {
                    if (!free(x, z)) {
                        continue;
                    }
                    c.set(x, -1, z, (x + z) % 5 == 0 ? COARSE_DIRT : Block.of("rooted_dirt", 0x8F6B4F));
                    if (x == p.x0() || x == p.x1() || z == p.z0() || z == p.z1()) {
                        boolean gate = z == p.z1() && x == (p.x0() + p.x1()) / 2;
                        c.set(x, 0, z, gate ? Block.of("oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]", 0xA2834F)
                                : Block.of("oak_fence", 0xA2834F));
                    }
                }
            }
            c.set(p.x0() + 2, 0, p.z0() + 2, Block.of("water_cauldron[level=3]", 0x3F76E4));
            c.fill(p.x1() - 4, 0, p.z0() + 1, p.x1() - 2, 0, p.z0() + 1, Block.of("composter[level=7]", 0x7A5A33));
            c.v.connect();
            return c.v;
        }

        /** 사료 탱크: 회색 원통(높이 11), 원뿔 지붕, 아래 배출구 */
        void silo(PortPlan.Canvas c, int x, int z) {
            double cx = x - c.x0 + 0.5, cz = z - c.z0 + 0.5;
            c.v.cylinder(cx, cz, 2.5, 3, 11, Block.of("light_gray_concrete", 0x7D7D73));
            c.v.cylinder(cx, cz, 2.5, 7, 7, Block.of("smooth_stone", 0x9E9E9E));
            c.v.cylinder(cx, cz, 1.8, 12, 12, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            c.v.cylinder(cx, cz, 0.9, 13, 13, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
            for (int[] o : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                c.fill(x + o[0], 0, z + o[1], x + o[0], 2, z + o[1], Block.of("polished_blackstone_wall", 0x353038));
            }
            c.v.cylinder(cx, cz, 1.2, 1, 2, Block.of("light_gray_concrete", 0x7D7D73));
            c.set(x, 0, z, Block.of("polished_andesite", 0x848685));
        }
    }

    private FarmPlan() {
    }
}
