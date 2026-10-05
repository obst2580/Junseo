package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 청라 농지·축산 농장 건물: 농산물 공판장, 우사, 착유실, 돈사, 계사, 농장 사무실. 모두 1층, 정면 남쪽(j = d-1).
 * 조립식 강판(흰·연회색 벽, 녹색·청색 지붕) 축사 모양. 동물은 두지 않습니다.
 */
final class Barns {
    static final Block PANEL = Block.of("white_concrete", 0xCFD5D6);
    static final Block BASE = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block FLOOR = Block.of("smooth_stone", 0x9E9E9E);
    static final Block ROOF = Block.of("green_terracotta", 0x4C532A);
    static final Block ROOF_BLUE = Block.of("cyan_terracotta", 0x565B5B);
    static final Block GLASS = Block.of("glass_pane", 0xC8DCE4);
    static final Block FENCE = Block.of("oak_fence", 0xA2834F);
    static final Block POST = Block.of("polished_blackstone_wall", 0x353038);

    /** 낮은 맞배지붕 (처마 높이 eave, 가운데로 2칸에 1칸씩 오름) */
    static void gableRoof(Voxels v, int w, int d, int eave, Block roof) {
        for (int j = -1; j <= d; j++) {
            int rise = Math.min(j + 1, d - j) / 3;
            v.fill(-1, eave + rise, j, w, eave + rise, j, roof);
        }
    }

    /** 문 (2칸): 나무 문 */
    static void door(Voxels v, int i, int j, String facing, String wood) {
        v.set(i, 0, j, Blocks.door(wood, facing, false));
        v.set(i, 1, j, Blocks.door(wood, facing, true));
    }

    // ------------------------------------------------------------------ 공판장

    /**
     * 농산물 공판장 (w × d): 높은 철골 창고(벽 높이 7), 앞 큰 출입구 둘(셔터를 올린 채), 콘크리트 바닥.
     * 안에 경매장 바닥(팔레트 위 상자·쌀 포대·호박·수박), 바닥 저울, 동쪽 구석 수매 접수 사무실(창구 카운터, 책상).
     */
    static Voxels saleHall(int w, int dd, Random r) {
        Voxels v = new Voxels(w, dd, -1, 11);
        int d = dd - 1; // 건물 깊이 (맨 앞 줄은 표지판·포장)
        v.fill(0, -1, d, w - 1, -1, d, FLOOR);
        int H = 7;
        v.fill(0, -1, 0, w - 1, -1, d - 1, FLOOR);
        v.walls(0, 0, 0, w - 1, H - 1, d - 1, PANEL);
        v.walls(0, 0, 0, w - 1, 0, d - 1, BASE);
        v.walls(0, H - 1, 0, w - 1, H - 1, d - 1, Block.of("green_concrete", 0x495B24));
        gableRoof(v, w, d, H, ROOF);
        for (int j = 0; j < d; j++) {
            int top = H + Math.min(j + 1, d - j) / 3;
            v.fill(0, H, j, 0, top - 1, j, PANEL);
            v.fill(w - 1, H, j, w - 1, top - 1, j, PANEL);
        }
        // 앞 출입구 둘 (4×4, 셔터 올림: 위에 셔터 통)
        for (int i0 : new int[]{3, 10}) {
            v.fill(i0, 0, d - 1, i0 + 3, 3, d - 1, AIR);
            v.fill(i0, 4, d - 1, i0 + 3, 4, d - 1, BASE);
        }
        // 옆 창 (높은 띠창)
        for (int j = 2; j < d - 2; j += 2) {
            v.set(0, 4, j, GLASS);
            v.set(w - 1, 4, j, GLASS);
        }
        // 경매장: 팔레트(가문비 반 블록) 위 상자·포대·호박·수박 줄
        Block[] goods = {Block.of("composter[level=7]", 0x7A5A33), Block.of("white_wool", 0xE9ECEC), PUMPKIN, MELON,
                Block.of("composter[level=7]", 0x7A5A33), HAY};
        for (int j = 3; j <= d - 6; j += 4) {
            for (int i = 2; i <= w - 12; i += 3) {
                Block g = goods[r.nextInt(goods.length)];
                v.fill(i, 0, j, i + 1, 0, j + 1, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
                v.fill(i, 0, j, i + 1, 0, j + 1, g);
                if (r.nextBoolean()) {
                    v.fill(i, 1, j, i + 1, 1, j + 1, g);
                }
            }
        }
        // 바닥 저울 (출입구 안쪽)
        v.fill(5, -1, d - 4, 7, -1, d - 3, Block.of("iron_block", 0xB8B8B8));
        v.set(8, 0, d - 4, Block.of("iron_block", 0xB8B8B8));
        v.set(8, 1, d - 4, Block.of("heavy_weighted_pressure_plate[power=0]", 0xDCDCDC));
        // 수매 접수 사무실 (동쪽 앞 구석): 벽, 창구 카운터, 책상, 문
        int ox0 = w - 9, oz0 = d - 8;
        v.fill(ox0, 0, oz0, w - 1, 2, oz0, PANEL);
        v.fill(ox0, 0, oz0, ox0, 2, d - 1, PANEL);
        v.fill(ox0, 3, oz0, w - 1, 3, d - 1, BASE);
        v.fill(ox0 + 2, 1, oz0, ox0 + 5, 1, oz0, GLASS);
        v.fill(ox0 + 2, 0, oz0 + 1, ox0 + 5, 0, oz0 + 1, Furniture.COUNTER);
        door(v, ox0 + 7, oz0, "north", "birch");
        Frame f = Frame.of(v);
        Furniture.desk(f, ox0 + 3, 0, d - 3, "north");
        Furniture.desk(f, ox0 + 6, 0, d - 3, "north");
        v.set(ox0 + 4, 2, oz0 - 1, Blocks.wallSign("birch", "north", "black", false, "", "수매 접수", "창구"));
        v.set(w - 2, 1, oz0 + 2, Blocks.wallSign("birch", "west", "black", false, "오늘 수매가", "쌀·배추·무", "감자·고추"));
        v.set(ox0 + 4, 2, d - 4, Interior.LIGHT);
        door(v, ox0 + 4, d - 1, "south", "birch");
        // 등, 이름 표지판
        for (int i = 4; i < w - 10; i += 6) {
            for (int j = 4; j < d - 2; j += 6) {
                v.set(i, H - 2, j, LANTERN_HANGING);
                v.fill(i, H - 1, j, i, H - 1, j, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
            }
        }
        v.set(7, 5, d, Blocks.wallSign("dark_oak", "south", "white", false, "", "청라 농산물", "공판장"));
        v.set(12, 5, d, Blocks.wallSign("dark_oak", "south", "white", false, "", "수매·경매", "판매"));
        v.connect();
        return v;
    }

    // ------------------------------------------------------------------ 축사

    /**
     * 우사 (w × d): 철골 기둥과 맞배지붕, 옆은 트인 낮은 벽. 가운데 사료 통로(동서로 뚫림), 양쪽 사료조(낮은 턱)와 목 걸이 철책,
     * 7칸마다 나무 울타리로 나눈 우리, 우리마다 바깥쪽 문과 물통, 깔짚 바닥, 통로에 매단 등.
     */
    static Voxels cattleShed(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 9);
        int mid = d / 2, a0 = mid - 1, a1 = mid + 1;
        // 바닥: 우리는 깔짚, 통로는 콘크리트
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                boolean alley = j >= a0 && j <= a1;
                v.set(i, -1, j, alley ? FLOOR : (i * 3 + j * 5) % 7 == 0 ? HAY : Block.of("coarse_dirt", 0x77563B));
            }
        }
        // 기둥과 지붕
        for (int i = 0; i < w; i += 5) {
            v.fill(i, 0, 0, i, 3, 0, POST);
            v.fill(i, 0, d - 1, i, 3, d - 1, POST);
        }
        v.fill(w - 1, 0, 0, w - 1, 3, 0, POST);
        v.fill(w - 1, 0, d - 1, w - 1, 3, d - 1, POST);
        gableRoof(v, w, d, 4, ROOF_BLUE);
        // 사료조 (낮은 턱) 와 목 걸이 철책
        for (int i = 1; i < w - 1; i++) {
            for (int j : new int[]{a0 - 1, a1 + 1}) {
                v.set(i, 0, j, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                v.set(i, 1, j, Block.of("iron_bars", 0x888888));
                v.set(i, 2, j, Block.of("iron_bars", 0x888888));
            }
        }
        // 우리 나누기 (7칸마다 울타리), 바깥쪽 낮은 벽과 문, 물통
        for (int side = 0; side < 2; side++) {
            int jIn = side == 0 ? a0 - 2 : a1 + 2, jOut = side == 0 ? 0 : d - 1;
            int jl = Math.min(jIn, jOut), jh = Math.max(jIn, jOut);
            for (int i = 0; i < w; i += 7) {
                v.fill(i, 0, jl, i, 0, jh, FENCE);
            }
            v.fill(w - 1, 0, jl, w - 1, 0, jh, FENCE);
            for (int i = 1; i < w - 1; i++) {
                if (i % 7 == 0) {
                    continue;
                }
                boolean gate = i % 7 == 3;
                v.set(i, 0, jOut, gate ? Block.of("oak_fence_gate[facing=" + (side == 0 ? "north" : "south") + ",in_wall=false,open=false,powered=false]", 0xA2834F)
                        : BASE);
                if (i % 7 == 5) {
                    v.set(i, 0, jOut + (side == 0 ? 1 : -1), Block.of("water_cauldron[level=3]", 0x3F76E4));
                }
            }
        }
        // 통로 끝은 트임, 등, 건초
        for (int i = 3; i < w; i += 8) {
            v.set(i, 3, mid, LANTERN_HANGING);
        }
        v.fill(1, 0, a0, 1, 1, a0, HAY);
        v.set(0, 0, a0 - 1, Block.of("oak_sign[rotation=4,waterlogged=false]", 0xA2834F)
                .withText("black", false, "", "우사", "방역 중"));
        v.connect();
        return v;
    }

    /** 착유실 (작은 흰 타일 방): 문 남쪽, 우유 냉각 탱크, 개수대, 배관, 등 */
    static Voxels milkRoom(int w, int dd) {
        Voxels v = new Voxels(w, dd, -1, 5);
        int d = dd - 1; // 건물 깊이 (맨 앞 줄은 표지판)
        Block tile = Block.of("white_terracotta", 0xD1B2A1);
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("light_gray_terracotta", 0x876A61));
        v.walls(0, 0, 0, w - 1, 2, d - 1, tile);
        v.fill(0, 3, 0, w - 1, 3, d - 1, BASE);
        door(v, w / 2, d - 1, "south", "birch");
        v.set(1, 1, d - 1, GLASS);
        v.fill(1, 0, 1, 3, 1, 2, Block.of("iron_block", 0xDCDCDC)); // 냉각 탱크
        v.set(w - 2, 0, 1, CAULDRON);
        v.fill(w - 2, 2, 1, w - 2, 2, d - 3, Block.of("iron_bars", 0x888888));
        v.set(w / 2, 2, d / 2, Interior.LIGHT);
        v.set(w / 2 + 1, 2, d, Blocks.wallSign("birch", "south", "black", false, "", "착유실"));
        v.connect();
        return v;
    }

    /**
     * 돈사 (w × d): 닫힌 흰 벽과 창, 낮은 지붕. 가운데 동서 복도, 양쪽 철책으로 나눈 돈방(문은 복도 쪽 나무 문짝),
     * 사료통·물통, 끝 벽 환풍기. 정문은 남쪽 가운데 (복도까지 통로).
     */
    static Voxels pigShed(int w, int dd, Random r) {
        Voxels v = new Voxels(w, dd, -1, 7);
        int d = dd - 1; // 건물 깊이 (맨 앞 줄은 표지판)
        int mid = d / 2;
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("polished_andesite", 0x848685));
        v.fill(1, -1, mid, w - 2, -1, mid, FLOOR);
        v.walls(0, 0, 0, w - 1, 2, d - 1, PANEL);
        v.walls(0, 0, 0, w - 1, 0, d - 1, BASE);
        gableRoof(v, w, d, 3, ROOF_BLUE);
        for (int i = 2; i < w - 2; i += 3) {
            v.set(i, 1, 0, GLASS);
            v.set(i, 1, d - 1, GLASS);
        }
        int door = w / 2;
        door(v, door, d - 1, "south", "spruce");
        v.set(door - 1, 1, d - 1, PANEL);
        v.set(door + 1, 1, d - 1, PANEL);
        // 돈방: 복도 양쪽, 5칸마다 철책
        for (int side = 0; side < 2; side++) {
            int jIn = side == 0 ? mid - 1 : mid + 1, jFar = side == 0 ? 1 : d - 2;
            int jl = Math.min(jIn, jFar), jh = Math.max(jIn, jFar);
            for (int i = 1; i < w - 1; i++) {
                boolean passage = side == 1 && Math.abs(i - door) <= 0;
                if (passage) {
                    continue;
                }
                if ((i - 1) % 5 == 0) {
                    v.fill(i, 0, jl, i, 1, jh, Block.of("iron_bars", 0x888888));
                    continue;
                }
                boolean gate = (i - 1) % 5 == 2;
                v.set(i, 0, jIn, gate ? Block.of("spruce_fence_gate[facing=" + (side == 0 ? "north" : "south") + ",in_wall=false,open=false,powered=false]", 0x725430)
                        : Block.of("iron_bars", 0x888888));
                if ((i - 1) % 5 == 1) {
                    v.set(i, 0, jFar, Block.of("composter[level=5]", 0x7A5A33));
                } else if ((i - 1) % 5 == 4) {
                    v.set(i, 0, jFar, Block.of("water_cauldron[level=3]", 0x3F76E4));
                }
            }
        }
        for (int i = door; i <= door; i++) {
            v.fill(i, 0, mid + 1, i, 1, d - 2, AIR);
            v.fill(i, -1, mid + 1, i, -1, d - 2, FLOOR);
        }
        // 환풍기 (끝 벽)
        for (int j = 2; j < d - 2; j += 4) {
            v.set(0, 1, j, Block.of("iron_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            v.set(w - 1, 1, j, Block.of("iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        for (int i = 3; i < w; i += 7) {
            v.set(i, 2, mid, Interior.LIGHT);
        }
        v.set(door + 2, 2, d, Blocks.wallSign("birch", "south", "red", false, "돈사", "방역 중", "관계자 외", "출입 금지"));
        v.connect();
        return v;
    }

    /**
     * 계사 (w × d, 길고 낮음): 흰 벽과 긴 띠창, 낮은 지붕. 깔짚 바닥, 가운데 통로, 뒤 벽 따라 산란 상자,
     * 앞쪽 줄에 모이통·물통. 정문은 남쪽 가운데.
     */
    static Voxels chickenHouse(int w, int dd, Random r) {
        Voxels v = new Voxels(w, dd, -1, 6);
        int d = dd - 1; // 건물 깊이 (맨 앞 줄은 표지판)
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("coarse_dirt", 0x77563B));
        int aisle = d / 2;
        v.fill(1, -1, aisle, w - 2, -1, aisle, FLOOR);
        v.walls(0, 0, 0, w - 1, 2, d - 1, PANEL);
        v.walls(0, 0, 0, w - 1, 0, d - 1, BASE);
        gableRoof(v, w, d, 3, ROOF);
        for (int i = 1; i < w - 1; i++) {
            if (i % 4 != 0) {
                v.set(i, 1, 0, GLASS);
                v.set(i, 1, d - 1, GLASS);
            }
        }
        int door = w / 2;
        door(v, door, d - 1, "south", "spruce");
        for (int i = 1; i < w - 1; i++) {
            // 산란 상자 (뒤 벽): 짚 위 선반
            v.set(i, 0, 1, HAY);
            v.set(i, 1, 1, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
            // 모이통·물통 줄 (앞쪽)
            if (i % 4 == 2 && Math.abs(i - door) > 1) {
                v.set(i, 0, d - 3, i % 8 == 2 ? Block.of("water_cauldron[level=3]", 0x3F76E4) : Block.of("composter[level=3]", 0x7A5A33));
            }
        }
        for (int i = 3; i < w; i += 7) {
            v.set(i, 2, aisle, Interior.LIGHT);
        }
        v.set(door + 2, 2, d, Blocks.wallSign("birch", "south", "black", false, "", "계사", "소독 후 출입"));
        v.connect();
        return v;
    }

    /** 농장 사무실 (조립식): 문 남쪽, 상담 카운터, 책상 둘, 소파, 방역 안내 */
    static Voxels office(int w, int d) {
        Voxels v = new Voxels(w, d, -1, 5);
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("polished_andesite", 0x848685));
        v.walls(0, 0, 0, w - 1, 2, d - 2, PANEL);
        v.fill(0, 3, 0, w - 1, 3, d - 2, Block.of("green_concrete", 0x495B24));
        v.fill(0, -1, d - 1, w - 1, -1, d - 1, FLOOR);
        int door = w / 2;
        door(v, door, d - 2, "south", "spruce");
        for (int i = 2; i < w - 2; i += 3) {
            if (Math.abs(i - door) > 1) {
                v.set(i, 1, d - 2, GLASS);
            }
            v.set(i, 1, 0, GLASS);
        }
        Frame f = Frame.of(v);
        v.fill(2, 0, d - 5, door - 2, 0, d - 5, Furniture.COUNTER);
        Furniture.desk(f, 3, 0, 2, "south");
        Furniture.desk(f, 6, 0, 2, "south");
        Furniture.sofa(f, new Random(1), w - 5, 0, d - 4, 3, "north");
        v.set(w - 2, 1, 2, Blocks.wallSign("birch", "west", "black", false, "방역 수칙", "출입 기록", "소독 필수"));
        v.set(door, 2, d / 2, Interior.LIGHT);
        v.set(door - 2, 2, d - 1, Blocks.wallSign("dark_oak", "south", "white", false, "", "준서 축산 농장", "사무실"));
        v.connect();
        return v;
    }

    private Barns() {
    }
}
