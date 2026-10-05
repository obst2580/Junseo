package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.Noise;

import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 공항 섬의 큰 구조물들: 비행기, 관제탑, 진입로의 거대 조형물(무인석·지구본·홍살문).
 * 모두 건물 좌표에서 정면(+j)을 기준으로 짓고 {@link Placement#rotated} 로 돌려 놓습니다.
 */
final class Landmarks {

    // ------------------------------------------------------------------ 비행기

    /** 여객기 상자 크기 */
    static final int PLANE_W = 37, PLANE_D = 37;

    /** 여객기: 기수가 +j. 하늘색 윗동체, 은색 아랫동체, 창문 줄, 엔진 둘, 꼬리날개에 태극 원 */
    static Voxels airplane() {
        Voxels v = new Voxels(PLANE_W, PLANE_D, -1, 15);
        double ci = PLANE_W / 2.0, cy = 4.5, r = 2.6;
        int nose = PLANE_D - 1;
        for (int j = 0; j <= nose; j++) {
            double t = j + 0.5;
            // 동체 반지름과 중심 높이: 꼬리 쪽은 가늘어지며 올라가고, 기수는 둥글게
            double rr = r, yc = cy;
            if (t < 9) {
                double k = t / 9;
                rr = 0.9 + (r - 0.9) * k;
                yc = cy + (1 - k) * 1.6;
            } else if (t > nose - 5) {
                double k = (t - (nose - 5)) / 5.5;
                rr = r * Math.sqrt(Math.max(0, 1 - k * k));
            }
            for (int y = 0; y <= 9; y++) {
                for (int i = 0; i < PLANE_W; i++) {
                    double dx = i + 0.5 - ci, dy = y + 0.5 - yc;
                    if (dx * dx + dy * dy <= rr * rr) {
                        Block b = dy > 0.3 ? LIGHT_BLUE_CONCRETE : dy > -0.8 ? WHITE_CONCRETE : LIGHT_GRAY_CONCRETE;
                        // 창문 줄
                        if (Math.abs(dy - 0.9) < 0.5 && Math.abs(Math.abs(dx) - rr) < 0.8 && t > 9 && t < nose - 5 && j % 2 == 0) {
                            b = BLACK_GLASS;
                        }
                        // 조종석 창
                        if (t > nose - 4 && t < nose - 1.5 && dy > 0.4 && dy < 1.6) {
                            b = BLACK_GLASS;
                        }
                        v.set(i, y, j, b);
                    }
                }
            }
        }
        // 날개: 뒤로 젖혀진 낮은 날개
        for (int i = 0; i < PLANE_W; i++) {
            double x = Math.abs(i + 0.5 - ci);
            if (x < 2 || x > 17.5) {
                continue;
            }
            double le = 21 - x * 0.62, te = 14 - x * 0.28;
            int y = 3 + (int) Math.floor(x / 9);
            for (int j = (int) Math.floor(te); j <= (int) Math.floor(le); j++) {
                v.set(i, y, j, x > 16.5 ? RED_CONCRETE : WHITE_CONCRETE);
            }
        }
        // 엔진
        for (int side : new int[]{-1, 1}) {
            double ei = ci + side * 7.5;
            for (int j = 15; j <= 20; j++) {
                for (int y = 0; y <= 3; y++) {
                    for (int i = (int) ei - 2; i <= (int) ei + 2; i++) {
                        double dx = i + 0.5 - ei, dy = y + 0.5 - 1.8;
                        if (dx * dx + dy * dy <= 1.45 * 1.45) {
                            v.set(i, y, j, j == 20 ? GRAY_CONCRETE : LIGHT_GRAY_CONCRETE);
                        }
                    }
                }
            }
        }
        // 수평 꼬리날개
        for (int i = 0; i < PLANE_W; i++) {
            double x = Math.abs(i + 0.5 - ci);
            if (x > 7.5) {
                continue;
            }
            for (int j = (int) Math.floor(1 + x * 0.35); j <= (int) Math.floor(5 - x * 0.1); j++) {
                v.set(i, 6, j, WHITE_CONCRETE);
            }
        }
        // 수직 꼬리날개 (태극 원)
        int fi = (int) Math.floor(ci);
        for (int y = 7; y <= 14; y++) {
            int h = y - 7;
            for (int j = (int) Math.floor(h * 0.7); j <= (int) Math.floor(7 - h * 0.25); j++) {
                v.set(fi, y, j, LIGHT_BLUE_CONCRETE);
            }
        }
        for (int y = 9; y <= 12; y++) {
            for (int j = 3; j <= 6; j++) {
                double dx = j + 0.5 - 4.6, dy = y + 0.5 - 10.6;
                if (dx * dx + dy * dy <= 2.3 * 2.3) {
                    v.set(fi, y, j, taegeuk(dx / 2.3, dy / 2.3) ? RED_CONCRETE : BLUE_CONCRETE);
                }
            }
        }
        // 바퀴
        v.fill(fi, 0, nose - 6, fi, 1, nose - 6, GRAY_CONCRETE);
        v.fill(fi - 3, 0, 17, fi - 3, 1, 17, GRAY_CONCRETE);
        v.fill(fi + 3, 0, 17, fi + 3, 1, 17, GRAY_CONCRETE);
        return v;
    }

    /** 태극 무늬: 위 빨강, 아래 파랑, 가운데 S 곡선. (x, y) 는 원 안 정규 좌표 (y 위쪽) */
    static boolean taegeuk(double x, double y) {
        double l = Math.hypot(x + 0.5, y), r = Math.hypot(x - 0.5, y);
        if (l <= 0.5) {
            return true;
        }
        if (r <= 0.5) {
            return false;
        }
        return y > 0;
    }

    // ------------------------------------------------------------------ 관제탑

    static final int TOWER = 21;

    /** 관제탑: 2층 받침 건물, 흰 원기둥, 위가 넓은 유리 관제실, 안테나와 경고등 */
    static Voxels controlTower() {
        Voxels v = new Voxels(TOWER, TOWER, -1, 66);
        double c = TOWER / 2.0;
        v.fill(2, -1, 2, TOWER - 3, -1, TOWER - 3, SMOOTH_STONE);
        v.walls(3, 0, 3, TOWER - 4, 7, TOWER - 4, WHITE_CONCRETE);
        v.fill(3, 8, 3, TOWER - 4, 8, TOWER - 4, LIGHT_GRAY_CONCRETE);
        for (int k = 4; k < TOWER - 4; k++) {
            if (k % 3 != 0) {
                for (int y : new int[]{1, 2, 5, 6}) {
                    v.set(k, y, 3, LIGHT_BLUE_GLASS);
                    v.set(k, y, TOWER - 4, LIGHT_BLUE_GLASS);
                    v.set(3, y, k, LIGHT_BLUE_GLASS);
                    v.set(TOWER - 4, y, k, LIGHT_BLUE_GLASS);
                }
            }
        }
        v.fill((int) c - 1, 0, TOWER - 4, (int) c, 2, TOWER - 4, AIR);
        v.cylinder(c, c, 3.4, 9, 46, WHITE_CONCRETE);
        // 엘리베이터 유리 줄
        for (int y = 10; y <= 45; y++) {
            v.set((int) c, y, (int) Math.floor(c + 3.0), LIGHT_BLUE_GLASS);
        }
        // 관제실: 넓어지는 받침 → 유리 고리 → 지붕
        for (int y = 47; y <= 50; y++) {
            v.cylinder(c, c, 3.4 + (y - 46) * 1.0, y, y, y == 50 ? SMOOTH_STONE : WHITE_CONCRETE);
        }
        v.tube(c, c, 7.6, 1.2, 51, 54, LIGHT_BLUE_GLASS);
        v.cylinder(c, c, 6.4, 51, 54, null);
        v.cylinder(c, c, 1.4, 51, 54, WHITE_CONCRETE);
        v.cylinder(c, c, 8.4, 55, 55, WHITE_CONCRETE);
        v.cylinder(c, c, 5.4, 56, 56, LIGHT_GRAY_CONCRETE);
        v.fill((int) c, 57, (int) c, (int) c, 63, (int) c, IRON_BARS);
        v.set((int) c, 64, (int) c, SHROOMLIGHT);
        v.set((int) c, 52, (int) c, SEA_LANTERN);
        return v;
    }

    // ------------------------------------------------------------------ 무인석 (돌 장군상)

    static final int STATUE_W = 17, STATUE_D = 15, STATUE_H = 42;

    /**
     * 거대한 무인석: 투구를 쓰고 두 손으로 칼자루를 짚고 선 돌 장군. 받침 4칸 + 키 34칸.
     * 정면(+j)이 길을 봅니다.
     */
    static Voxels colossus(long seed) {
        Voxels v = new Voxels(STATUE_W, STATUE_D, -1, STATUE_H);
        Random r = new Random(seed);
        double ci = STATUE_W / 2.0, cj = 6.5;
        int base = 4, fh = 34;
        // 받침
        v.fill(0, -1, 0, STATUE_W - 1, -1, STATUE_D - 1, POLISHED_ANDESITE);
        v.fill(0, 0, 0, STATUE_W - 1, 0, STATUE_D - 1, POLISHED_ANDESITE);
        v.fill(1, 1, 1, STATUE_W - 2, 2, STATUE_D - 2, STONE_BRICKS);
        v.fill(0, 3, 0, STATUE_W - 1, 3, STATUE_D - 1, POLISHED_ANDESITE);
        for (int i = 2; i < STATUE_W - 2; i += 3) {
            v.set(i, 1, STATUE_D - 2, SEA_LANTERN); // 받침 앞 조명
        }
        for (int y = base; y < base + fh; y++) {
            double h = (y - base + 0.5) / fh;
            for (int j = 0; j < STATUE_D; j++) {
                for (int i = 0; i < STATUE_W; i++) {
                    double dx = i + 0.5 - ci, dz = j + 0.5 - cj;
                    Block b = null;
                    if (h < 0.64) {
                        // 도포 자락: 아래가 조금 넓고, 둥근 네모
                        double hw = 5.4 - 0.4 * h, hd = 3.7;
                        if (h > 0.58) {
                            hw = 6.0; // 어깨
                        }
                        if (superEllipse(dx / hw, dz / hd, 4) <= 1) {
                            b = stone(r);
                            if (h < 0.05 || (h > 0.40 && h < 0.43)) {
                                b = POLISHED_DEEPSLATE; // 단·허리띠
                            } else if (h > 0.45 && h < 0.58 && dz > hd - 1.2 && Math.abs(dx) < 3.5) {
                                b = (y % 2 == 0) ? STONE_BRICKS : POLISHED_ANDESITE; // 가슴 갑옷
                            }
                        }
                    } else if (h < 0.69) {
                        if (superEllipse(dx / 2.6, dz / 2.6, 2) <= 1) {
                            b = stone(r); // 목
                        }
                    } else if (h < 0.9) {
                        if (superEllipse(dx / 3.7, dz / 3.6, 3) <= 1) {
                            b = stone(r); // 얼굴
                        }
                    } else {
                        // 투구
                        double k = (h - 0.9) / 0.1;
                        double rr = 4.3 * Math.sqrt(Math.max(0, 1 - k * k));
                        if (dx * dx + dz * dz <= rr * rr) {
                            b = h < 0.92 ? DEEPSLATE_TILES : POLISHED_DEEPSLATE;
                        }
                    }
                    if (b != null) {
                        v.set(i, y, j, b);
                    }
                }
            }
        }
        // 투구 챙과 꼭대기 장식
        int helmY = base + (int) (fh * 0.9);
        v.tube(ci, cj, 4.6, 1.2, helmY, helmY, DEEPSLATE_TILES);
        v.fill((int) ci, base + fh, (int) cj, (int) ci, base + fh + 2, (int) cj, GOLD_BLOCK);
        // 얼굴: 부릅뜬 눈, 짙은 눈썹, 코, 수염
        int face = (int) Math.floor(cj + 3.6);
        int eyeY = base + (int) (fh * 0.82);
        for (int side : new int[]{-1, 1}) {
            int ei = (int) Math.floor(ci + side * 1.6);
            v.set(ei, eyeY, face, BLACK_CONCRETE);
            v.set(ei, eyeY + 1, face + 1, POLISHED_DEEPSLATE);
            v.set(ei + side, eyeY + 1, face + 1, POLISHED_DEEPSLATE);
            v.set(ei, eyeY - 3, face, POLISHED_DEEPSLATE);
        }
        v.set((int) ci, eyeY - 1, face + 1, stone(r));
        v.set((int) ci, eyeY - 2, face + 1, stone(r));
        v.fill((int) ci - 1, eyeY - 4, face, (int) ci + 1, eyeY - 5, face + 1, POLISHED_DEEPSLATE);
        // 팔: 어깨에서 가슴 앞 칼자루로
        double handY = base + fh * 0.5, handJ = cj + 5.2;
        for (int side : new int[]{-1, 1}) {
            v.rod(ci + side * 5.2, base + fh * 0.6, cj + 0.5, ci + side * 1.3, handY, handJ, 1.7, stone(r));
        }
        // 칼: 받침에 꽂힌 칼날, 금색 코등이, 손, 칼자루 끝
        int si = (int) Math.floor(ci), sj = (int) Math.floor(handJ) + 1;
        v.fill(si, base, sj, si, (int) handY - 2, sj, IRON_BLOCK);
        v.fill(si - 2, (int) handY - 1, sj, si + 2, (int) handY - 1, sj, GOLD_BLOCK);
        v.fill(si - 1, (int) handY, sj - 1, si + 1, (int) handY + 1, sj, stone(r));
        v.set(si, (int) handY + 2, sj, GOLD_BLOCK);
        // 발
        for (int side : new int[]{-1, 1}) {
            v.fill((int) Math.floor(ci + side * 2.2), base, (int) Math.floor(cj + 3.7), (int) Math.floor(ci + side * 2.2) + side, base, (int) Math.floor(cj + 4.6), POLISHED_DEEPSLATE);
        }
        return v;
    }

    private static Block stone(Random r) {
        int k = r.nextInt(10);
        return k < 5 ? POLISHED_ANDESITE : k < 8 ? ANDESITE : STONE;
    }

    private static double superEllipse(double x, double z, double p) {
        return Math.pow(Math.abs(x), p) + Math.pow(Math.abs(z), p);
    }

    // ------------------------------------------------------------------ 지구본 조형물

    static final int GLOBE = 45;

    /** 계단식 흰 받침 위의 커다란 지구본(대륙·위경선)과 금빛 고리 세 개, 둘레 광장 */
    static Voxels globe() {
        Voxels v = new Voxels(GLOBE, GLOBE, -1, 46);
        double c = GLOBE / 2.0;
        Noise noise = new Noise(4242);
        // 광장
        for (int j = 0; j < GLOBE; j++) {
            for (int i = 0; i < GLOBE; i++) {
                double rr = Math.hypot(i + 0.5 - c, j + 0.5 - c);
                if (rr < GLOBE / 2.0) {
                    double ang = Math.toDegrees(Math.atan2(j + 0.5 - c, i + 0.5 - c));
                    v.set(i, -1, j, ((int) Math.floor((ang + 360) / 15)) % 2 == 0 ? POLISHED_DIORITE : SMOOTH_STONE);
                    if (rr > GLOBE / 2.0 - 1.5) {
                        v.set(i, -1, j, RED_TERRACOTTA);
                    }
                }
            }
        }
        // 받침
        v.cylinder(c, c, 14, 0, 1, QUARTZ);
        v.cylinder(c, c, 11, 2, 4, SMOOTH_QUARTZ);
        v.tube(c, c, 11, 1, 4, 4, GOLD_BLOCK);
        v.cylinder(c, c, 6, 5, 8, QUARTZ_PILLAR);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            v.set((int) Math.floor(c + 13.5 * Math.cos(a)), 1, (int) Math.floor(c + 13.5 * Math.sin(a)), SEA_LANTERN);
        }
        // 지구
        double gy = 22.5, R = 13;
        for (int y = 8; y <= 37; y++) {
            for (int j = 0; j < GLOBE; j++) {
                for (int i = 0; i < GLOBE; i++) {
                    double dx = i + 0.5 - c, dy = y + 0.5 - gy, dz = j + 0.5 - c;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > R || d < R - 1.8) {
                        continue;
                    }
                    double lat = Math.toDegrees(Math.asin(dy / d)), lon = Math.toDegrees(Math.atan2(dz, dx));
                    boolean grid = Math.abs(((lat % 30) + 30) % 30 - 15) > 13.2 || Math.abs(((lon % 30) + 30) % 30 - 15) > 13.6;
                    // 대륙: 구 위 점으로 노이즈 (이음매 없음)
                    double n = noise.fbm(dx / R * 1.6 + dz / R * 0.9, dy / R * 1.6 - dz / R * 0.7, 3)
                            + 0.5 * noise.fbm(dz / R * 1.3 + 7, dx / R * 1.3 - dy / R * 0.8 + 3, 2);
                    boolean land = n > 0.12 && Math.abs(lat) < 72;
                    boolean ice = Math.abs(lat) > 74;
                    Block b = grid ? GOLD_BLOCK : ice ? WHITE_CONCRETE : land ? (n > 0.35 ? GREEN_CONCRETE : LIME_CONCRETE) : (n < -0.2 ? BLUE_CONCRETE : LIGHT_BLUE_CONCRETE);
                    v.set(i, y, j, b);
                }
            }
        }
        // 고리 셋: 적도, 기울어진 고리(23.5도), 세로 고리
        ring(v, c, gy, c, 17.5, 0, GOLD_BLOCK);
        ring(v, c, gy, c, 17.5, 23.5, GOLD_BLOCK);
        verticalRing(v, c, gy, c, 17.5, GOLD_BLOCK);
        v.set((int) c, (int) (gy + 18.5), (int) c, SEA_LANTERN);
        return v;
    }

    /** x 축으로 tilt 만큼 기울인 둥근 고리 */
    private static void ring(Voxels v, double ci, double cy, double cj, double radius, double tilt, Block b) {
        double t = Math.toRadians(tilt);
        for (double a = 0; a < 360; a += 0.6) {
            double rad = Math.toRadians(a);
            double x = radius * Math.cos(rad), z = radius * Math.sin(rad);
            double y = -z * Math.sin(t), zz = z * Math.cos(t);
            v.set((int) Math.floor(ci + x), (int) Math.floor(cy + y), (int) Math.floor(cj + zz), b);
        }
    }

    private static void verticalRing(Voxels v, double ci, double cy, double cj, double radius, Block b) {
        for (double a = 0; a < 360; a += 0.6) {
            double rad = Math.toRadians(a);
            v.set((int) Math.floor(ci + radius * Math.cos(rad)), (int) Math.floor(cy + radius * Math.sin(rad)), (int) Math.floor(cj), b);
        }
        // 고리 아래를 받침 기둥에 붙임
        v.fill((int) Math.floor(ci), 8, (int) Math.floor(cj), (int) Math.floor(ci), (int) Math.floor(cy - radius) + 1, (int) Math.floor(cj), GOLD_BLOCK);
    }

    // ------------------------------------------------------------------ 홍살문

    static final int GATE_D = 5;

    /**
     * 길 위에 걸친 거대한 홍살문: 빨간 두 기둥, 가로대 둘, 그 사이에 하늘로 솟은 붉은 살, 가운데 태극.
     * w 는 길을 가로지르는 폭 (길은 j 방향으로 지나감).
     */
    static Voxels hongsalGate(int w) {
        Voxels v = new Voxels(w, GATE_D, -1, 42);
        double cj = GATE_D / 2.0;
        for (double pi : new double[]{3.5, w - 3.5}) {
            v.cylinder(pi, cj, 2.5, 0, 36, RED_CONCRETE);
            v.cylinder(pi, cj, 3.2, -1, 1, POLISHED_ANDESITE); // 주춧돌
            v.set((int) Math.floor(pi), 37, (int) Math.floor(cj), GOLD_BLOCK);
        }
        int jm = (int) Math.floor(cj);
        v.fill(1, 25, jm - 1, w - 2, 27, jm + 1, RED_CONCRETE);
        v.fill(1, 33, jm - 1, w - 2, 34, jm + 1, RED_CONCRETE);
        // 살: 2칸마다, 위 가로대 위로 솟고 끝은 검게
        for (int i = 6; i < w - 6; i += 2) {
            v.fill(i, 28, jm, i, 39, jm, RED_CONCRETE);
            v.set(i, 40, jm, POLISHED_BLACKSTONE);
        }
        // 가운데 태극 (두 가로대 사이, 앞뒤 양면)
        double tc = w / 2.0, ty = 31.5, tr = 5.6;
        for (int y = 25; y <= 38; y++) {
            for (int i = (int) (tc - tr) - 1; i <= (int) (tc + tr) + 1; i++) {
                double x = (i + 0.5 - tc) / tr, yy = (y + 0.5 - ty) / tr;
                if (x * x + yy * yy <= 1) {
                    Block b = taegeuk(x, yy) ? RED_CONCRETE : BLUE_CONCRETE;
                    for (int j = jm - 1; j <= jm + 1; j++) {
                        v.set(i, y, j, b);
                    }
                }
            }
        }
        // 가로대 아래 조명
        for (int i = 6; i < w - 6; i += 4) {
            v.set(i, 24, jm, LANTERN_HANGING);
        }
        return v;
    }

    private Landmarks() {
    }
}
