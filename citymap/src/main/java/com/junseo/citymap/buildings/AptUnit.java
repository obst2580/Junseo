package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 아파트 한 세대 실내 (한국 아파트 평면, 사람이 실제로 살 수 있는 크기).
 * <ul>
 *   <li>3베이: 앞(창 쪽)은 [방 | 거실 | 방], 뒤는 [욕실 | 현관·주방·식당 | 방]. 넓은 옆 칸이 안방(더블 침대·붙박이장),
 *       그 뒤가 욕실 (5칸이면 공용 욕실 + 안방 욕실 둘). 좁은 옆 칸은 앞뒤로 작은방 둘.
 *       84㎡형(새 아파트, 15×12)이면 거실 5×7, 주방·식당 5×5, 안방 5×7, 침실 3×7·3×4, 욕실 2×4 둘.
 *       59㎡형(옛 복도식, 13×10)이면 거실 4×5, 침실 셋, 욕실 하나</li>
 *   <li>현관 2×2 타일과 신발장, 거실과 트인 주방(ㄱ자 조리대·개수대·가스레인지·냉장고·위 수납장)과 식탁</li>
 *   <li>모든 방은 문으로 드나들고, 가구 사이로 한 칸 이상 걸어 다닐 길이 남습니다</li>
 * </ul>
 * 모두 {@link Frame} 기준 좌표: 안쪽 a 0..w-1, b 0..d-1. b = -1 이 현관 벽(계단홀·복도 쪽, 현관문은 a = entryA 에 냄),
 * b = d 가 창(발코니) 쪽입니다. 바깥 벽·창·발코니는 건물이 그립니다. 층고 4 (바닥 1 + 빈 칸 3, {@link Floors#HOME}).
 * <p>
 * 동 번호를 옆벽에 크게 쓰는 숫자({@link #number})도 여기 있습니다.
 */
final class AptUnit {
    /**
     * 아파트 겉모습: 벽, 층 띠(바닥판 끝), 포인트 색(세로 띠·옥상 장식), 창 유리, 발코니 난간, 1층·기단 돌,
     * 창틀, 동 번호 글씨색, 옥상 바닥.
     */
    record Skin(Block wall, Block band, Block accent, Block glass, Block rail, Block base, Block frame, Block number, Block roof) {
    }

    /** 방 하나의 안쪽 (벽 안, 양 끝 포함) */
    record Room(String name, int a0, int b0, int a1, int b1) {
        int w() {
            return a1 - a0 + 1;
        }

        int d() {
            return b1 - b0 + 1;
        }

        /** 짧은 변 ≥ s, 긴 변 ≥ l 인지 (방향 상관없음) */
        boolean atLeast(int s, int l) {
            return Math.min(w(), d()) >= s && Math.max(w(), d()) >= l;
        }
    }

    /** 세대 평면 (검사용): 세대 안쪽 크기와 방들 */
    record Plan(int w, int d, List<Room> rooms) {
        List<Room> named(String prefix) {
            return rooms.stream().filter(x -> x.name().startsWith(prefix)).toList();
        }
    }

    /** 84㎡형 세대 안쪽 (새 타워형·판상형): 너비 × 깊이 */
    static final int UNIT84_W = 15, UNIT84_D = 12;
    /** 59㎡형 세대 안쪽 (옛 복도식) */
    static final int UNIT59_W = 13, UNIT59_D = 10;

    static final Block WALL = Interior.INNER_WALL;
    static final Block SHOE = Block.of("stripped_birch_wood[axis=y]", 0xC4B07B);
    static final Block TUB = Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF);
    static final Block TOILET_N = Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE);
    static final Block RUG = Block.of("light_gray_carpet", 0x8E8E86);
    static final Block CABINET = Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14);
    static final Block STOVE = Block.of("smoker[facing=south,lit=false]", 0x555451);
    static final Block SHOWER = Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050);
    /** 현관문 (철제 방화문처럼 어두운 문. 철문은 손으로 못 열어서 나무문) */
    static final String ENTRY_WOOD = "dark_oak";
    static final String ROOM_WOOD = "pale_oak";

    /**
     * 한 세대를 꾸밉니다.
     *
     * @param entryA 현관문 자리 (b = -1 벽, 0..w-1). 거실 칸 안에 오게 칸을 나눕니다
     * @return 방 배치 (검사용)
     */
    static Plan home(Frame f, Random r, int w, int d, int level, int entryA) {
        entryA = Math.max(0, Math.min(w - 1, entryA));
        int top = level + 2;
        List<Room> rooms = new ArrayList<>();
        Interior.floor(f, 0, 0, w - 1, d - 1, level, r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        Interior.door(f, entryA, level, -1, ENTRY_WOOD, "south");
        if (w < 9 || d < 8) {
            studio(f, r, w, d, level, entryA);
            rooms.add(new Room("원룸", 0, 0, w - 1, d - 1));
            return new Plan(w, d, rooms);
        }
        // 뒤쪽(현관·주방·욕실·작은방) 깊이 bz, 사이 벽 b = bz (옆 칸에만), 앞쪽(거실·방) b = fz..d-1
        int bz = d >= 10 ? 4 : 3, fz = bz + 1;
        // 칸 나누기: 왼쪽 L, 가운데 M (현관이 들어감), 오른쪽 R (옆 칸은 0 또는 3..5, 사이 벽 1칸)
        int minMid = w >= 14 ? 5 : 4;
        int bestL = -1, bestR = -1, best = Integer.MIN_VALUE;
        for (int left : new int[]{0, 3, 4, 5}) {
            for (int right : new int[]{0, 3, 4, 5}) {
                int mid = w - left - right - (left > 0 ? 1 : 0) - (right > 0 ? 1 : 0);
                int m0 = left > 0 ? left + 1 : 0, m1 = m0 + mid - 1;
                if (mid < minMid || mid > 8 || entryA < m0 || entryA > m1) {
                    continue;
                }
                int score = (left > 0 ? 40 : 0) + (right > 0 ? 40 : 0) + (Math.max(left, right) >= 4 ? 30 : 0)
                        - Math.abs(mid - 5) * 4 + Math.min(left, right) + (left >= right ? 1 : 0);
                if (score > best) {
                    best = score;
                    bestL = left;
                    bestR = right;
                }
            }
        }
        if (bestL < 0) {
            openPlan(f, r, w, d, level, entryA, bz, rooms);
            return new Plan(w, d, rooms);
        }
        int left = bestL, right = bestR;
        int m0 = left > 0 ? left + 1 : 0, m1 = right > 0 ? w - right - 2 : w - 1;
        if (left > 0) {
            f.fill(left, level, 0, left, top, d - 1, WALL);
            f.fill(0, level, bz, left - 1, top, bz, WALL);
        }
        if (right > 0) {
            f.fill(m1 + 1, level, 0, m1 + 1, top, d - 1, WALL);
            f.fill(m1 + 2, level, bz, w - 1, top, bz, WALL);
        }
        // 넓은 옆 칸 = 안방 + 욕실, 다른 옆 칸 = 작은방 둘 (앞·뒤)
        boolean masterLeft = left >= right;
        int[][] bays = {{0, left - 1, left}, {m1 + 2, w - 1, m1 + 1}}; // {a0, a1, 문이 나는 벽 a}
        for (int side = 0; side < 2; side++) {
            int a0 = bays[side][0], a1 = bays[side][1], wallA = bays[side][2];
            if (a1 < a0) {
                continue;
            }
            String facing = side == 0 ? "east" : "west";
            boolean master = (side == 0) == masterLeft || (side == 0 ? right == 0 : left == 0);
            if (master) {
                bedroom(f, r, a0, fz, a1, d - 1, level, true, wallA, fz, facing);
                rooms.add(new Room("안방", a0, fz, a1, d - 1));
                baths(f, a0, a1, bz, level, wallA, facing, rooms);
            } else {
                bedroom(f, r, a0, fz, a1, d - 1, level, false, wallA, fz, facing);
                rooms.add(new Room("침실", a0, fz, a1, d - 1));
                backRoom(f, r, a0, 0, a1, bz - 1, level, wallA, facing);
                rooms.add(new Room("침실", a0, 0, a1, bz - 1));
            }
        }
        kitchen(f, r, m0, m1, bz, level, entryA, left > 0, right > 0, rooms);
        living(f, r, m0, m1, fz, d - 1, level);
        rooms.add(new Room("거실", m0, fz, m1, d - 1));
        rooms.add(new Room("주방", m0, 0, m1, bz));
        return new Plan(w, d, rooms);
    }

    /** 칸을 못 나누는 집: 한 칸에 거실·주방, 뒤 구석 욕실, 창가 방 */
    private static void openPlan(Frame f, Random r, int w, int d, int level, int entryA, int bz, List<Room> rooms) {
        int top = level + 2;
        boolean bathLeft = entryA >= w / 2;
        int ba0 = bathLeft ? 0 : w - 3, ba1 = ba0 + 2;
        int wallA = bathLeft ? 3 : w - 4;
        f.fill(wallA, level, 0, wallA, top, bz - 1, WALL);
        f.fill(ba0, level, bz, ba1, top, bz, WALL);
        Interior.door(f, ba0 + 1, level, bz, ROOM_WOOD, "south");
        bathFixtures(f, ba0, 0, ba1, bz - 1, level, ba0 + 1);
        rooms.add(new Room("욕실", ba0, 0, ba1, bz - 1));
        int m0 = bathLeft ? 4 : 0, m1 = bathLeft ? w - 1 : w - 5;
        kitchen(f, r, m0, m1, bz, level, entryA, false, false, rooms);
        Furniture.bed(f, r, bathLeft ? w - 1 : 0, level, d - 2, "south");
        rooms.add(new Room("거실", 0, bz + 1, w - 1, d - 1));
        rooms.add(new Room("주방", m0, 0, m1, bz));
        f.set(w / 2, top, (bz + d) / 2, Interior.LIGHT);
    }

    /** 원룸: 현관 옆 욕실(뒤 구석), 부엌, 창가 침대, 책상 */
    private static void studio(Frame f, Random r, int w, int d, int level, int entryA) {
        int top = level + 2;
        boolean bathLeft = entryA >= w / 2;
        int ba0 = bathLeft ? 0 : w - 3, ba1 = ba0 + 2;
        if (w >= 6 && d >= 5) {
            int wallA = bathLeft ? 3 : w - 4;
            f.fill(wallA, level, 0, wallA, top, 2, WALL);
            f.fill(ba0, level, 3, ba1, top, 3, WALL);
            Interior.door(f, ba0 + 1, level, 3, ROOM_WOOD, "south");
            bathFixtures(f, ba0, 0, ba1, 2, level, ba0 + 1);
        }
        Interior.floor(f, entryA, 0, entryA, 0, level, Interior.TILE);
        int k0 = bathLeft ? 4 : 0, k1 = bathLeft ? w - 1 : w - 5;
        for (int a = k0; a <= k1; a++) {
            if (Math.abs(a - entryA) <= 1) {
                continue;
            }
            f.set(a, level, 0, a == k1 ? Furniture.FRIDGE : (a == k0 ? CAULDRON : Furniture.COUNTER));
            if (a == k1) {
                f.set(a, level + 1, 0, Furniture.FRIDGE);
            }
        }
        Furniture.bed(f, r, bathLeft ? w - 1 : 0, level, d - 2, "south");
        if (d >= 5) {
            f.set(bathLeft ? 0 : w - 1, level, d - 1, Furniture.DESK_TOP);
        }
        f.set(w / 2, top, d / 2, Interior.LIGHT);
    }

    /**
     * 방 (앞쪽): 문은 옆 칸 사이 벽(doorA)의 doorB 줄. 붙박이장은 문 줄을 따라, 침대 머리는 먼 옆벽.
     * 안방이면 침대 둘을 붙인 더블, 작은방이면 창가 책상.
     */
    private static void bedroom(Frame f, Random r, int a0, int b0, int a1, int b1, int level, boolean master,
                                int doorA, int doorB, String doorFacing) {
        int top = level + 2;
        Interior.door(f, doorA, level, doorB, ROOM_WOOD, doorFacing);
        boolean doorEast = doorA > a1;
        int far = doorEast ? a0 : a1, near = doorEast ? a1 : a0, step = doorEast ? 1 : -1;
        for (int a = far; a != near; a += step) {
            f.set(a, level, b0, Rooms.WARDROBE);
            f.set(a, level + 1, b0, Rooms.WARDROBE);
        }
        int depth = b1 - b0 + 1;
        int bb = b0 + Math.max(2, depth / 2);
        if (bb > b1) {
            bb = b1;
        }
        String toward = doorEast ? "east" : "west";
        Furniture.bed(f, r, far, level, bb, toward);
        if (master && depth >= 4) {
            Furniture.bed(f, r, far, level, bb + 1 <= b1 ? bb + 1 : bb - 1, toward);
        }
        if (!master && depth >= 5 && a1 - a0 >= 2) {
            Furniture.desk(f, near, level, b1, doorEast ? "west" : "east");
        }
        if (master && a1 - a0 >= 3) {
            f.set(near, level, b1, Furniture.DESK_TOP); // 화장대
            f.set(near, level + 1, b1, Block.of("light_blue_stained_glass_pane", 0x6699D8));
        }
        f.set((a0 + a1) / 2, top, (b0 + b1 + 1) / 2, Interior.LIGHT);
    }

    /** 뒤쪽 작은방: 문은 옆 칸 사이 벽의 맨 앞 줄(b1). 먼 쪽에 침대, 가운데 책장, 문 쪽 책상 */
    private static void backRoom(Frame f, Random r, int a0, int b0, int a1, int b1, int level, int doorA, String doorFacing) {
        Interior.door(f, doorA, level, b1, ROOM_WOOD, doorFacing);
        boolean doorEast = doorA > a1;
        int far = doorEast ? a0 : a1, near = doorEast ? a1 : a0, step = doorEast ? 1 : -1;
        Furniture.bed(f, r, far, level, b0, "south");
        if (a1 - a0 >= 2) {
            f.set(far + step, level, b0, Furniture.BOOKSHELF);
            f.set(far + step, level + 1, b0, Furniture.BOOKSHELF);
            f.set(near, level, b0, Furniture.DESK_TOP);
            if (b1 - b0 >= 2) {
                Furniture.chair(f, near, level, b0 + 1, "north", "birch");
            }
        }
        f.set((a0 + a1) / 2, level + 2, (b0 + b1 + 1) / 2, Interior.LIGHT);
    }

    /**
     * 안방 칸 뒤쪽 욕실: 5칸이면 [안방 욕실 2 | 벽 | 공용 욕실 2] (공용은 거실 쪽 벽에 문, 안방 욕실은 안방 쪽 벽에 문),
     * 좁으면 공용 욕실 하나.
     */
    private static void baths(Frame f, int a0, int a1, int bz, int level, int wallA, String facing, List<Room> rooms) {
        int top = level + 2;
        boolean midEast = wallA > a1;
        if (a1 - a0 + 1 >= 5) {
            int pa0 = midEast ? a1 - 1 : a0, pa1 = pa0 + 1;           // 공용 욕실 (거실 쪽)
            int ma0 = midEast ? a0 : a1 - (a1 - a0 + 1 - 3) + 1;      // 안방 욕실 (바깥쪽)
            int ma1 = midEast ? a1 - 3 : a1;
            int split = midEast ? pa0 - 1 : pa1 + 1;
            f.fill(split, level, 0, split, top, bz - 1, WALL);
            Interior.door(f, wallA, level, bz - 1, ROOM_WOOD, facing);
            bathFixtures(f, pa0, 0, pa1, bz - 1, level, midEast ? pa1 : pa0);
            rooms.add(new Room("욕실", pa0, 0, pa1, bz - 1));
            int door = midEast ? ma1 : ma0;
            Interior.door(f, door, level, bz, ROOM_WOOD, "south");
            bathFixtures(f, ma0, 0, ma1, bz - 1, level, door);
            rooms.add(new Room("욕실(안방)", ma0, 0, ma1, bz - 1));
        } else {
            Interior.door(f, wallA, level, bz - 1, ROOM_WOOD, facing);
            bathFixtures(f, a0, 0, a1, bz - 1, level, midEast ? a1 : a0);
            rooms.add(new Room("욕실", a0, 0, a1, bz - 1));
        }
    }

    /**
     * 욕실 설비: 타일 바닥, 문 쪽 줄(nearA)은 비우고 맨 안쪽에 변기, 다른 줄에 세면대와 욕조(샤워기).
     */
    private static void bathFixtures(Frame f, int a0, int b0, int a1, int b1, int level, int nearA) {
        Interior.floor(f, a0, b0, a1, b1, level, Interior.TILE);
        f.set(nearA, level, b0, TOILET_N);
        int far = nearA == a0 ? a1 : a0;
        if (far != nearA) {
            f.set(far, level, b0, CAULDRON);
            for (int b = b0 + 1; b <= b1; b++) {
                f.set(far, level, b, TUB);
            }
            f.set(far, level + 2, b1, SHOWER);
        }
        f.set((a0 + a1) / 2, level + 2, (b0 + b1 + 1) / 2, Interior.LIGHT);
    }

    /** 가운데 칸 뒤쪽: 현관 2×2 타일과 신발장, ㄱ자 주방, 식탁. 옆 방 문 앞(b = bz-1)과 현관에서 거실로 가는 줄은 비움 */
    private static void kitchen(Frame f, Random r, int m0, int m1, int bz, int level, int entryA,
                                boolean doorWest, boolean doorEast, List<Room> rooms) {
        int top = level + 2;
        int s = entryA + 1 <= m1 && (entryA - m0 >= m1 - entryA || entryA - 1 < m0) ? 1 : -1;
        if (entryA + s < m0 || entryA + s > m1) {
            s = -s;
        }
        int e2 = entryA + s;
        Interior.floor(f, Math.min(entryA, e2), 0, Math.max(entryA, e2), 1, level, Interior.TILE);
        rooms.add(new Room("현관", Math.min(entryA, e2), 0, Math.max(entryA, e2), 1));
        int shoe = entryA - s;
        boolean shoeOk = shoe >= m0 && shoe <= m1;
        if (shoeOk) {
            f.set(shoe, level, 0, SHOE);
            f.set(shoe, level + 1, 0, SHOE);
        }
        // 막힌 칸: 현관 2×2, 신발장, 현관에서 거실로 가는 줄, 옆 방 문 앞
        java.util.Set<Long> blocked = new java.util.HashSet<>();
        for (int b = 0; b <= bz; b++) {
            blocked.add(key(entryA, b));
            blocked.add(key(e2, b));
        }
        if (shoeOk) {
            blocked.add(key(shoe, 0));
        }
        if (doorWest) {
            blocked.add(key(m0, bz - 1));
        }
        if (doorEast) {
            blocked.add(key(m1, bz - 1));
        }
        // 조리대: 현관에서 먼 옆벽(b = bz-2..1) → 뒷벽(b = 0)을 따라 현관 쪽으로 (현관과 한 칸 띄움)
        int far = Math.abs(entryA - m0) >= Math.abs(m1 - entryA) ? m0 : m1, dir = far == m0 ? 1 : -1;
        List<int[]> run = new ArrayList<>();
        for (int b = bz - 2; b >= 1; b--) {
            if (!blocked.contains(key(far, b))) {
                run.add(new int[]{far, b});
            }
        }
        for (int a = far; a >= m0 && a <= m1; a += dir) {
            if (blocked.contains(key(a, 0)) || Math.abs(a - entryA) < 2 || Math.abs(a - e2) < 1) {
                break;
            }
            run.add(new int[]{a, 0});
        }
        if (run.size() >= 3) {
            int sink = run.size() / 2, stove = Math.min(run.size() - 1, sink + 1);
            for (int k = 0; k < run.size(); k++) {
                int[] c = run.get(k);
                Block x = k == 0 ? Furniture.FRIDGE : k == sink ? CAULDRON : k == stove ? STOVE : Furniture.COUNTER;
                f.set(c[0], level, c[1], x);
                blocked.add(key(c[0], c[1]));
                if (k == 0) {
                    f.set(c[0], level + 1, c[1], Furniture.FRIDGE);
                } else if (c[1] == 0) {
                    f.set(c[0], top, c[1], Furniture.COUNTER); // 위 수납장
                }
            }
        }
        // 식탁 (2인 판 + 의자): 막힌 칸과 겹치지 않는 자리
        if (bz >= 3 && m1 - m0 >= 3) {
            for (int ta = m0 + 1; ta + 1 <= m1 - 1; ta++) {
                boolean ok = true;
                for (int a = ta; a <= ta + 1 && ok; a++) {
                    for (int b = bz - 2; b <= bz && ok; b++) {
                        ok = !blocked.contains(key(a, b)) && f.empty(a, level, b);
                    }
                }
                if (ok) {
                    Furniture.table(f, ta, level, bz - 1, 2, 1, "oak");
                    break;
                }
            }
        }
        f.set((m0 + m1) / 2, top, Math.max(1, bz / 2), Interior.LIGHT);
    }

    private static long key(int a, int b) {
        return ((long) a << 32) ^ (b & 0xffffffffL);
    }

    /** 가운데 칸 앞쪽: 거실 (TV·거실장은 서쪽 벽, 소파는 동쪽 벽, 가운데 깔개, 창가 화분). 방문 앞 줄(b0)은 비움 */
    private static void living(Frame f, Random r, int m0, int m1, int b0, int b1, int level) {
        int top = level + 2;
        int depth = b1 - b0 + 1;
        if (m1 - m0 >= 3 && depth >= 4) {
            int tb = Math.max(b0 + 1, Math.min(b1 - 1, b0 + depth / 2));
            f.set(m0, level, tb, CABINET);
            f.set(m0, level + 1, tb, Furniture.TV);
            f.set(m0, level, tb + 1, CABINET);
            f.set(m0, level + 1, tb + 1, Furniture.TV);
            String wood = Furniture.SOFA[r.nextInt(Furniture.SOFA.length)];
            for (int b = tb - 1; b <= Math.min(b1 - 1, tb + 2); b++) {
                if (b > b0) {
                    f.set(m1, level, b, Block.of(wood + "_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", Furniture.rgb(wood)));
                }
            }
            for (int a = m0 + 2; a <= m1 - 2; a++) {
                for (int b = tb; b <= tb + 1; b++) {
                    if (f.empty(a, level, b)) {
                        f.set(a, level, b, RUG);
                    }
                }
            }
        }
        Furniture.plant(f, r, m1, level, b1);
        f.set((m0 + m1) / 2, top, (b0 + b1 + 1) / 2, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 동 번호 숫자

    /** 큰 숫자 6×10 칸 (도장 글씨처럼 굵기 2) */
    private static final String[][] BIG = {
            {".####.", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##", ".####."},
            {"..##", ".###", "####", "..##", "..##", "..##", "..##", "..##", "..##", "..##"},
            {".####.", "##..##", "....##", "....##", "...##.", "..##..", ".##...", "##....", "##....", "######"},
            {".####.", "##..##", "....##", "....##", "..###.", "....##", "....##", "....##", "##..##", ".####."},
            {"....##", "...###", "..####", ".##.##", "##..##", "######", "....##", "....##", "....##", "....##"},
            {"######", "##....", "##....", "#####.", "....##", "....##", "....##", "....##", "##..##", ".####."},
            {".####.", "##..##", "##....", "##....", "#####.", "##..##", "##..##", "##..##", "##..##", ".####."},
            {"######", "....##", "....##", "...##.", "...##.", "..##..", "..##..", "..##..", "..##..", "..##.."},
            {".####.", "##..##", "##..##", "##..##", ".####.", "##..##", "##..##", "##..##", "##..##", ".####."},
            {".####.", "##..##", "##..##", "##..##", "##..##", ".#####", "....##", "....##", "##..##", ".####."},
    };
    /** 작은 숫자 4×7 칸 (좁은 옆벽용) */
    private static final String[][] SMALL = {
            {".##.", "#..#", "#..#", "#..#", "#..#", "#..#", ".##."},
            {".#", "##", ".#", ".#", ".#", ".#", ".#"},
            {".##.", "#..#", "...#", "..#.", ".#..", "#...", "####"},
            {"###.", "...#", "...#", ".##.", "...#", "...#", "###."},
            {"..#.", ".##.", "#.#.", "#.#.", "####", "..#.", "..#."},
            {"####", "#...", "###.", "...#", "...#", "#..#", ".##."},
            {".##.", "#...", "#...", "###.", "#..#", "#..#", ".##."},
            {"####", "...#", "..#.", "..#.", ".#..", ".#..", ".#.."},
            {".##.", "#..#", "#..#", ".##.", "#..#", "#..#", ".##."},
            {".##.", "#..#", "#..#", ".###", "...#", "...#", ".##."},
    };

    private static String[][] font(boolean big) {
        return big ? BIG : SMALL;
    }

    /** 숫자 글씨 높이 */
    static int numberHeight(boolean big) {
        return big ? 10 : 7;
    }

    /** 숫자 글씨 폭 (글자 사이 big 이면 2칸, 작으면 1칸) */
    static int numberWidth(String digits, boolean big) {
        int w = 0;
        for (char ch : digits.toCharArray()) {
            w += font(big)[ch - '0'][0].length() + (big ? 2 : 1);
        }
        return Math.max(0, w - (big ? 2 : 1));
    }

    static int numberWidth(String digits) {
        return numberWidth(digits, true);
    }

    /** 이 폭에 들어가는 가장 큰 글씨 (안 들어가면 null) */
    static Boolean fits(String digits, int width) {
        return numberWidth(digits, true) <= width ? Boolean.TRUE : numberWidth(digits, false) <= width ? Boolean.FALSE : null;
    }

    static void number(Voxels v, String digits, int i, int yTop, int j, int di, int dj, Block fg) {
        number(v, digits, true, i, yTop, j, di, dj, fg);
    }

    /**
     * 옆벽에 동 번호를 크게 칠합니다 (벽 블록을 바꿈). 첫 글자 왼쪽 위가 (i, yTop, j), (di, dj) 방향으로 씁니다
     * (남쪽을 보는 면이면 (1, 0), 북쪽 (-1, 0), 동쪽 (0, -1), 서쪽 (0, 1)).
     */
    static void number(Voxels v, String digits, boolean big, int i, int yTop, int j, int di, int dj, Block fg) {
        int at = 0;
        for (char ch : digits.toCharArray()) {
            if (ch < '0' || ch > '9') {
                continue;
            }
            String[] g = font(big)[ch - '0'];
            for (int row = 0; row < g.length; row++) {
                for (int col = 0; col < g[row].length(); col++) {
                    if (g[row].charAt(col) == '#') {
                        v.set(i + (at + col) * di, yTop - row, j + (at + col) * dj, fg);
                    }
                }
            }
            at += g[0].length() + (big ? 2 : 1);
        }
    }

    private AptUnit() {
    }
}
