package com.junseo.citymap.buildings;

import java.util.Arrays;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 판상형 아파트 한 동 (길쭉한 남향 판).
 * <ul>
 *   <li>{@link Type#STAIR} 계단식 (1990년대 이후): 계단실 하나(꺾인 계단 + 엘리베이터 + 승강장 홀)를 두 세대가 같이 씀.
 *       세대 12×11 (3베이), 두 세대 + 코어 = 한 줄(라인). 코어는 북쪽으로 튀어나오고, 남쪽은 큰 창과 유리 난간 발코니.
 *       새 단지는 1층 필로티와 유리 공동현관, 옛 단지는 1층부터 세대이고 공동현관은 북쪽 홀 옆문.
 *       옥상에는 코어마다 경사 지붕 장식, 옆벽 꼭대기에 큰 동 번호</li>
 *   <li>{@link Type#CORRIDOR} 복도식 (1970년대 여의도 시범아파트): 북쪽에 긴 바깥 복도(난간벽), 복도 양 끝과 가운데에
 *       북쪽으로 튀어나온 계단실, 엘리베이터. 세대 9~10×8 (2베이), 남쪽 발코니는 난간벽 위에 샷시를 단 모양.
 *       크림색 페인트가 바래서 얼룩지고, 옥상에 물탱크, 옆벽에 큰 동 번호</li>
 * </ul>
 * 층고 4 (필로티 로비 5). 걸어서 모든 층과 옥상까지 계단으로 오릅니다. 정면은 남쪽(j = d-1).
 */
final class SlabApartment {
    enum Type { STAIR, CORRIDOR }

    /** 계단식 세대 안쪽 너비·깊이 (84㎡형) */
    static final int UW = AptUnit.UNIT84_W, UD = AptUnit.UNIT84_D;
    /** 복도식 세대 안쪽 너비·깊이 (59㎡형) */
    static final int CUW = AptUnit.UNIT59_W, CUD = AptUnit.UNIT59_D;
    /** 한 라인 너비 (세대 둘 + 칸막이) */
    static final int LINE = 2 * (UW + 1);

    private static final Block BUTTON = Block.of("polished_blackstone_button[face=wall,facing=south,powered=false]", 0x353038);

    /** 계단식 상자 너비 (라인 수 n) */
    static int stairWidth(int lines) {
        return LINE * lines + 1 + 4;
    }

    /** 계단식 상자 깊이 */
    static int stairDepth(boolean piloti) {
        int sd = Interior.stairDepth(piloti ? Floors.GROUND : Floors.HOME);
        int jf = sd + 5 + UD + 1;
        return 2 + jf + 1 + 1 + 4;
    }

    /** 복도식 상자 너비 (세대 수 n) */
    static int corridorWidth(int units) {
        return units * (CUW + 1) + 1 + 4;
    }

    /** 복도식 상자 깊이 */
    static int corridorDepth() {
        return 2 + 7 + 2 + 1 + CUD + 1 + 1 + 4;
    }

    private final Voxels v;
    private final AptUnit.Skin s;
    private final Random r;
    private final int ox = 2, oz = 2, floors, roof, mw;
    private final int[] levels;
    /** 지은 세대 평면 (검사용) */
    final java.util.List<AptUnit.Plan> plans = new java.util.ArrayList<>();

    private SlabApartment(int w, int d, int floors, int groundH, AptUnit.Skin s, Random r, int massW) {
        this.s = s;
        this.r = r;
        this.floors = Math.max(3, Math.min(60, floors));
        this.levels = Floors.levels(groundH, Floors.HOME, this.floors);
        this.roof = levels[this.floors];
        this.mw = massW;
        this.v = new Voxels(w, d, -1, roof + 14);
    }

    private void set(int mi, int y, int mj, Block b) {
        v.set(ox + mi, y, oz + mj, b);
    }

    private Block get(int mi, int y, int mj) {
        return v.get(ox + mi, y, oz + mj);
    }

    private void fill(int mi0, int y0, int mj0, int mi1, int y1, int mj1, Block b) {
        v.fill(ox + mi0, y0, oz + mj0, ox + mi1, y1, oz + mj1, b);
    }

    // ================================================================== 계단식

    /**
     * 계단식 판상형.
     *
     * @param lines  라인 수 (한 라인 = 계단실 하나 + 두 세대), 1..5
     * @param piloti 1층 필로티 (새 단지)
     */
    static Voxels stair(int w, int d, int lines, int floors, AptUnit.Skin s, boolean piloti, int dong, String complex, Random r) {
        return makeStair(w, d, lines, floors, s, piloti, dong, complex, r).v;
    }

    /** {@link #stair} 와 같고 세대 평면도 돌려줌 (검사용) */
    static SlabApartment makeStair(int w, int d, int lines, int floors, AptUnit.Skin s, boolean piloti, int dong, String complex, Random r) {
        lines = Math.max(1, Math.min(lines, (w - 5) / LINE));
        int mw = LINE * lines + 1;
        SlabApartment a = new SlabApartment(Math.max(w, mw + 4), Math.max(d, stairDepth(piloti)), floors,
                piloti ? Floors.GROUND : Floors.HOME, s, r, mw);
        a.buildStair(lines, piloti, dong, complex);
        a.v.connect();
        return a;
    }

    Voxels voxels() {
        return v;
    }

    int[] levels() {
        return levels;
    }

    private void buildStair(int lines, boolean piloti, int dong, String complex) {
        int sd = Interior.stairDepth(levels);
        int jb = sd + 5, jf = jb + UD + 1, east = mw - 1;
        int units = 2 * lines;
        Block wall = s.wall(), inner = AptUnit.WALL, cw = Interior.CORE_WALL;
        // 바닥판
        for (int k = 0; k <= floors; k++) {
            int y = levels[k] - 1;
            for (int mj = jb; mj <= jf; mj++) {
                for (int mi = 0; mi <= east; mi++) {
                    boolean edge = mi == 0 || mi == east || mj == jb || mj == jf;
                    set(mi, y, mj, k == 0 ? POLISHED_ANDESITE : k == floors ? s.roof() : edge ? s.band() : SMOOTH_STONE);
                }
            }
        }
        int first = piloti ? 1 : 0;
        for (int m = 0; m < lines; m++) {
            int sx = (2 * m + 1) * (UW + 1);
            // 코어: 계단(옥상까지) + 엘리베이터, 그 앞 홀 (i sx-5..sx+7, 두 세대 현관이 홀에 면함)
            int h0 = sx - 5, h1 = sx + 7;
            fill(h0 - 1, -1, 0, h1 + 1, -1, jb - 1, POLISHED_ANDESITE);
            for (int k = 1; k <= floors; k++) {
                fill(h0 - 1, levels[k] - 1, jb - 3, h1 + 1, levels[k] - 1, jb - 1, k == floors ? s.roof() : SMOOTH_STONE);
            }
            Interior.stairCore(Frame.facing(v, ox + sx, oz + jb - 5, "north"), levels, cw, "polished_andesite", 0x848685);
            Interior.elevator(new Frame(v, ox + sx + 2, oz + jb - 6, 0), Arrays.copyOf(levels, floors), cw);
            fill(sx + 1, -1, 0, sx + 4, roof + 3, jb - 8, cw);
            fill(sx + 1, roof, jb - 7, sx + 4, roof + 3, jb - 4, cw); // 기계실
            fill(sx - 5, roof + 3, 0, sx + 4, roof + 3, jb - 4, s.roof());
            for (int k = 0; k < floors; k++) {
                int level = levels[k], top = levels[k + 1] - 2;
                // 홀 벽 (양옆, 코어 밖 북쪽), 홀 바닥, 등, 엘리베이터 버튼
                fill(h0 - 1, level, jb - 3, h0 - 1, top, jb - 1, wall);
                fill(h1 + 1, level, jb - 3, h1 + 1, top, jb - 1, wall);
                fill(sx + 5, level, jb - 4, h1 + 1, top, jb - 4, wall);
                fill(h0, level, jb - 3, h1, top, jb - 1, AIR);
                Interior.floor(Frame.of(v), ox + h0, oz + jb - 3, ox + h1, oz + jb - 1, level, POLISHED_DIORITE);
                set(sx + 4, level + 1, jb - 3, BUTTON);
                set(sx - 1, top, jb - 2, Interior.LIGHT);
                set(sx + 6, top, jb - 2, Interior.LIGHT);
                // 홀 창 (동쪽 끝 북쪽 벽)
                if (k > 0) {
                    set(sx + 6, level + 1, jb - 4, s.glass());
                }
            }
            // 공동현관
            if (piloti) {
                int top = levels[1] - 2;
                fill(h0 - 1, 0, jb, h0 - 1, top, jb + 3, s.glass());
                fill(h1 + 1, 0, jb, h1 + 1, top, jb + 3, s.glass());
                fill(h0 - 1, 0, jb + 4, h1 + 1, top, jb + 4, s.glass());
                fill(h0 - 1, 0, jb, h0 - 1, 0, jb + 4, s.base());
                fill(h1 + 1, 0, jb, h1 + 1, 0, jb + 4, s.base());
                fill(h0 - 1, 0, jb + 4, h1 + 1, 0, jb + 4, s.base());
                fill(h0, -1, jb, h1, -1, jb + 3, POLISHED_DIORITE);
                fill(h0, 0, jb, h1, top, jb + 3, AIR);
                set(sx + 1, 0, jb + 4, AIR);
                set(sx + 2, 0, jb + 4, AIR);
                Kit.doubleDoor(Frame.of(v), ox + sx + 1, 0, oz + jb + 4, "birch", "south");
                for (int mj = jb; mj <= jb + 2; mj++) {
                    set(h0, 0, mj, IRON_BLOCK); // 우편함
                    set(h0, 1, mj, Block.of("iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
                }
                set(h1, 0, jb + 3, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
                set(sx + 1, top, jb + 1, Interior.LIGHT);
                set(sx - 2, 2, jb + 5, Blocks.wallSign("dark_oak", "south", "white", false, TowerApartment.signLines(complex, dong)));
            } else {
                // 북쪽 홀 옆문 (동쪽 벽) 과 차양, 동 표지판
                set(h1 + 1, 0, jb - 2, AIR);
                set(h1 + 1, 1, jb - 2, AIR);
                Interior.door(Frame.of(v), ox + h1 + 1, 0, oz + jb - 2, "birch", "east");
                fill(h1 + 2, 3, jb - 3, h1 + 3, 3, jb - 1, Kit.SLAB);
                fill(h1 + 2, -1, jb - 4, h1 + 4, -1, jb - 1, POLISHED_ANDESITE);
                set(h1 + 2, 2, jb - 1, Blocks.wallSign("dark_oak", "east", "white", false, TowerApartment.signLines(complex, dong)));
            }
        }
        // 세대 층
        for (int k = first; k < floors; k++) {
            int level = levels[k], top = level + 2;
            fill(0, level, jb, east, top, jb, wall);
            fill(0, level, jf, east, top, jf, wall);
            fill(0, level, jb, 0, top, jf, wall);
            fill(east, level, jb, east, top, jf, wall);
            for (int u = 1; u < units; u++) {
                fill(u * (UW + 1), level, jb + 1, u * (UW + 1), top, jf - 1, inner);
            }
            for (int u = 0; u < units; u++) {
                int x0 = u * (UW + 1) + 1;
                // 현관은 거실 칸의 코어 쪽 가장자리 (서쪽 세대 a 10, 동쪽 세대 a 6)
                plans.add(AptUnit.home(new Frame(v, ox + x0, oz + jb + 1, 0), r, UW, UD, level, u % 2 == 0 ? 10 : 6));
            }
            // 호수 표지판
            for (int m = 0; m < lines; m++) {
                int sx = (2 * m + 1) * (UW + 1);
                String no = (k + 1) + String.format("%02d", 2 * m + 1);
                String no2 = (k + 1) + String.format("%02d", 2 * m + 2);
                set(sx - 4, level + 1, jb - 1, Blocks.wallSign("birch", "north", "black", false, "", no + "호"));
                set(sx + 6, level + 1, jb - 1, Blocks.wallSign("birch", "north", "black", false, "", no2 + "호"));
            }
        }
        // 1층 필로티 기둥과 천장 등
        if (piloti) {
            int top = levels[1] - 2;
            for (int u = 0; u <= units; u++) {
                int mi = Math.min(east, u * (UW + 1));
                for (int mj : new int[]{jb, jb + 6, jf}) {
                    if (get(mi, 0, mj) == null) {
                        fill(mi, 0, mj, mi, top, mj, s.base());
                    }
                }
            }
            for (int mi = 3; mi < east; mi += 6) {
                for (int mj = jb + 3; mj < jf; mj += 6) {
                    if (get(mi, top, mj) == null) {
                        set(mi, top, mj, LANTERN_HANGING);
                    }
                }
            }
            for (int mi = 0; mi <= east; mi++) {
                for (int mj = jb; mj <= jf; mj++) {
                    if (get(mi, -1, mj) == POLISHED_ANDESITE && (mi % 6 == 0 || mj % 6 == 0)) {
                        set(mi, -1, mj, SMOOTH_STONE);
                    }
                }
            }
        }
        stairFacade(lines, jb, jf, first);
        stairRoof(lines, jb, jf, dong);
        yard(jf + 2);
    }

    private boolean partition(int mi, int y, int mj) {
        Block b = get(mi, y, mj);
        return b != null && (b.equals(AptUnit.WALL) || b.equals(Interior.CORE_WALL));
    }

    private void stairFacade(int lines, int jb, int jf, int first) {
        int east = mw - 1, units = 2 * lines;
        for (int k = first; k < floors; k++) {
            int level = levels[k];
            boolean base = k == first && s.base() != null && first == 0;
            // 남쪽: 바닥까지 내려오는 창, 칸막이 뒤는 벽
            for (int mi = 1; mi < east; mi++) {
                if (mi % (UW + 1) == 0 || partition(mi, level + 1, jf - 1)) {
                    continue;
                }
                set(mi, level, jf, base ? s.base() : s.frame());
                set(mi, level + 1, jf, s.glass());
                set(mi, level + 2, jf, s.glass());
            }
            // 발코니: 바닥판, 유리 난간, 세대 사이 핀
            for (int mi = 0; mi <= east; mi++) {
                set(mi, level - 1, jf + 1, s.band());
                if (mi % (UW + 1) == 0 || mi == east) {
                    fill(mi, level, jf + 1, mi, level + 2, jf + 1, s.accent());
                } else {
                    set(mi, level, jf + 1, s.rail());
                }
            }
            // 북쪽: 부엌·욕실 작은 창 (코어가 아닌 곳)
            for (int mi = 1; mi < east; mi++) {
                if (mi % (UW + 1) == 0 || partition(mi, level + 1, jb + 1) || get(mi, level + 1, jb - 1) != null) {
                    continue;
                }
                if (Math.floorMod(mi, UW + 1) % 4 != 2) {
                    set(mi, level + 1, jb, s.glass());
                    set(mi, level + 2, jb, s.glass());
                }
            }
            // 옆벽: 아래층만 작은 창 (위는 동 번호 자리)
            if (k < floors - 4) {
                for (int mj = jb + 2; mj < jf - 1; mj += 4) {
                    for (int side = 0; side < 2; side++) {
                        int mi = side == 0 ? 0 : east;
                        if (!partition(mi + (side == 0 ? 1 : -1), level + 1, mj)) {
                            set(mi, level + 1, mj, s.glass());
                        }
                    }
                }
            }
        }
        for (int mi = 0; mi <= east; mi++) {
            set(mi, roof - 1, jf + 1, s.band());
        }
    }

    /** 옥상: 난간, 코어마다 경사 지붕 장식, 옆벽 꼭대기 동 번호 */
    private void stairRoof(int lines, int jb, int jf, int dong) {
        int east = mw - 1;
        for (int mi = 0; mi <= east; mi++) {
            set(mi, roof, jf + 1, s.wall());
            if (get(mi, roof, jb - 1) == null) {
                set(mi, roof, jb, s.wall());
            }
        }
        for (int mj = jb; mj <= jf + 1; mj++) {
            set(0, roof, mj, s.wall());
            set(east, roof, mj, s.wall());
        }
        // 지붕 장식: 코어 위로 남북 방향 박공 (양 끝은 세모 벽)
        Block up = Block.of("deepslate_tile_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0x363637);
        Block down = Block.of("deepslate_tile_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", 0x363637);
        Block ridge = Block.of("deepslate_tile_slab[type=bottom,waterlogged=false]", 0x363637);
        for (int m = 0; m < lines; m++) {
            int sx = (2 * m + 1) * (UW + 1);
            int a0 = sx - 6, a1 = sx + 8, half = (a1 - a0) / 2;
            // 옥상 홀 둘레 난간
            for (int mj = jb - 3; mj <= jb - 1; mj++) {
                set(a0, roof, mj, s.wall());
                set(a1, roof, mj, s.wall());
            }
            fill(a0 + 1, roof, jb, a1 - 1, roof, jb, AIR);
            fill(sx + 5, roof, jb - 4, a1, roof, jb - 4, s.wall());
            fill(a0, roof + 3, 0, a1, roof + 3, jf + 1, s.band());
            for (int t = 0; t <= half; t++) {
                int y = roof + 4 + t;
                for (int mj = 0; mj <= jf + 1; mj++) {
                    if (a0 + t < a1 - t) {
                        set(a0 + t, y, mj, up);
                        set(a1 - t, y, mj, down);
                    } else {
                        set(a0 + t, y, mj, ridge);
                    }
                }
                if (a0 + t + 1 <= a1 - t - 1) {
                    fill(a0 + t + 1, y, 0, a1 - t - 1, y, 0, s.accent());
                    fill(a0 + t + 1, y, jf + 1, a1 - t - 1, y, jf + 1, s.accent());
                }
            }
            // 박공 받침 기둥과 남쪽 띠
            for (int mj = jb + 3; mj <= jf + 1; mj += 4) {
                fill(a0, roof + 1, mj, a0, roof + 2, mj, s.wall());
                fill(a1, roof + 1, mj, a1, roof + 2, mj, s.wall());
            }
            fill(a0, roof + 1, jf + 1, a1, roof + 2, jf + 1, s.wall());
        }
        // 옆벽 동 번호 (꼭대기 층 높이)
        String no = String.valueOf(dong);
        int span = jf - jb + 1;
        Boolean big = AptUnit.fits(no, span);
        if (big != null) {
            int width = AptUnit.numberWidth(no, big);
            int mid = (jb + jf) / 2;
            int yTop = roof - 2;
            AptUnit.number(v, no, big, ox, yTop, oz + mid - width / 2, 0, 1, s.number());
            AptUnit.number(v, no, big, ox + east, yTop, oz + mid + width / 2, 0, -1, s.number());
        }
    }

    /** 앞마당: 화단 회양목과 보도, 둘레 잔디 */
    private void yard(int mj0) {
        int j0 = oz + mj0;
        v.fill(0, -1, j0, v.w - 1, -1, v.d - 1, LIGHT_GRAY_CONCRETE);
        for (int i = 1; i < v.w - 1; i++) {
            if (Math.floorMod(i - ox, 26) > 3) {
                v.set(i, -1, j0 + 1, GRASS);
                v.set(i, 0, j0 + 1, Kit.BOX_HEDGE);
            }
        }
        for (int j = 0; j < v.d; j++) {
            for (int i = 0; i < v.w; i++) {
                if (v.get(i, -1, j) == null) {
                    v.set(i, -1, j, GRASS);
                }
            }
        }
    }

    // ================================================================== 복도식

    /**
     * 복도식 판상형 (1970년대). 세대는 uw(9~10) 너비, units 줄.
     *
     * @param faded 페인트가 바래서 얼룩진 모습
     */
    static Voxels corridor(int w, int d, int units, int floors, AptUnit.Skin s, boolean faded, int dong, String complex, Random r) {
        return makeCorridor(w, d, units, floors, s, faded, dong, complex, r).v;
    }

    /** {@link #corridor} 와 같고 세대 평면도 돌려줌 (검사용) */
    static SlabApartment makeCorridor(int w, int d, int units, int floors, AptUnit.Skin s, boolean faded, int dong, String complex, Random r) {
        int uw = CUW;
        units = Math.max(3, Math.min(units, (w - 5) / (uw + 1)));
        int mw = units * (uw + 1) + 1;
        SlabApartment a = new SlabApartment(Math.max(w, mw + 4), Math.max(d, corridorDepth()), floors, Floors.HOME, s, r, mw);
        a.buildCorridor(units, uw, faded, dong, complex);
        a.v.connect();
        return a;
    }

    private void buildCorridor(int units, int uw, boolean faded, int dong, String complex) {
        // 줄: 계단실 j 0..6 (북쪽으로 튀어나옴), 복도 난간 j 6, 복도 j 7..8, 세대 북쪽 벽 j 9, 세대 10..9+CUD, 남쪽 벽, 발코니
        int c0 = 7, jb = c0 + 2, jf = jb + CUD + 1, east = mw - 1;
        Block wall = s.wall(), inner = AptUnit.WALL, cw = s.wall();
        for (int k = 0; k <= floors; k++) {
            int y = levels[k] - 1;
            for (int mj = c0; mj <= jf; mj++) {
                for (int mi = 0; mi <= east; mi++) {
                    boolean edge = mi == 0 || mi == east || mj == c0 || mj == jf;
                    set(mi, y, mj, k == 0 ? POLISHED_ANDESITE : k == floors ? s.roof() : edge ? s.band() : SMOOTH_STONE);
                }
            }
        }
        // 계단실: 양 끝 (복도 끝 세대 앞) 과 길면 가운데. 엘리베이터는 가운데 계단 옆
        int[] stairs = units >= 8 ? new int[]{5, mw / 2 + 2, east - 1} : new int[]{5, east - 1};
        int lift = units >= 8 ? mw / 2 - 4 : mw / 2 - 1;
        for (int sx : stairs) {
            Interior.stairCore(Frame.facing(v, ox + sx, oz + c0 - 2, "north"), levels, cw, "stone_brick", 0x7A7979);
            fill(sx - 5, roof + 3, c0 - 8, sx + 1, roof + 3, c0 - 1, s.roof());
            // 옥상 물탱크 (계단실 위)
            fill(sx - 4, roof + 4, c0 - 7, sx, roof + 6, c0 - 3, wall);
            fill(sx - 4, roof + 7, c0 - 7, sx, roof + 7, c0 - 3, s.band());
        }
        Interior.elevator(new Frame(v, ox + lift, oz + c0 - 3, 0), Arrays.copyOf(levels, floors), cw);
        fill(lift - 1, roof, c0 - 4, lift + 2, roof + 3, c0 - 1, cw);
        for (int k = 0; k < floors; k++) {
            int level = levels[k], top = level + 2;
            // 복도 난간벽 (북쪽): 계단실·엘리베이터 출입구는 트임
            for (int mi = 0; mi <= east; mi++) {
                Block here = get(mi, level, c0 - 1);
                if (here == null) {
                    set(mi, level, c0 - 1, k == 0 ? null : wall);
                    set(mi, level - 1, c0 - 1, k == 0 ? null : s.band());
                }
            }
            fill(0, level, c0, east, top, c0 + 1, AIR);
            set(lift + 2, level + 1, c0, BUTTON);
            if (k > 0) {
                fill(0, level, c0, 0, level, c0 + 1, wall);
                fill(east, level, c0, east, level, c0 + 1, wall);
            }
            for (int mi = 3; mi < east; mi += 6) {
                set(mi, top, c0 + 1, Interior.LIGHT);
            }
            // 세대 벽
            fill(0, level, jb, east, top, jb, wall);
            fill(0, level, jf, east, top, jf, wall);
            fill(0, level, jb, 0, top, jf, wall);
            fill(east, level, jb, east, top, jf, wall);
            for (int u = 1; u < units; u++) {
                fill(u * (uw + 1), level, jb + 1, u * (uw + 1), top, jf - 1, inner);
            }
            for (int u = 0; u < units; u++) {
                int x0 = u * (uw + 1) + 1;
                int entry = uw / 2;
                plans.add(AptUnit.home(new Frame(v, ox + x0, oz + jb + 1, 0), r, uw, CUD, level, entry));
                // 현관 옆 호수 표지판, 복도 쪽 작은 창 (부엌)
                set(x0 + entry + 1, level + 1, c0 + 1, Blocks.wallSign("birch", "north", "black", false, "", (k + 1) + String.format("%02d", u + 1) + "호"));
                if (!partition(x0 + entry - 2, level + 1, jb + 1)) {
                    set(x0 + entry - 2, level + 1, jb, s.glass());
                }
            }
        }
        // 복도 끝 (옆벽 쪽) 은 트여 있고, 1층 복도는 땅과 이어짐
        fill(-1, -1, c0 - 1, east + 1, -1, c0 + 1, POLISHED_ANDESITE);
        // 남쪽: 창, 발코니 (난간벽 + 샷시)
        for (int k = 0; k < floors; k++) {
            int level = levels[k];
            for (int mi = 1; mi < east; mi++) {
                if (mi % (uw + 1) == 0 || partition(mi, level + 1, jf - 1)) {
                    continue;
                }
                set(mi, level + 1, jf, s.glass());
                set(mi, level + 2, jf, Math.floorMod(mi, uw + 1) % 3 == 0 ? wall : s.glass());
            }
            for (int mi = 0; mi <= east; mi++) {
                boolean fin = mi % (uw + 1) == 0;
                if (k > 0) {
                    set(mi, level - 1, jf + 1, s.band());
                }
                set(mi, level, jf + 1, fin ? s.band() : wall);
                set(mi, level + 1, jf + 1, fin ? wall : s.rail());
                set(mi, level + 2, jf + 1, fin ? wall : s.rail());
            }
        }
        for (int mi = 0; mi <= east; mi++) {
            set(mi, roof - 1, jf + 1, s.band());
        }
        // 옥상 난간
        for (int mi = 0; mi <= east; mi++) {
            set(mi, roof, c0, s.band());
            set(mi, roof, jf + 1, s.band());
        }
        for (int mj = c0; mj <= jf + 1; mj++) {
            set(0, roof, mj, s.band());
            set(east, roof, mj, s.band());
        }
        // 옆벽 동 번호
        String no = String.valueOf(dong);
        int span = jf + 1 - (c0 - 1) + 1;
        Boolean big = AptUnit.fits(no, span);
        if (big != null) {
            int width = AptUnit.numberWidth(no, big);
            int mid = (c0 - 1 + jf + 1) / 2;
            int yTop = roof - 3;
            // 번호 둘레는 창 없는 벽
            fill(0, yTop - AptUnit.numberHeight(big), c0 - 1, 0, yTop + 1, jf + 1, wall);
            fill(east, yTop - AptUnit.numberHeight(big), c0 - 1, east, yTop + 1, jf + 1, wall);
            AptUnit.number(v, no, big, ox, yTop, oz + mid - width / 2, 0, 1, s.number());
            AptUnit.number(v, no, big, ox + east, yTop, oz + mid + width / 2, 0, -1, s.number());
        }
        if (faded) {
            fade(jf);
        }
        // 1층 복도 끝 동 표지판
        set(-1, 2, c0 + 2, Blocks.wallSign("dark_oak", "west", "white", false, TowerApartment.signLines(complex, dong)));
        yard(jf + 2);
    }

    /** 바랜 페인트: 벽 여기저기 얼룩, 창 밑 빗물 자국 */
    private void fade(int jf) {
        Block[] stains = {Block.of("cut_sandstone", 0xD9CD9F), Block.of("white_terracotta", 0xD1B2A1),
                Block.of("smooth_sandstone", 0xDFD6AA)};
        for (int y = 0; y < roof; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    if (b != null && b.equals(s.wall()) && r.nextInt(9) == 0) {
                        v.set(i, y, j, stains[r.nextInt(stains.length)]);
                    }
                }
            }
        }
    }
}
