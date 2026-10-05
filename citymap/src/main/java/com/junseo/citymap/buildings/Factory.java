package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 공장: 샌드위치 패널 벽, 파란(또는 회색) 지붕, 큰 셔터 문, 앞마당과 철망 울타리.
 * 큰 공장은 톱니 지붕과 벽돌 굴뚝, 2층 사무동이 붙습니다.
 * abandoned 이면 금 간 벽돌, 깨진 창, 거미줄 (암시장 후보 폐공장).
 */
final class Factory {
    private static final Block[] PANELS = {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, IRON_BLOCK, SMOOTH_STONE, WHITE_CONCRETE};
    private static final Block[] ROOFS = {BLUE_CONCRETE, BLUE_CONCRETE, CYAN_CONCRETE, LIGHT_GRAY_CONCRETE, LIGHT_BLUE_TERRACOTTA};

    static Voxels build(int w, int d, Random r, boolean abandoned) {
        int yard = Math.max(5, Math.min(10, d / 4));
        int si0 = 1, si1 = w - 2, sj0 = 1, sj1 = d - 1 - yard; // 공장 건물
        int wallH = 8 + r.nextInt(4);
        boolean saw = sj1 - sj0 >= 18 && r.nextInt(10) < 5;
        boolean chimney = abandoned || (w * d > 1100 && r.nextInt(10) < 5);
        Voxels v = new Voxels(w, d, -1, wallH + 26);

        Block panel = abandoned ? BRICKS : PANELS[r.nextInt(PANELS.length)];
        Block base = abandoned ? STONE_BRICKS : GRAY_CONCRETE;
        Block column = abandoned ? CRACKED_STONE_BRICKS : POLISHED_ANDESITE;
        Block roof = abandoned ? LIGHT_GRAY_CONCRETE : ROOFS[r.nextInt(ROOFS.length)];

        // 바닥: 마당은 콘크리트, 건물 안은 매끈한 돌
        v.fill(0, -1, 0, w - 1, -1, d - 1, abandoned ? COBBLESTONE : SMOOTH_STONE);
        v.fill(si0, -1, sj0, si1, -1, sj1, abandoned ? MOSSY_COBBLESTONE : POLISHED_ANDESITE);

        // 벽: 아래 2줄은 콘크리트, 위는 패널, 6칸마다 기둥
        v.walls(si0, 0, sj0, si1, 1, sj1, base);
        v.walls(si0, 2, sj0, si1, wallH, sj1, panel);
        for (int i = si0; i <= si1; i += 6) {
            v.fill(i, 0, sj0, i, wallH, sj0, column);
            v.fill(i, 0, sj1, i, wallH, sj1, column);
        }
        for (int j = sj0; j <= sj1; j += 6) {
            v.fill(si0, 0, j, si0, wallH, j, column);
            v.fill(si1, 0, j, si1, wallH, j, column);
        }
        // 높은 창 띠
        Block pane = abandoned ? GLASS_PANE : GRAY_PANE;
        for (int y = wallH - 3; y <= wallH - 2; y++) {
            for (int i = si0 + 1; i < si1; i++) {
                if ((i - si0) % 6 != 0) {
                    v.set(i, y, sj0, broken(r, abandoned, pane));
                    v.set(i, y, sj1, broken(r, abandoned, pane));
                }
            }
            for (int j = sj0 + 1; j < sj1; j++) {
                if ((j - sj0) % 6 != 0) {
                    v.set(si0, y, j, broken(r, abandoned, pane));
                    v.set(si1, y, j, broken(r, abandoned, pane));
                }
            }
        }

        // 정면 셔터 문 (가로줄 무늬)과 사람 문
        int doors = Math.max(1, Math.min(3, (si1 - si0) / 14));
        for (int n = 0; n < doors; n++) {
            int c = si0 + (si1 - si0) * (2 * n + 1) / (2 * doors);
            for (int i = c - 2; i <= c + 2; i++) {
                for (int y = 0; y <= 4; y++) {
                    v.set(i, y, sj1, abandoned && n == 0 ? AIR : y % 2 == 0 ? IRON_BLOCK : LIGHT_GRAY_CONCRETE);
                }
            }
            v.fill(c - 3, 5, sj1, c + 3, 5, sj1, YELLOW_CONCRETE); // 셔터 위 안전띠
        }
        int pd = si0 + 3;
        v.set(pd, 0, sj1, door("iron", "south", false));
        v.set(pd, 1, sj1, door("iron", "south", true));
        // 회사 간판: 정면 벽 위쪽에 판, 가운데 표지판 글씨
        int mid = (si0 + si1) / 2;
        Block boardBlock = abandoned ? LIGHT_GRAY_CONCRETE : r.nextBoolean() ? BLUE_CONCRETE : WHITE_CONCRETE;
        v.fill(mid - 5, wallH - 1, sj1, mid + 5, wallH, sj1, boardBlock);
        if (!abandoned) {
            v.set(mid, wallH, sj1 + 1, Blocks.wallSign(boardBlock == BLUE_CONCRETE ? "dark_oak" : "birch", "south",
                    boardBlock == BLUE_CONCRETE ? "white" : "blue", false, "", KoreanNames.factory(r)));
        }

        // 지붕
        if (saw) {
            // 톱니 지붕: 7칸마다 남쪽으로 올라갔다가 유리면(채광창)으로 뚝 떨어짐
            for (int j = sj0; j <= sj1; j++) {
                int t = (j - sj0) % 7;
                boolean side;
                for (int i = si0; i <= si1; i++) {
                    side = i == si0 || i == si1;
                    if (t == 6 || j == sj1) {
                        v.fill(i, wallH + 1, j, i, wallH + 4, j, side || j == sj1 ? panel : GLASS);
                        continue;
                    }
                    int rise = (t * 4 + 3) / 6;
                    v.set(i, wallH + 1 + rise, j, roof);
                    if (side) {
                        v.fill(i, wallH + 1, j, i, wallH + rise, j, panel);
                    }
                }
            }
        } else {
            // 완만한 박공 지붕: 긴 변을 따라 용마루, 3칸마다 한 칸씩 올라감
            boolean alongI = si1 - si0 >= sj1 - sj0;
            int half = alongI ? (sj1 - sj0) / 2 : (si1 - si0) / 2;
            for (int j = sj0; j <= sj1; j++) {
                for (int i = si0; i <= si1; i++) {
                    int dist = alongI ? Math.min(j - sj0, sj1 - j) : Math.min(i - si0, si1 - i);
                    int rise = Math.min(half, dist) / 3;
                    v.set(i, wallH + 1 + rise, j, roof);
                    boolean gableEnd = alongI ? (i == si0 || i == si1) : (j == sj0 || j == sj1);
                    if (gableEnd && rise > 0) {
                        v.fill(i, wallH + 1, j, i, wallH + rise, j, panel);
                    }
                }
            }
        }
        // 지붕 채광·조명 (위에서 보면 밝은 점)
        for (int j = sj0 + 3; j < sj1; j += 6) {
            for (int i = si0 + 3; i < si1; i += 6) {
                int y = topOf(v, i, j, wallH);
                if (y > 0 && !abandoned) {
                    v.set(i, y, j, SEA_LANTERN);
                }
            }
        }
        // 폐공장: 지붕 구멍
        if (abandoned) {
            for (int n = 0; n < 6; n++) {
                int i = si0 + 2 + r.nextInt(Math.max(1, si1 - si0 - 4)), j = sj0 + 2 + r.nextInt(Math.max(1, sj1 - sj0 - 4));
                int y = topOf(v, i, j, wallH);
                v.fill(i, y, j, i + 1, y, j + 1, AIR);
            }
        }

        // 안: 기계와 짐
        for (int j = sj0 + 3; j < sj1 - 3; j += 5) {
            for (int i = si0 + 3; i < si1 - 3; i += 4) {
                int pick = r.nextInt(abandoned ? 5 : 8);
                Block b = switch (pick) {
                    case 0 -> BARREL;
                    case 1 -> abandoned ? COBWEB : BLAST_FURNACE;
                    case 2 -> abandoned ? COBWEB : STONECUTTER;
                    case 3 -> CAULDRON;
                    case 4 -> abandoned ? AIR : IRON_BLOCK;
                    case 5 -> ANVIL;
                    default -> null;
                };
                if (b != null) {
                    v.set(i, 0, j, b);
                    if (b == BARREL && r.nextBoolean()) {
                        v.set(i, 1, j, BARREL);
                    }
                }
            }
        }
        if (abandoned) {
            for (int n = 0; n < 14; n++) {
                v.setIfEmpty(si0 + 1 + r.nextInt(si1 - si0 - 1), wallH - 1 - r.nextInt(3), sj0 + 1 + r.nextInt(sj1 - sj0 - 1), COBWEB);
            }
        } else {
            // 안쪽 매달린 등
            for (int j = sj0 + 3; j < sj1; j += 6) {
                for (int i = si0 + 3; i < si1; i += 6) {
                    v.set(i, wallH - 1, j, SEA_LANTERN);
                }
            }
        }

        // 2층 사무동 (마당 한쪽)
        if (yard >= 6 && si1 - si0 >= 20) {
            int oi0 = si1 - 9, oi1 = si1, oj0 = sj1 + 1, oj1 = Math.min(d - 3, sj1 + yard - 2);
            Block ow = abandoned ? CRACKED_STONE_BRICKS : WHITE_CONCRETE;
            v.fill(oi0, -1, oj0, oi1, -1, oj1, SMOOTH_STONE);
            v.walls(oi0, 0, oj0, oi1, 6, oj1, ow);
            v.fill(oi0, 3, oj0, oi1, 3, oj1, ow);
            v.fill(oi0, 7, oj0, oi1, 7, oj1, abandoned ? COBBLESTONE : SMOOTH_STONE);
            v.fill(oi0 + 1, 0, oj0 + 1, oi1 - 1, 2, oj1 - 1, AIR);
            v.fill(oi0 + 1, 4, oj0 + 1, oi1 - 1, 6, oj1 - 1, AIR);
            for (int i = oi0 + 1; i < oi1; i++) {
                if (i % 3 != 0) {
                    v.fill(i, 1, oj1, i, 1, oj1, broken(r, abandoned, GLASS_PANE));
                    v.fill(i, 5, oj1, i, 5, oj1, broken(r, abandoned, GLASS_PANE));
                }
            }
            v.set(oi0 + 2, 0, oj1, door("iron", "south", false));
            v.set(oi0 + 2, 1, oj1, door("iron", "south", true));
            v.set(oi0 + 5, 2, (oj0 + oj1) / 2, SEA_LANTERN);
            v.set(oi0 + 5, 6, (oj0 + oj1) / 2, SEA_LANTERN);
            for (int y = 0; y <= 7; y++) {
                v.set(oi1 - 1, y, oj0 + 1, LADDER);
            }
        }

        // 굴뚝
        if (chimney) {
            double ci = si0 + 3.5, cj = sj0 + 3.5;
            int ch = wallH + 14 + r.nextInt(8);
            v.cylinder(ci, cj, 2.2, 0, ch, BRICKS);
            v.cylinder(ci, cj, 1.2, 1, ch, AIR);
            v.tube(ci, cj, 2.2, 1, ch - 3, ch - 2, abandoned ? BRICKS : RED_CONCRETE);
            v.tube(ci, cj, 2.2, 1, ch - 5, ch - 4, abandoned ? BRICKS : WHITE_CONCRETE);
        }

        // 철망 울타리 (마당 둘레, 정면 가운데 출입구)
        int gate = (w - 1) / 2;
        for (int i = 0; i < w; i++) {
            if (Math.abs(i - gate) > 4) {
                fence(v, i, d - 1);
            }
        }
        for (int j = sj1 + 1; j < d; j++) {
            fence(v, 0, j);
            fence(v, w - 1, j);
        }
        // 마당: 주차선과 팔레트 짐
        for (int i = 3; i < Math.min(w - 12, 3 + 3 * 5); i += 3) {
            v.fill(i, -1, sj1 + 2, i, -1, d - 3, WHITE_CONCRETE);
        }
        for (int n = 0; n < 3; n++) {
            int i = 2 + r.nextInt(Math.max(1, w - 4)), j = sj1 + 2 + r.nextInt(Math.max(1, yard - 3));
            v.setIfEmpty(i, 0, j, r.nextBoolean() ? BARREL : HAY);
        }
        v.connect();
        return v;
    }

    private static void fence(Voxels v, int i, int j) {
        v.set(i, 0, j, SMOOTH_STONE);
        v.set(i, 1, j, IRON_BARS);
        v.set(i, 2, j, IRON_BARS);
    }

    private static Block broken(Random r, boolean abandoned, Block pane) {
        return abandoned && r.nextInt(3) == 0 ? AIR : pane;
    }

    /** (i, j) 에서 위에서 처음 만나는 지붕 블록의 y */
    private static int topOf(Voxels v, int i, int j, int from) {
        for (int y = v.y0 + v.h - 1; y > from; y--) {
            if (v.solid(i, y, j)) {
                return y;
            }
        }
        return -1;
    }

    private Factory() {
    }
}
