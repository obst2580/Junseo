package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 한국 아파트 한 동 (복도식 판상형, 주공아파트처럼).
 * <ul>
 *   <li>남쪽(정면)으로 세대가 줄지어 있고, 북쪽에 바깥 복도(난간)가 길게 이어집니다.</li>
 *   <li>서쪽 끝(넓으면 양 끝)에 꺾인 계단실, 복도 가운데 북쪽에 엘리베이터.</li>
 *   <li>세대마다 현관·욕실·부엌·침실·거실({@link Rooms#home}), 남쪽 발코니 창과 난간.</li>
 *   <li>층고 4칸(아파트 층고 약 2.8m), 1층도 세대. 옥상 난간·물탱크실, 공동현관에 동 번호 표지판.</li>
 * </ul>
 */
final class Apartment {
    static final int UNIT = 10;      // 세대 폭 (칸막이 포함)
    static final int UNIT_DEPTH = 10; // 세대 안쪽 깊이
    static final int CORE = 7;       // 계단실 폭 (벽 포함)

    /** {벽, 띠, 포인트(옆벽 위 색 띠)} */
    private static final Block[][] SKINS = {
            {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, Block.of("light_blue_terracotta", 0x716C89)},
            {SMOOTH_QUARTZ, WHITE_TERRACOTTA, Block.of("cyan_terracotta", 0x565B5B)},
            {WHITE_TERRACOTTA, SMOOTH_STONE, Block.of("orange_terracotta", 0xA15325)},
            {Block.of("light_gray_concrete", 0x7D7D73), WHITE_CONCRETE, Block.of("blue_terracotta", 0x4A3B5B)},
            {SANDSTONE, SMOOTH_STONE, Block.of("brown_terracotta", 0x4D3323)},
    };

    /** 이 크기 땅(정면 너비 w)에 들어가는 세대 수 */
    static int unitsFor(int w) {
        return Math.max(2, (w - CORE - 2) / UNIT);
    }

    /** 상자 깊이: 엘리베이터 3 + 복도 2 + 벽 + 세대 + 벽 + 발코니 + 앞마당 */
    static int depth() {
        return 5 + 2 + 1 + UNIT_DEPTH + 1 + 1 + 1;
    }

    /**
     * @param units  세대 수 (한 층)
     * @param floors 층 수
     * @param dong   동 번호 (예: 101)
     */
    static Voxels build(int w, int d, int units, int floors, int dong, String complex, Random r) {
        Block[] skin = SKINS[r.nextInt(SKINS.length)];
        Block wall = skin[0], band = skin[1], accent = skin[2];
        int[] levels = Floors.levels(Floors.HOME, Floors.HOME, floors);
        int roof = levels[floors];
        Voxels v = new Voxels(w, d, -1, roof + 6);
        // 줄: 엘리베이터 승강로 j 1..4, 복도 c0..c0+1, 뒷벽 jb, 세대 jb+1..jf-1, 앞벽 jf, 발코니 jf+1
        int c0 = 5, jb = c0 + 2, jf = jb + UNIT_DEPTH + 1;
        int x0 = 0, ux0 = CORE, ux1 = ux0 + units * UNIT; // 세대 칸 [ux0, ux1), 마지막 칸막이 = 동쪽 벽 ux1
        int x1 = Math.min(w - 1, ux1);
        Frame f = Frame.of(v);

        // 바닥판·벽
        for (int k = 0; k <= floors; k++) {
            int y = levels[k] - 1;
            v.fill(ux0, y, jb, x1, y, jf, k == 0 ? POLISHED_ANDESITE : k == floors ? SMOOTH_STONE : band);
            v.fill(ux0, y, c0, x1, y, c0 + 1, k == 0 ? POLISHED_ANDESITE : SMOOTH_STONE); // 복도 바닥
            if (k > 0 && k < floors) {
                v.fill(ux0, y, jf + 1, x1 - 1, y, jf + 1, band); // 발코니 바닥
            }
        }
        v.walls(ux0, 0, jb, x1, roof - 1, jf, wall);
        // 칸막이 벽 (세대 사이)
        for (int u = 1; u < units; u++) {
            v.fill(ux0 + u * UNIT, 0, jb, ux0 + u * UNIT, roof - 1, jf, wall);
        }

        // 세대마다: 실내, 앞 발코니 창, 뒤 창
        for (int k = 0; k < floors; k++) {
            int L = levels[k];
            for (int u = 0; u < units; u++) {
                int a = ux0 + u * UNIT + 1;
                Rooms.home(new Frame(v, a, jb + 1, 0), r, UNIT - 1, UNIT_DEPTH, L, Floors.HOME, 1);
                // 앞: 거실·침실 큰 창 (발코니 샷시)
                for (int i = a; i < a + UNIT - 1; i++) {
                    v.set(i, L, jf, i == a + 3 ? wall : band);
                    v.set(i, L + 1, jf, GLASS_PANE);
                    v.set(i, L + 2, jf, i == a + 3 ? wall : GLASS_PANE);
                    if (k > 0) {
                        v.set(i, L, jf + 1, Block.of("white_stained_glass_pane", 0xF0F0F0)); // 발코니 난간
                    }
                }
                // 뒤: 작은 창 (부엌·욕실), 현관문은 Rooms.home 이 뒷벽에 냄
                v.set(a + 5, L + 1, jb, GLASS_PANE);
                v.set(a + 6, L + 1, jb, GLASS_PANE);
                v.set(a + 2, L + 2, jb, GLASS_PANE);
                // 실외기 (발코니 끝)
                if (k > 0 && r.nextInt(3) == 0) {
                    v.set(a + UNIT - 2, L, jf + 1, SMOOTH_QUARTZ);
                }
            }
            // 복도 난간 (북쪽): 1층은 트여 있음 (공동 출입)
            if (k > 0) {
                v.fill(ux0, L, c0 - 1, x1, L, c0 - 1, wall);
                v.fill(ux0, L + 1, c0 - 1, x1, L + 1, c0 - 1, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
                v.fill(ux0, L - 1, c0 - 1, x1, L - 1, c0 - 1, band);
            }
            if (k > 0) {
                v.fill(x1, L, c0, x1, L + 1, c0 + 1, wall); // 복도 끝 막음
            }
            // 복도 등
            for (int i = ux0 + 3; i < x1; i += 6) {
                v.set(i, L + 2, c0 + 1, Interior.LIGHT);
            }
        }
        // 옆벽 꼭대기 색 띠 (아파트 상징색)
        for (int y = roof - 6; y < roof; y++) {
            for (int j = jb; j <= jf; j++) {
                if ((y + j) % 7 < 2) {
                    v.set(x1, y, j, accent);
                }
            }
        }

        // 계단실 (서쪽 끝, 출입구가 동쪽 복도를 봄)
        Frame sf = new Frame(v, CORE - 2, c0, 1);
        Interior.stairCore(sf, levels, wall, "polished_andesite", 0x848685);
        v.fill(0, roof + 3, c0 - 1, CORE - 1, roof + 3, c0 + 5, SMOOTH_STONE);
        // 동 번호 (계단실 북쪽 벽, 1층 복도 입구 옆)
        v.set(1, 1, c0 - 2, Blocks.wallSign("dark_oak", "north", "white", false, complex, dong + "동"));

        // 엘리베이터 (복도 가운데, 북쪽으로 튀어나옴)
        int ei = ux0 + (units / 2) * UNIT - 1;
        int[] liftLevels = java.util.Arrays.copyOf(levels, floors);
        Interior.elevator(new Frame(v, ei, c0 - 3, 0), liftLevels, wall);
        v.fill(ei - 1, roof, c0 - 4, ei + 2, roof + 3, c0 - 1, wall);

        // 옥상: 난간, 물탱크실
        for (int i = ux0; i <= x1; i++) {
            v.set(i, roof, jb, wall);
            v.set(i, roof, jf, wall);
        }
        for (int j = jb; j <= jf; j++) {
            v.set(x1, roof, j, wall);
            v.set(ux0, roof, j, wall);
        }
        int ti = ux0 + (units * UNIT) * 2 / 3;
        v.fill(ti, roof, jb + 2, ti + 6, roof + 3, jb + 6, wall);
        v.fill(ti, roof + 4, jb + 2, ti + 6, roof + 4, jb + 6, band);
        // 앞마당: 화단과 1층 앞 보도
        v.fill(0, -1, jf + 1, w - 1, -1, d - 1, LIGHT_GRAY_CONCRETE);
        for (int i = ux0 + 1; i < x1; i += 3) {
            v.set(i, 0, jf + 2 < d ? jf + 2 : d - 1, Block.of("oak_leaves[persistent=true]", 0x4F7F2A)); // 화단 회양목
        }
        v.connect();
        return v;
    }

    private Apartment() {
    }
}
