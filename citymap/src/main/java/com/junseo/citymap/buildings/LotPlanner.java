package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 도로로 둘러싸인 땅 덩어리를 건물 터(필지)로 나눕니다.
 * 덩어리를 감싸는 직사각형을 반씩 계속 자르고(사이에 골목을 남김), 조각이 지을 수 없는 땅에 걸치면
 * 걸친 변을 깎아 냅니다.
 */
final class LotPlanner {
    /** 건물 터 [x0..x1]×[z0..z1] 과 정면 방향 */
    record Lot(int x0, int z0, int x1, int z1, String front) {
        int sizeX() {
            return x1 - x0 + 1;
        }

        int sizeZ() {
            return z1 - z0 + 1;
        }

        /** 정면에서 본 너비 */
        int width() {
            return front.equals("south") || front.equals("north") ? sizeX() : sizeZ();
        }

        /** 정면에서 본 깊이 */
        int depth() {
            return front.equals("south") || front.equals("north") ? sizeZ() : sizeX();
        }
    }

    private final BuildMask mask;
    private final Random rnd;
    private final int minSide, maxSide, gapMin, gapMax;

    LotPlanner(BuildMask mask, Random rnd, int minSide, int maxSide, int gapMin, int gapMax) {
        this.mask = mask;
        this.rnd = rnd;
        this.minSide = minSide;
        this.maxSide = maxSide;
        this.gapMin = gapMin;
        this.gapMax = gapMax;
    }

    List<Lot> split(int[] block) {
        List<Lot> out = new ArrayList<>();
        split(block[0], block[1], block[2], block[3], out);
        return out;
    }

    private void split(int xa, int za, int xb, int zb, List<Lot> out) {
        int sx = xb - xa + 1, sz = zb - za + 1;
        if (sx < minSide || sz < minSide) {
            return;
        }
        // 이 조각의 최대 크기는 조각마다 조금씩 다르게
        int limit = minSide + rnd.nextInt(Math.max(1, maxSide - minSide + 1));
        if (sx <= Math.max(limit, minSide * 2 - 1) && sz <= Math.max(limit, minSide * 2 - 1)) {
            leaf(xa, za, xb, zb, out);
            return;
        }
        int gap = gapMin + rnd.nextInt(gapMax - gapMin + 1);
        if (sx >= sz) {
            int cut = xa + (int) (sx * (0.38 + 0.24 * rnd.nextDouble()));
            split(xa, za, cut - 1, zb, out);
            split(cut + gap, za, xb, zb, out);
        } else {
            int cut = za + (int) (sz * (0.38 + 0.24 * rnd.nextDouble()));
            split(xa, za, xb, cut - 1, out);
            split(xa, cut + gap, xb, zb, out);
        }
    }

    /** 지을 수 없는 칸이 없을 때까지 가장 나쁜 변을 깎아 냄 */
    private void leaf(int xa, int za, int xb, int zb, List<Lot> out) {
        while (xb - xa + 1 >= minSide && zb - za + 1 >= minSide) {
            int area = (xb - xa + 1) * (zb - za + 1);
            int free = mask.count(xa, za, xb, zb);
            if (free == area) {
                String front = mask.frontOf(xa, za, xb, zb);
                out.add(new Lot(xa, za, xb, zb, front));
                return;
            }
            if (free < area / 3) {
                return;
            }
            int north = (xb - xa + 1) - mask.count(xa, za, xb, za);
            int south = (xb - xa + 1) - mask.count(xa, zb, xb, zb);
            int west = (zb - za + 1) - mask.count(xa, za, xa, zb);
            int east = (zb - za + 1) - mask.count(xb, za, xb, zb);
            int worst = Math.max(Math.max(north, south), Math.max(west, east));
            if (worst == 0) {
                return; // 가운데에 구멍(거점 바닥 등)이 있는 조각은 버림
            }
            if (worst == north) {
                za++;
            } else if (worst == south) {
                zb--;
            } else if (worst == west) {
                xa++;
            } else {
                xb--;
            }
        }
    }
}
