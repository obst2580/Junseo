package com.junseo.citymap.terrain;

import java.util.Random;

/**
 * 2D 펄린 노이즈. 산을 자연스럽게 울퉁불퉁하게 만들 때 씁니다.
 * 같은 씨앗이면 언제나 같은 값이 나와서, 월드를 다시 만들어도 지형이 똑같습니다.
 */
public final class Noise {
    private final int[] perm = new int[512];

    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    /** 대략 -1 ~ 1 */
    public double noise(double x, double z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double xf = x - xi, zf = z - zi;
        int X = xi & 255, Z = zi & 255;
        double u = fade(xf), v = fade(zf);
        int aa = perm[perm[X] + Z], ab = perm[perm[X] + Z + 1];
        int ba = perm[perm[X + 1] + Z], bb = perm[perm[X + 1] + Z + 1];
        double x1 = lerp(u, grad(aa, xf, zf), grad(ba, xf - 1, zf));
        double x2 = lerp(u, grad(ab, xf, zf - 1), grad(bb, xf - 1, zf - 1));
        return lerp(v, x1, x2) * 1.41;
    }

    /** 여러 겹(옥타브)을 겹친 노이즈, 대략 -1 ~ 1 */
    public double fbm(double x, double z, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * freq, z * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double z) {
        return switch (hash & 7) {
            case 0 -> x + z;
            case 1 -> -x + z;
            case 2 -> x - z;
            case 3 -> -x - z;
            case 4 -> x;
            case 5 -> -x;
            case 6 -> z;
            default -> -z;
        };
    }
}
