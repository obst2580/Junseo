package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 건물 하나를 월드 어디에 어느 방향으로 놓는지.
 * 블록 상자({@link Voxels})는 처음 필요할 때 만들고, 메모리가 모자라면 버렸다가 다시 만듭니다
 * (같은 설계도면 늘 같은 결과).
 * <p>
 * 건물 좌표 (a, b) → 월드 좌표 = 원점 + 회전(a, b). 각도가 90도 단위가 아니면 칸을 몇 군데 더 찍어 보고
 * 벽이 끊기지 않게 채웁니다.
 */
public final class Placement {
    /** 생성기 칸 하나에 블록을 놓는 곳. y 는 월드 높이 */
    public interface Sink {
        void set(int y, Block block);
    }

    public final String name;
    /** 건물 종류 (미니맵 색, 검사용): "terminal", "dome", "market", "house", "factory", "statue", "plaza" 등 */
    public final String kind;
    private final double ox, oz, cos, sin;
    private final boolean exact;
    private final int quarter;
    private final int w, d;
    private final Supplier<Voxels> builder;
    private final double[] bounds;
    private final Polygon footprint;
    private volatile SoftReference<Built> cache = new SoftReference<>(null);

    private record Built(Voxels voxels, Block[] palette, List<int[]> signs) {
    }

    /** 표지판 자리 (월드 좌표)와 블록 (글씨 포함) */
    public record SignSpot(int x, int y, int z, Block block) {
    }

    private Placement(String name, String kind, double ox, double oz, double angleDeg, int w, int d,
                      List<double[]> localFootprint, Supplier<Voxels> builder) {
        this.name = name;
        this.kind = kind;
        this.ox = ox;
        this.oz = oz;
        double rad = Math.toRadians(angleDeg);
        double c = Math.cos(rad), s = Math.sin(rad);
        double q = angleDeg / 90.0;
        this.exact = Math.abs(q - Math.rint(q)) < 1e-9;
        if (exact) {
            c = Math.rint(c);
            s = Math.rint(s);
        }
        this.cos = c;
        this.sin = s;
        this.quarter = (int) Math.floorMod(Math.round(q), 4L);
        this.w = w;
        this.d = d;
        this.builder = builder;
        List<double[]> corners = List.of(new double[]{0, 0}, new double[]{w, 0}, new double[]{w, d}, new double[]{0, d});
        double[] bb = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : corners) {
            double[] wp = toWorld(p[0], p[1]);
            bb[0] = Math.min(bb[0], wp[0]);
            bb[1] = Math.min(bb[1], wp[1]);
            bb[2] = Math.max(bb[2], wp[0]);
            bb[3] = Math.max(bb[3], wp[1]);
        }
        this.bounds = bb;
        List<double[]> fp = new ArrayList<>();
        for (double[] p : localFootprint == null ? corners : localFootprint) {
            fp.add(toWorld(p[0], p[1]));
        }
        this.footprint = new Polygon(fp);
    }

    /**
     * 땅 위 직사각형 [x0..x1]×[z0..z1] (블록, 양 끝 포함) 에 놓습니다. front 는 건물 정면이 바라보는 쪽
     * ("south", "west", "north", "east"). 건물 상자 크기는 정면 방향에 맞춰 정해집니다.
     */
    public static Placement rect(String name, String kind, int x0, int z0, int x1, int z1, String front,
                                 java.util.function.BiFunction<Integer, Integer, Voxels> build) {
        return rect(name, kind, x0, z0, x1, z1, front, null, build);
    }

    /**
     * {@link #rect} 와 같고, 건물이 땅을 차지하는 모양을 건물 좌표(a = 정면 너비 방향, b = 깊이 방향)의
     * 다각형으로 줍니다 (만드는 함수는 건물 상자 크기 w, d 를 받음).
     */
    public static Placement rect(String name, String kind, int x0, int z0, int x1, int z1, String front,
                                 java.util.function.BiFunction<Integer, Integer, List<double[]>> footprint,
                                 java.util.function.BiFunction<Integer, Integer, Voxels> build) {
        int q = switch (front) {
            case "south" -> 0;
            case "west" -> 1;
            case "north" -> 2;
            case "east" -> 3;
            default -> throw new IllegalArgumentException(front);
        };
        int sx = x1 - x0 + 1, sz = z1 - z0 + 1;
        int w = q % 2 == 0 ? sx : sz, d = q % 2 == 0 ? sz : sx;
        double ox = switch (q) {
            case 0, 3 -> x0;
            default -> x1 + 1;
        };
        double oz = switch (q) {
            case 0, 1 -> z0;
            default -> z1 + 1;
        };
        return new Placement(name, kind, ox, oz, q * 90, w, d, footprint == null ? null : footprint.apply(w, d), () -> build.apply(w, d));
    }

    /**
     * 아무 각도로 놓습니다. 건물 좌표 (pivotA, pivotB) 가 월드 (wx, wz) 에 오고, 건물의 +a 쪽이
     * 월드에서 angleDeg 방향(0 = 동쪽, 90 = 남쪽)을 봅니다.
     */
    public static Placement rotated(String name, String kind, double wx, double wz, double angleDeg, int w, int d,
                                    double pivotA, double pivotB, List<double[]> localFootprint, Supplier<Voxels> build) {
        double rad = Math.toRadians(angleDeg);
        double c = Math.cos(rad), s = Math.sin(rad);
        double ox = wx - (pivotA * c - pivotB * s), oz = wz - (pivotA * s + pivotB * c);
        return new Placement(name, kind, ox, oz, angleDeg, w, d, localFootprint, build);
    }

    double[] toWorld(double a, double b) {
        return new double[]{ox + a * cos - b * sin, oz + a * sin + b * cos};
    }

    /** 월드 바깥 상자 {minX, minZ, maxX, maxZ} */
    public double[] bounds() {
        return bounds.clone();
    }

    double[] boundsRef() {
        return bounds;
    }

    /** 건물이 땅을 차지하는 모양 (미니맵·검사용, 월드 좌표) */
    public Polygon footprint() {
        return footprint;
    }

    public boolean covers(double x, double z) {
        return x >= bounds[0] && x <= bounds[2] && z >= bounds[1] && z <= bounds[3] && footprint.contains(x, z);
    }

    private Built built() {
        Built b = cache.get();
        if (b == null) {
            synchronized (this) {
                b = cache.get();
                if (b == null) {
                    Voxels v = builder.get();
                    Block[] pal = new Block[v.palette().size() + 1];
                    boolean anyText = false;
                    for (int k = 0; k < v.palette().size(); k++) {
                        pal[k + 1] = v.palette().get(k).rotate(quarter);
                        anyText |= pal[k + 1].text() != null;
                    }
                    List<int[]> signs = new ArrayList<>();
                    if (anyText && exact) {
                        for (int y = v.y0; y < v.y0 + v.h; y++) {
                            for (int j = 0; j < v.d; j++) {
                                for (int i = 0; i < v.w; i++) {
                                    int c = v.raw(i, y, j);
                                    if (c != 0 && pal[c].text() != null) {
                                        signs.add(new int[]{i, y, j, c});
                                    }
                                }
                            }
                        }
                    }
                    b = new Built(v, pal, signs);
                    cache = new SoftReference<>(b);
                }
            }
        }
        return b;
    }

    /** 이 건물의 블록 상자 (미리보기·검사용) */
    public Voxels voxels() {
        return built().voxels;
    }

    /**
     * 글씨가 있는 표지판들 (월드 좌표). 생성기는 블록만 놓고, 글씨는 청크를 처음 불러올 때 이걸로 씁니다.
     * 비스듬히 놓인 건물에는 표지판을 두지 않습니다.
     */
    public List<SignSpot> signs(int baseY) {
        Built b = built();
        List<SignSpot> out = new ArrayList<>(b.signs.size());
        for (int[] s : b.signs) {
            double[] w = toWorld(s[0] + 0.5, s[2] + 0.5);
            out.add(new SignSpot((int) Math.floor(w[0]), baseY + s[1], (int) Math.floor(w[1]), b.palette[s[3]]));
        }
        return out;
    }

    /** 놓인 방향에 맞게 돌린 블록 */
    Block rotated(Block b) {
        return b == null ? null : b.rotate(quarter);
    }

    private static final double[][] SAMPLES = {{0, 0}, {0.3, 0.3}, {-0.3, 0.3}, {0.3, -0.3}, {-0.3, -0.3}};

    /**
     * 월드 칸 (x, z) 세로줄에 이 건물이 놓는 블록을 sink 에 넣습니다.
     *
     * @param baseY 건물 y = 0 이 오는 월드 높이 (땅 윗면 + 1)
     * @param minY  이 높이(월드)보다 낮은 블록은 놓지 않음 (도로 위로 지나가는 부분 등)
     */
    public void column(int x, int z, int baseY, int minY, Sink sink) {
        Built built = built();
        Voxels v = built.voxels;
        int from = minY == Integer.MIN_VALUE ? v.y0 : Math.max(v.y0, minY - baseY);
        if (exact) {
            int[] ij = local(x + 0.5, z + 0.5);
            if (ij == null) {
                return;
            }
            for (int y = from; y < v.y0 + v.h; y++) {
                int c = v.raw(ij[0], y, ij[1]);
                if (c != 0) {
                    sink.set(baseY + y, built.palette[c]);
                }
            }
            return;
        }
        // 비스듬히 놓인 건물: 칸 가운데와 네 귀퉁이 근처를 찍어서, 가운데가 비면 귀퉁이의 블록을 씀
        int[][] pts = new int[SAMPLES.length][];
        int n = 0;
        for (double[] s : SAMPLES) {
            int[] ij = local(x + 0.5 + s[0], z + 0.5 + s[1]);
            if (ij != null) {
                boolean dup = false;
                for (int k = 0; k < n; k++) {
                    dup |= pts[k][0] == ij[0] && pts[k][1] == ij[1];
                }
                if (!dup) {
                    pts[n++] = ij;
                }
            }
        }
        if (n == 0) {
            return;
        }
        int[] center = local(x + 0.5, z + 0.5);
        for (int y = from; y < v.y0 + v.h; y++) {
            int pick = center == null ? 0 : v.raw(center[0], y, center[1]);
            if (pick == 0 || built.palette[pick].isAir()) {
                for (int k = 0; k < n; k++) {
                    int c = v.raw(pts[k][0], y, pts[k][1]);
                    if (c != 0 && !built.palette[c].isAir()) {
                        pick = c;
                        break;
                    }
                }
            }
            if (pick != 0) {
                sink.set(baseY + y, built.palette[pick]);
            }
        }
    }

    /** 월드 점 → 건물 칸 {i, j} (상자 밖이면 null) */
    private int[] local(double wx, double wz) {
        double dx = wx - ox, dz = wz - oz;
        double a = dx * cos + dz * sin, b = -dx * sin + dz * cos;
        int i = (int) Math.floor(a), j = (int) Math.floor(b);
        if (i < 0 || j < 0 || i >= w || j >= d) {
            return null;
        }
        return new int[]{i, j};
    }

    @Override
    public String toString() {
        return name + " (" + kind + ") @ " + Math.round((bounds[0] + bounds[2]) / 2) + ", " + Math.round((bounds[1] + bounds[3]) / 2);
    }
}
