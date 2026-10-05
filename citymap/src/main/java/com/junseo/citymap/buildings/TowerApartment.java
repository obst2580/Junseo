package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 타워형 아파트 한 동 (2000년대 이후 잠실·대치·도곡·공덕 단지의 탑상형).
 * <ul>
 *   <li>한 층에 네 세대가 가운데 코어를 둘러쌉니다: 남향 두 세대(안쪽 18×12, 4베이 넓은 84㎡형)와
 *       서향·동향 두 세대(15×12, 3베이 84㎡형). 모두 거실 5칸 이상, 안방 + 욕실 둘, 작은방 둘</li>
 *   <li>코어: 북쪽 가운데 꺾인 계단(옥상까지)과 엘리베이터 두 대, 그 앞 승강장 홀이 T자로 남쪽 두 세대 현관까지
 *       이어지고 양 끝은 창. 홀 옆 설비 칸(PS)</li>
 *   <li>1층은 필로티: 기둥만 남기고 트였고, 가운데에 유리로 두른 공동현관 로비(우편함·소파·동 표지판)</li>
 *   <li>남쪽은 바닥까지 내려오는 창과 유리 난간 발코니, 서·동향 세대 앞에도 발코니, 옆·뒤는 띄엄띄엄 창.
 *       층마다 바닥판 띠, 세로 포인트 띠, 아래 몇 층은 돌로 두를 수 있음 (강남)</li>
 *   <li>옥상: 난간, 계단·엘리베이터 기계실, 세로 살로 두른 지붕 장식(크라운)과 조명, 양옆 판에 큰 동 번호</li>
 * </ul>
 * 층고 4 (1층 로비 5). 걸어서 모든 층과 옥상까지 계단으로 오릅니다.
 * 정면은 남쪽(j = d-1). 상자는 {@link #BOX_W}×{@link #BOX_D} 이상 (몸체는 가운데, 앞마당 4칸).
 */
final class TowerApartment {
    /** 몸체 크기 (벽 포함) */
    static final int MASS_W = 39, MASS_D = 35;
    /** 상자 최소 크기: 둘레 2칸, 앞 발코니 1칸 + 앞마당 4칸 */
    static final int BOX_W = MASS_W + 4, BOX_D = 2 + MASS_D + 1 + 4;

    // 몸체 좌표 (mi, mj) 의 벽 줄
    /** 서향 세대 동쪽 벽, 동향 세대 서쪽 벽 (그 사이가 코어 기둥줄) */
    private static final int NW_E = 13, NE_W = 25;
    /** 서·동향 세대 남쪽 벽, 남향 세대 북쪽 벽, 남향 두 세대 사이 벽 */
    private static final int NS = 17, JS = 21, MID = 19;
    private static final int JF = MASS_D - 1, EAST = MASS_W - 1;
    /** 승강장 홀: 코어 기둥줄 j 8..17, 가로 홀 j 18..20 */
    private static final int HALL0 = 8, BAR0 = 18, BAR1 = 20;

    private static final Block BUTTON = Block.of("polished_blackstone_button[face=wall,facing=south,powered=false]", 0x353038);

    private final Voxels v;
    private final AptUnit.Skin s;
    private final Random r;
    private final int ox, oz, floors, roof;
    private final int[] levels;
    /** 아래 몇 층을 기단 돌로 두르는지 (강남은 3층까지 어두운 돌) */
    private final int baseFloors;
    /** 지은 세대 평면 (검사용) */
    final List<AptUnit.Plan> plans = new ArrayList<>();

    private TowerApartment(int w, int d, int floors, AptUnit.Skin s, int baseFloors, Random r) {
        this.s = s;
        this.r = r;
        this.floors = Math.max(6, Math.min(70, floors));
        this.levels = Floors.levels(Floors.GROUND, Floors.HOME, this.floors);
        this.roof = levels[this.floors];
        this.baseFloors = baseFloors;
        this.v = new Voxels(w, d, -1, roof + 16);
        this.ox = (w - MASS_W) / 2;
        this.oz = 2;
    }

    /**
     * @param floors     층 수 (1층 필로티 로비 포함, 6..70)
     * @param baseFloors 기단 돌로 두르는 아래 층 수 (0이면 1층 기둥만)
     * @param dong       동 번호 (예: 101)
     */
    static Voxels build(int w, int d, int floors, AptUnit.Skin s, int baseFloors, int dong, String complex, Random r) {
        return make(w, d, floors, s, baseFloors, dong, complex, r).v;
    }

    /** {@link #build} 와 같고 세대 평면도 돌려줌 (검사용) */
    static TowerApartment make(int w, int d, int floors, AptUnit.Skin s, int baseFloors, int dong, String complex, Random r) {
        TowerApartment t = new TowerApartment(Math.max(w, BOX_W), Math.max(d, BOX_D), floors, s, baseFloors, r);
        t.slabs();
        t.core();
        t.lobby(dong, complex);
        for (int k = 1; k < t.floors; k++) {
            t.unitFloor(k);
        }
        t.facade();
        t.crown(String.valueOf(dong));
        t.yard();
        t.v.connect();
        return t;
    }

    Voxels voxels() {
        return v;
    }

    int[] levels() {
        return levels;
    }

    // ------------------------------------------------------------------ 몸체

    private boolean mass(int mi, int mj) {
        if (mi < 0 || mj < 0 || mi > EAST || mj > JF) {
            return false;
        }
        // 북쪽 끝 줄은 코어(계단·엘리베이터)만 튀어나옴
        return mj > 0 || (mi >= NW_E && mi <= NE_W);
    }

    private boolean perimeter(int mi, int mj) {
        return mass(mi, mj) && (!mass(mi - 1, mj) || !mass(mi + 1, mj) || !mass(mi, mj - 1) || !mass(mi, mj + 1));
    }

    /** 코어 블록(계단·엘리베이터·설비, 북쪽 j 0..7) */
    private static boolean coreBlock(int mi, int mj) {
        return mi >= NW_E && mi <= NE_W && mj <= HALL0 - 1;
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

    private void slabs() {
        for (int k = 0; k <= floors; k++) {
            int y = levels[k] - 1;
            for (int mj = 0; mj <= JF; mj++) {
                for (int mi = 0; mi <= EAST; mi++) {
                    if (!mass(mi, mj)) {
                        continue;
                    }
                    Block b = k == 0 ? POLISHED_ANDESITE : k == floors ? s.roof() : perimeter(mi, mj) ? s.band() : SMOOTH_STONE;
                    set(mi, y, mj, b);
                }
            }
        }
    }

    /** 계단(옥상까지), 엘리베이터 두 대, 홀 옆 설비 칸 */
    private void core() {
        Block cw = Interior.CORE_WALL;
        Interior.stairCore(Frame.facing(v, ox + 18, oz + 6, "north"), levels, cw, "polished_andesite", 0x848685);
        int[] lift = Arrays.copyOf(levels, floors);
        Interior.elevator(new Frame(v, ox + 20, oz + 5, 0), lift, cw);
        Interior.elevator(new Frame(v, ox + 23, oz + 5, 0), lift, cw);
        fill(20, 0, 0, 24, roof + 3, 3, cw);
        // 설비 칸 (PS·쓰레기 배관) 둘: 홀 기둥줄 양옆
        fill(14, 0, 12, 16, roof - 1, NS, cw);
        fill(22, 0, 12, 24, roof - 1, NS, cw);
        // 옥상 기계실 (엘리베이터 위) 과 계단 옥탑 지붕
        fill(19, roof, 4, NE_W, roof + 3, 7, cw);
        fill(NW_E, roof + 3, 0, NE_W, roof + 3, 7, s.roof());
    }

    // ------------------------------------------------------------------ 1층 필로티와 로비

    private void lobby(int dong, String complex) {
        int top = levels[1] - 2;
        // 필로티 기둥 (벽이 만나는 줄)
        int[] cols = {0, 6, NW_E, MID, NE_W, 32, EAST};
        int[] rows = {1, 9, NS, JS, 28, JF};
        for (int mi : cols) {
            for (int mj : rows) {
                if (mass(mi, mj) && !(mi >= NW_E && mi <= NE_W && mj <= JS)) {
                    fill(mi, 0, mj, mi, top, mj, s.base());
                }
            }
        }
        // 로비: 코어 앞 홀 + 가로 홀 가운데 (mi 14..24, mj 8..20), 유리 벽
        Block glass = s.glass();
        fill(NW_E, 0, HALL0, NW_E, top, JS, glass);
        fill(NE_W, 0, HALL0, NE_W, top, JS, glass);
        fill(NW_E, 0, JS, NE_W, top, JS, glass);
        fill(NW_E, 0, HALL0, NW_E, 0, JS, s.base());
        fill(NE_W, 0, HALL0, NE_W, 0, JS, s.base());
        fill(NW_E, 0, JS, NE_W, 0, JS, s.base());
        fill(14, -1, HALL0, 24, -1, BAR1, POLISHED_DIORITE);
        // 정문 (양여닫이 유리문, 가운데)
        set(18, 0, JS, AIR);
        set(19, 0, JS, AIR);
        set(18, 1, JS, AIR);
        set(19, 1, JS, AIR);
        Kit.doubleDoor(Frame.of(v), ox + 18, 0, oz + JS, "birch", "south");
        // 우편함 벽, 소파, 화분, 등, 동 표지판
        for (int mj = HALL0 + 1; mj <= HALL0 + 3; mj++) {
            set(14, 0, mj, IRON_BLOCK);
            set(14, 1, mj, Block.of("iron_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        }
        for (int mi = 22; mi <= 24; mi++) {
            set(mi, 0, BAR1, Block.of("dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", 0x432B14));
        }
        set(14, 0, BAR1, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
        set(24, 0, BAR0, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
        for (int mj = 9; mj <= BAR1; mj += 4) {
            set(19, top, mj, Interior.LIGHT);
        }
        set(16, 2, JS + 1, Blocks.wallSign("dark_oak", "south", "white", false, signLines(complex, dong)));
        // 필로티 천장 등과 바닥 줄눈
        for (int mj = 4; mj <= JF - 2; mj += 6) {
            for (int mi = 3; mi <= EAST - 2; mi += 6) {
                if (mass(mi, mj) && get(mi, top, mj) == null) {
                    set(mi, top, mj, LANTERN_HANGING);
                }
            }
        }
        for (int mj = 0; mj <= JF; mj++) {
            for (int mi = 0; mi <= EAST; mi++) {
                if (mass(mi, mj) && get(mi, -1, mj) == POLISHED_ANDESITE && (mi % 6 == 0 || mj % 6 == 0)) {
                    set(mi, -1, mj, SMOOTH_STONE);
                }
            }
        }
    }

    /** 표지판 줄: 단지 이름이 길면 두 줄로 */
    static String[] signLines(String complex, int dong) {
        String name = complex == null ? "" : complex;
        if (name.length() <= 9) {
            return new String[]{"", name, dong + "동", ""};
        }
        return new String[]{name.substring(0, 9), name.substring(9, Math.min(name.length(), 18)), dong + "동", ""};
    }

    // ------------------------------------------------------------------ 세대 층

    private void unitFloor(int k) {
        int level = levels[k], top = level + 2;
        Block wall = s.wall(), inner = AptUnit.WALL;
        // 바깥 벽 (코어 블록은 계단·엘리베이터가 그림)
        for (int mj = 0; mj <= JF; mj++) {
            for (int mi = 0; mi <= EAST; mi++) {
                if (perimeter(mi, mj) && !coreBlock(mi, mj)) {
                    fill(mi, level, mj, mi, top, mj, wall);
                }
            }
        }
        // 세대 사이 벽과 홀 벽
        fill(NW_E, level, 1, NW_E, top, NS, inner);
        fill(NE_W, level, 1, NE_W, top, NS, inner);
        fill(0, level, NS, NW_E, top, NS, inner);
        fill(NE_W, level, NS, EAST, top, NS, inner);
        fill(0, level, JS, EAST, top, JS, inner);
        fill(MID, level, JS, MID, top, JF, inner);
        // 승강장 홀: 코어 앞 (mi 14..24, mj 8..11) + 가운데 통로 (17..21, 12..17) + 가로 홀 (1..37, 18..20)
        fill(14, level, HALL0, 24, top, 11, AIR);
        fill(17, level, 12, 21, top, NS, AIR);
        fill(1, level, BAR0, EAST - 1, top, BAR1, AIR);
        Frame f = Frame.of(v);
        Interior.floor(f, ox + 14, oz + HALL0, ox + 24, oz + 11, level, POLISHED_DIORITE);
        Interior.floor(f, ox + 17, oz + 12, ox + 21, oz + NS, level, POLISHED_DIORITE);
        Interior.floor(f, ox + 1, oz + BAR0, ox + EAST - 1, oz + BAR1, level, POLISHED_DIORITE);
        set(22, level + 1, HALL0, BUTTON);
        for (int mi = 4; mi < EAST; mi += 7) {
            set(mi, top, 19, Interior.LIGHT);
        }
        set(19, top, 10, Interior.LIGHT);
        set(19, top, 15, Interior.LIGHT);
        // 세대 넷 (현관은 거실 칸 가장자리)
        plans.add(AptUnit.home(new Frame(v, ox + 1, oz + JS + 1, 0), r, 18, 12, level, 11));
        plans.add(AptUnit.home(new Frame(v, ox + MID + 1, oz + JS + 1, 0), r, 18, 12, level, 6));
        plans.add(AptUnit.home(Frame.facing(v, ox + NW_E - 1, oz + 2, "west"), r, 15, 12, level, 6));
        plans.add(AptUnit.home(Frame.facing(v, ox + NE_W + 1, oz + 16, "east"), r, 15, 12, level, 6));
        // 현관 옆 호수 표지판 (홀 쪽)
        int no = k + 1;
        set(13, level + 1, BAR1, Blocks.wallSign("birch", "north", "black", false, "", no + "01호"));
        set(25, level + 1, BAR1, Blocks.wallSign("birch", "north", "black", false, "", no + "02호"));
        set(14, level + 1, 9, Blocks.wallSign("birch", "east", "black", false, "", no + "03호"));
        set(24, level + 1, 11, Blocks.wallSign("birch", "west", "black", false, "", no + "04호"));
    }

    // ------------------------------------------------------------------ 바깥

    /** 안쪽 이웃이 칸막이 벽이면 창을 내지 않음 */
    private boolean partitionBehind(int mi, int y, int mj, int di, int dj) {
        Block b = get(mi + di, y, mj + dj);
        return b != null && (b.equals(AptUnit.WALL) || b.equals(Interior.CORE_WALL) || b.equals(Rooms.WARDROBE));
    }

    private void facade() {
        Block wall = s.wall();
        for (int k = 1; k < floors; k++) {
            int level = levels[k];
            boolean base = k < baseFloors;
            for (int mj = 0; mj <= JF; mj++) {
                for (int mi = 0; mi <= EAST; mi++) {
                    if (!perimeter(mi, mj) || coreBlock(mi, mj)) {
                        continue;
                    }
                    int di = !mass(mi - 1, mj) ? 1 : !mass(mi + 1, mj) ? -1 : 0;
                    int dj = !mass(mi, mj + 1) ? -1 : !mass(mi, mj - 1) ? 1 : 0;
                    boolean corner = di != 0 && dj != 0;
                    boolean junction = mi == NW_E || mi == NE_W || mi == MID || mj == NS || mj == JS;
                    if (corner || junction || partitionBehind(mi, level + 1, mj, di, dj)) {
                        if (base) {
                            fill(mi, level, mj, mi, level + 2, mj, s.base());
                        }
                        continue;
                    }
                    boolean hallEnd = di != 0 && mj >= BAR0 && mj <= BAR1;
                    boolean living = (dj == -1 && mj == JF) || (di != 0 && mj > 1 && mj < NS);
                    if (living) {
                        set(mi, level, mj, base ? s.base() : s.frame());
                        set(mi, level + 1, mj, s.glass());
                        set(mi, level + 2, mj, s.glass());
                    } else {
                        int along = di != 0 ? mj : mi;
                        boolean win = hallEnd || Math.floorMod(along, 4) == 1 || Math.floorMod(along, 4) == 2;
                        Block w = base ? s.base() : wall;
                        set(mi, level, mj, w);
                        set(mi, level + 1, mj, win ? s.glass() : w);
                        set(mi, level + 2, mj, win && !hallEnd ? s.glass() : w);
                    }
                }
            }
            // 남쪽 발코니: 바닥판, 유리 난간, 세대 사이·양 끝 핀
            int bj = JF + 1;
            for (int mi = 0; mi <= EAST; mi++) {
                set(mi, level - 1, bj, s.band());
                boolean fin = mi == 0 || mi == MID || mi == EAST;
                if (fin) {
                    fill(mi, level, bj, mi, level + 2, bj, s.accent());
                } else {
                    set(mi, level, bj, s.rail());
                }
            }
            // 서·동향 세대 발코니 (바깥 옆벽 앞 한 칸)
            for (int mj = 2; mj <= NS - 1; mj++) {
                for (int side = 0; side < 2; side++) {
                    int mi = side == 0 ? -1 : EAST + 1;
                    boolean fin = mj == 2 || mj == NS - 1;
                    set(mi, level - 1, mj, s.band());
                    set(mi, level, mj, fin ? s.accent() : s.rail());
                    if (fin) {
                        fill(mi, level + 1, mj, mi, level + 2, mj, s.accent());
                    }
                }
            }
        }
        for (int mi = 0; mi <= EAST; mi++) {
            set(mi, roof - 1, JF + 1, s.band());
        }
        for (int mj = 2; mj <= NS - 1; mj++) {
            set(-1, roof - 1, mj, s.band());
            set(EAST + 1, roof - 1, mj, s.band());
        }
        // 세로 포인트 띠: 가로 홀 양 끝 (홀 창 줄) 위아래
        for (int y = levels[1]; y < roof; y++) {
            set(0, y, BAR0 - 1, s.accent());
            set(0, y, BAR1 + 1, s.accent());
            set(EAST, y, BAR0 - 1, s.accent());
            set(EAST, y, BAR1 + 1, s.accent());
        }
    }

    /** 옥상: 난간, 세로 살 크라운, 조명, 양옆 판에 동 번호 */
    private void crown(String dong) {
        int y0 = roof;
        for (int mj = 0; mj <= JF; mj++) {
            for (int mi = 0; mi <= EAST; mi++) {
                if (!perimeter(mi, mj) || coreBlock(mi, mj)) {
                    continue;
                }
                set(mi, y0, mj, s.wall());
                boolean side = (mi == 0 || mi == EAST) && mj >= 2 && mj <= JF - 1;
                if (side) {
                    continue;
                }
                if ((mi + mj) % 2 == 0) {
                    fill(mi, y0 + 1, mj, mi, y0 + 6, mj, s.frame());
                }
                set(mi, y0 + 7, mj, s.accent());
                if ((mi + mj) % 6 == 0) {
                    set(mi, y0 + 8, mj, SEA_LANTERN);
                }
            }
        }
        // 양옆 판 (동 번호) — 크라운보다 조금 높게
        int panelTop = y0 + 12;
        for (int side = 0; side < 2; side++) {
            int mi = side == 0 ? 0 : EAST;
            fill(mi, y0 + 1, 2, mi, panelTop, JF - 1, s.wall());
            fill(mi, panelTop + 1, 2, mi, panelTop + 1, JF - 1, s.accent());
            fill(mi, y0 + 1, 2, mi, y0 + 1, JF - 1, s.accent());
            int in = side == 0 ? 1 : EAST - 1;
            for (int mj = 3; mj <= JF - 2; mj += 4) {
                fill(in, y0, mj, in, panelTop, mj, s.wall());
            }
            int width = AptUnit.numberWidth(dong);
            int mid = (2 + JF - 1) / 2;
            if (side == 0) {
                AptUnit.number(v, dong, ox + mi, panelTop - 1, oz + mid - width / 2, 0, 1, s.number());
            } else {
                AptUnit.number(v, dong, ox + mi, panelTop - 1, oz + mid + width / 2, 0, -1, s.number());
            }
        }
    }

    /** 앞마당: 로비로 가는 보도, 화단 회양목, 둘레 잔디 */
    private void yard() {
        int j0 = oz + JF + 2;
        v.fill(0, -1, j0, v.w - 1, -1, v.d - 1, LIGHT_GRAY_CONCRETE);
        int c = ox + 18;
        v.fill(c - 2, -1, j0, c + 3, -1, v.d - 1, POLISHED_ANDESITE);
        for (int i = 1; i < v.w - 1; i++) {
            if (i < c - 3 || i > c + 4) {
                v.set(i, -1, j0 + 1, GRASS);
                v.set(i, 0, j0 + 1, Kit.BOX_HEDGE);
            }
        }
        for (int j = 0; j < j0; j++) {
            for (int i = 0; i < v.w; i++) {
                if (v.get(i, -1, j) == null) {
                    v.set(i, -1, j, GRASS);
                }
            }
        }
    }
}
