package com.junseo.citymap.buildings;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 서울대학교 정문과 캠퍼스 바깥 땅.
 * <ul>
 *   <li>정문: 「국립 서울대학교」의 첫소리 ㄱ·ㅅ·ㄷ 을 엮은 철제 조형물. 앞(큰길)에서 보면 「샤」 모양으로,
 *       왼쪽 큰 ㅅ 두 다리 사이로 캠퍼스 길이 지나가고 오른쪽에 ㅑ 기둥과 두 팔. 은빛 강판 상자 단면,
 *       다리 밑 콘크리트 받침, 기둥에 학교 이름 표지판</li>
 *   <li>캠퍼스 땅: 잔디밭, 화강석 길과 광장(정문 광장, 아크로폴리스), 은행나무 가로수, 느티나무·소나무 숲,
 *       벤치, 보행등, 화단. 건물 자리는 비워 두고(건물이 제 바닥을 깖) 길은 건물 현관과 이어집니다.</li>
 * </ul>
 */
final class SnuCampus {
    /** 땅 지도 칸 종류 */
    static final char OUT = ' ', LAWN = 'L', PATH = 'P', PLAZA = 'Q', BUILDING = 'B', OPEN = 'O', AVENUE = 'A';
    /** 물(연못, 땅 높이), 나무 없는 잔디(운동장·우리), 모래, 흙길 */
    static final char WATER = 'W', FIELD = 'F', SAND = 'S', DIRT = 'D';
    /** 큰길 인도와 이어지는 가장자리 보도 (안쪽 잔디와 사이에 회양목 생울타리) */
    static final char EDGE = 'E';

    static final Block STEEL = IRON_BLOCK;
    static final Block WATER_BLOCK = Blocks.WATER;
    static final Block STEEL_EDGE = Block.of("light_gray_concrete", 0x7D7D73);

    // ------------------------------------------------------------------ 정문

    /** 정문 상자 크기 (정면 너비 × 깊이) */
    static final int GATE_W = 30, GATE_D = 6;
    /** ㅅ 두 다리 사이 (길) 가운데 i */
    static final int GATE_ROAD = 10;

    /** 정문 「샤」: 정면(+j)에서 보면 왼쪽(i 작은 쪽)에 ㅅ, 오른쪽에 ㅑ */
    static Voxels gate() {
        Voxels v = new Voxels(GATE_W, GATE_D, -1, 17);
        int j0 = 1, j1 = 4;
        // ㅅ: 왼쪽 획은 꼭대기(10, 15)에서 왼쪽 아래(2, 0)로, 오른쪽 획은 왼쪽 획 중간(7, 9.5)에서 오른쪽 아래(18, 0)로
        beam(v, 10.5, 15.0, 2.2, 0.0, 1.8, j0, j1);
        beam(v, 7.2, 9.6, 18.2, 0.0, 1.8, j0, j1);
        // ㅑ: 세로 기둥과 오른쪽으로 뻗은 두 팔
        beam(v, 23.5, 0.0, 23.5, 15.0, 1.5, j0, j1);
        beam(v, 23.5, 11.0, 28.5, 11.0, 1.2, j0, j1);
        beam(v, 23.5, 5.5, 28.5, 5.5, 1.2, j0, j1);
        v.fill(0, -1, 0, GATE_W - 1, -1, GATE_D - 1, POLISHED_ANDESITE);
        // 다리 밑 받침 (낮은 콘크리트 받침, 땅 위로 1칸)
        for (int[] foot : new int[][]{{0, 4}, {16, 20}, {21, 25}}) {
            v.fill(foot[0], -1, j0 - 1, foot[1], -1, j1 + 1, POLISHED_ANDESITE);
        }
        // 길 바닥 (다리 사이)
        v.fill(4, -1, 0, 16, -1, GATE_D - 1, POLISHED_GRANITE);
        // 학교 이름 (ㅑ 기둥, 큰길 쪽)
        v.set(22, 2, j1 + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", "서울대학교", "SEOUL NATIONAL", "UNIVERSITY"));
        v.set(24, 2, j0 - 1, Blocks.wallSign("dark_oak", "north", "white", false, "", "서울대학교"));
        return v;
    }

    /** 앞에서 본 (i, y) 평면의 굵은 선을 깊이 j0..j1 로 밀어 낸 강판 상자 */
    private static void beam(Voxels v, double ia, double ya, double ib, double yb, double r, int j0, int j1) {
        int i0 = (int) Math.floor(Math.min(ia, ib) - r), i1 = (int) Math.ceil(Math.max(ia, ib) + r);
        int y0 = Math.max(0, (int) Math.floor(Math.min(ya, yb) - r)), y1 = (int) Math.ceil(Math.max(ya, yb) + r);
        double dx = ib - ia, dy = yb - ya, len2 = dx * dx + dy * dy;
        for (int y = y0; y <= y1; y++) {
            for (int i = i0; i <= i1; i++) {
                double px = i + 0.5 - ia, py = y + 0.5 - ya;
                double t = Math.max(0, Math.min(1, (px * dx + py * dy) / len2));
                double qx = px - t * dx, qy = py - t * dy;
                if (qx * qx + qy * qy <= r * r) {
                    v.fill(i, y, j0, i, y, j1, STEEL);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 캠퍼스 땅

    /**
     * 캠퍼스 땅 (월드 축 그대로, 정면 남쪽). map[j][i] 의 칸 종류대로 깔고, 잔디밭에 나무, 길가에 벤치·보행등.
     * AVENUE 는 은행나무 가로수 길, OPEN 은 거점처럼 비워 둘 광장.
     */
    static Voxels grounds(int w, int d, char[][] map, Ground ground, Random r) {
        Voxels v = new Voxels(w, d, -1, 14);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                char c = map[j][i];
                Block b = switch (c) {
                    case LAWN -> GRASS;
                    case PATH -> Math.floorMod(i * 3 + j * 5, 11) == 0 ? STONE_BRICKS : POLISHED_ANDESITE;
                    case EDGE -> LIGHT_GRAY_CONCRETE;
                    case AVENUE -> (i + j) % 2 == 0 ? POLISHED_GRANITE : Block.of("granite", 0x956755);
                    case PLAZA, OPEN -> j % 6 == 0 || i % 6 == 0 ? POLISHED_ANDESITE : Block.of("light_gray_concrete", 0x7D7D73);
                    case WATER -> WATER_BLOCK;
                    case FIELD -> GRASS;
                    case SAND -> Block.of("sand", 0xDBD3A0);
                    case DIRT -> Block.of("dirt_path", 0x947A47);
                    default -> null;
                };
                if (b != null) {
                    v.set(i, -1, j, b);
                }
            }
        }
        // 가장자리 생울타리: 큰길 보도와 맞닿은 잔디 칸
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if ((map[j][i] == LAWN || map[j][i] == FIELD) && near(map, i, j, 1, EDGE, w, d) && ground.clear(i, j, 2)) {
                    v.set(i, 0, j, Kit.BOX_HEDGE);
                }
            }
        }
        // 가로수 길 양옆 은행나무, 광장 가장자리 느티나무
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (map[j][i] != LAWN) {
                    continue;
                }
                boolean byAvenue = near(map, i, j, 1, AVENUE, w, d);
                if (byAvenue && j % 6 == 0 && free(map, i, j, 2, w, d) && ground.clear(i, j, 3)) {
                    Kit.ginkgo(v, i, j, -1, r);
                }
            }
        }
        // 잔디밭 숲: 엇갈린 격자
        for (int j = 3; j < d - 3; j += 7) {
            for (int i = 3 + (j / 7 % 2) * 3; i < w - 3; i += 8) {
                int ii = i + r.nextInt(3) - 1, jj = j + r.nextInt(3) - 1;
                if (ii < 0 || jj < 0 || ii >= w || jj >= d || map[jj][ii] != LAWN) {
                    continue;
                }
                if (!free(map, ii, jj, 4, w, d) || !ground.clear(ii, jj, 5) || v.get(ii, 0, jj) != null) {
                    continue;
                }
                if (near(v, ii, jj, 3)) {
                    continue;
                }
                if (r.nextInt(3) == 0) {
                    Kit.pine(v, ii, jj, -1, r);
                } else {
                    Kit.zelkova(v, ii, jj, -1, r);
                }
            }
        }
        // 길가 벤치·보행등: 길과 맞닿은 잔디 칸
        for (int j = 1; j < d - 1; j++) {
            for (int i = 1; i < w - 1; i++) {
                if (map[j][i] != LAWN || v.get(i, 0, j) != null || !ground.clear(i, j, 1)) {
                    continue;
                }
                String face = null;
                if (isWalk(map[j - 1][i]) && map[j + 1][i] == LAWN) {
                    face = "north";
                } else if (isWalk(map[j + 1][i]) && map[j - 1][i] == LAWN) {
                    face = "south";
                } else if (isWalk(map[j][i - 1]) && map[j][i + 1] == LAWN) {
                    face = "west";
                } else if (isWalk(map[j][i + 1]) && map[j][i - 1] == LAWN) {
                    face = "east";
                }
                if (face == null) {
                    continue;
                }
                boolean alongI = face.equals("north") || face.equals("south");
                int along = alongI ? i : j;
                if (Math.floorMod(along, 16) == 0 && (face.equals("north") || face.equals("west"))) {
                    Kit.parkLamp(v, i, 0, j);
                } else if (Math.floorMod(along, 14) == 6 && r.nextInt(3) > 0) {
                    boolean ok = true;
                    for (int k = 0; k < 3 && ok; k++) {
                        int bi = alongI ? i + k : i, bj = alongI ? j : j + k;
                        ok = bi < w && bj < d && map[bj][bi] == LAWN && v.get(bi, 0, bj) == null;
                    }
                    if (ok) {
                        Kit.bench(v, i, 0, j, 3, alongI, face);
                    }
                }
            }
        }
        v.connect();
        return v;
    }

    private static boolean isWalk(char c) {
        return c == PATH || c == PLAZA || c == AVENUE || c == OPEN || c == DIRT;
    }

    /** (i, j) 둘레 r 칸이 모두 잔디인지 */
    private static boolean free(char[][] map, int i, int j, int r, int w, int d) {
        for (int dj = -r; dj <= r; dj++) {
            for (int di = -r; di <= r; di++) {
                int a = i + di, b = j + dj;
                if (a < 0 || b < 0 || a >= w || b >= d || map[b][a] != LAWN) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean near(char[][] map, int i, int j, int r, char c, int w, int d) {
        for (int dj = -r; dj <= r; dj++) {
            for (int di = -r; di <= r; di++) {
                int a = i + di, b = j + dj;
                if (a >= 0 && b >= 0 && a < w && b < d && map[b][a] == c) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 둘레 r 칸 안에 이미 나무 줄기가 있는지 */
    private static boolean near(Voxels v, int i, int j, int r) {
        for (int dj = -r; dj <= r; dj++) {
            for (int di = -r; di <= r; di++) {
                if (v.solid(i + di, 1, j + dj)) {
                    return true;
                }
            }
        }
        return false;
    }

    private SnuCampus() {
    }
}
