package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 한국 거리의 바탕: 동네 빈 땅 포장(인도와 이어지는 보도, 골목 아스팔트)과 인도 위 시설
 * (가로수와 나무 구덩이, 가로등, 전봇대와 전깃줄, 버스 정류장).
 * 시설은 인도 연석 쪽 한 줄에만 놓아서 걷는 길과 차도를 막지 않습니다.
 */
final class StreetPlan {
    /** 포장하는 구역 (빽빽한 동네) */
    private static final String[] PAVED = {"guincheon", "market", "yeouido", "junggu", "yongsan", "gangnam", "songpa", "mapo",
            "gwangjin", "hongdae", "university"};
    /** 거리 시설을 놓는 구역 */
    private static final String[] FURNISHED = {"guincheon", "market", "cheongna", "airport", "yeouido", "junggu", "yongsan",
            "gangnam", "songpa", "mapo", "gwangjin", "hongdae", "university", "namsan", "bukhansan"};
    /** 차도 가장자리에서 인도 쪽으로 이만큼 (연석 쪽 칸) */
    private static final double CURB = 1.0;

    static final Block POST = Block.of("andesite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x888888);
    static final Block LAMP = Block.of("end_rod[facing=down]", 0xE8E2D8);
    static final Block TOP_SLAB = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);

    /** 동네 빈 땅 포장 (건물보다 먼저 깔고, 건물이 자기 바닥으로 덮음) */
    static List<Placement> paving(CityTerrain t) {
        List<Placement> out = new ArrayList<>();
        for (String id : PAVED) {
            Polygon area = Plans.district(t, id);
            if (area == null) {
                continue;
            }
            double[] b = area.bounds();
            int x0 = (int) Math.floor(b[0]), z0 = (int) Math.floor(b[1]), x1 = (int) Math.ceil(b[2]), z1 = (int) Math.ceil(b[3]);
            out.add(Placement.rect("동네 포장", "pave", x0, z0, x1, z1, "south", (w, d) -> pave(t, area, x0, z0, w, d)));
        }
        return out;
    }

    private static Voxels pave(CityTerrain t, Polygon area, int x0, int z0, int w, int d) {
        Voxels v = new Voxels(w, d, -1, -1);
        BuildMask m = BuildMask.of(t, area, 0);
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                int x = x0 + i, z = z0 + j;
                if (!area.contains(x + 0.5, z + 0.5)) {
                    continue;
                }
                Column c = t.column(x, z);
                if (c.surface != Surface.GRASS || c.mountainHeight > 0 || c.isWater()) {
                    continue;
                }
                int dist = m.roadDistance(x, z);
                // 3×3 칸 단위로 가끔 덧씌운 자국 (블록마다 바꾸면 얼룩덜룩해 보임)
                long h = (Math.floorDiv(x, 3) * 73856093L) ^ (Math.floorDiv(z, 3) * 19349663L);
                Block b;
                if (dist <= 3) {
                    b = LIGHT_GRAY_CONCRETE; // 인도와 이어지는 보도
                } else {
                    b = Math.floorMod(h, 9) == 0 ? ANDESITE : GRAY_CONCRETE; // 골목 아스팔트
                }
                v.set(i, -1, j, b);
            }
        }
        return v;
    }

    /**
     * 건물을 지은 구역의 거점 표시(석영 바닥과 빨간 기둥)를 걷어 내고 보도로 덮습니다.
     * 거점 좌표는 그대로라서 게임(장소·NPC·길 안내)에는 영향이 없습니다.
     */
    static List<Placement> hubCovers(CityTerrain t) {
        List<Placement> out = new ArrayList<>();
        for (Layout.Hub h : t.layout().hubs()) {
            int x = (int) Math.floor(h.x()), z = (int) Math.floor(h.z());
            Column c = t.column(x, z);
            int ground = c.mountainHeight - 1; // 산 위 거점은 산 높이에
            int pillar = Math.max(3, c.hubPillar + 1);
            out.add(Placement.rect("거점 표시 걷기", "hubcover", x - 2, z - 2, x + 2, z + 2, "south", (w, d) -> {
                Voxels v = new Voxels(w, d, ground, ground + 1 + pillar);
                v.fill(0, ground, 0, w - 1, ground, d - 1, LIGHT_GRAY_CONCRETE);
                v.fill(0, ground + 1, 0, w - 1, ground + 3, d - 1, AIR);
                v.fill(w / 2, ground + 1, d / 2, w / 2, ground + pillar, d / 2, AIR); // 거점 기둥
                return v;
            }));
        }
        return out;
    }

    /** 인도 시설 */
    static List<Placement> furniture(CityTerrain t) {
        List<Polygon> areas = new ArrayList<>();
        for (String id : FURNISHED) {
            Polygon p = Plans.district(t, id);
            if (p != null) {
                areas.add(p);
            }
        }
        Random rnd = Plans.random(t, "street");
        List<Placement> out = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Layout.Road road : t.layout().roads()) {
            if (!road.kind().equals("arterial") && !road.kind().equals("street")) {
                continue;
            }
            double offset = CityTerrain.halfWidth(road.kind()) - 4 + CURB; // 차도 끝 + 1
            List<double[]> line = road.line();
            double along = 0;
            for (int k = 0; k + 1 < line.size(); k++) {
                double[] a = line.get(k), b = line.get(k + 1);
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
                double ux = (b[0] - a[0]) / len, uz = (b[1] - a[1]) / len;
                boolean axis = Math.abs(ux) < 1e-6 || Math.abs(uz) < 1e-6;
                double[] lastPole = {Double.NaN, Double.NaN};
                for (double s = 4; s < len - 4; s += 1) {
                    long step = Math.round(along + s);
                    for (int side : new int[]{-1, 1}) {
                        // side 의 법선 (인도 쪽)
                        double nx = -uz * side, nz = ux * side;
                        double px = a[0] + ux * s + nx * offset, pz = a[1] + uz * s + nz * offset;
                        int cx = (int) Math.floor(px), cz = (int) Math.floor(pz);
                        if (!inAny(areas, px, pz) || !sidewalkRun(t, px, pz, ux, uz)) {
                            continue;
                        }
                        // 차도 쪽(가로등 팔이 향하는 쪽) 각도: 정면 +j 가 -n
                        double ang = Math.toDegrees(Math.atan2(nx, -nz));
                        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                        String kind = null;
                        if (step % 36 == 0) {
                            kind = "lamp";
                        } else if (side < 0 && step % 36 == 18) {
                            kind = "pole";
                        } else if (step % 12 == 6) {
                            kind = "tree";
                        }
                        if (kind == null || near(used, cx, cz, kind.equals("tree") ? 3 : 2)) {
                            continue;
                        }
                        used.add(key);
                        long seed = rnd.nextLong();
                        switch (kind) {
                            case "lamp" -> out.add(Placement.rotated("가로등", "street", px, pz, ang, 1, 4, 0.5, 0.5, null, StreetPlan::lamp));
                            case "pole" -> {
                                out.add(Placement.rotated("전봇대", "street", px, pz, ang, 1, 3, 0.5, 1.5, null, () -> pole(seed)));
                                if (axis && !Double.isNaN(lastPole[0]) && Math.hypot(px - lastPole[0], pz - lastPole[1]) < 45) {
                                    out.add(wire(lastPole[0], lastPole[1], px, pz));
                                }
                                lastPole[0] = px;
                                lastPole[1] = pz;
                            }
                            default -> out.add(Placement.rotated("가로수", "street", px, pz, ang, 5, 5, 2.5, 2.5, null, () -> tree(seed)));
                        }
                    }
                }
                // 버스 정류장: 긴 구간 가운데 한쪽
                if (len > 120) {
                    double s = len / 2;
                    int side = rnd.nextBoolean() ? 1 : -1;
                    double nx = -uz * side, nz = ux * side;
                    double px = a[0] + ux * s + nx * (offset + 1.5), pz = a[1] + uz * s + nz * (offset + 1.5);
                    if (inAny(areas, px, pz) && sidewalkRun(t, px, pz, ux, uz) && !near(used, (int) Math.floor(px), (int) Math.floor(pz), 5)) {
                        double ang = Math.toDegrees(Math.atan2(nx, -nz));
                        String stop = road.name() == null ? "정류장" : road.name().split("[·,]")[0];
                        out.add(Placement.rotated("버스 정류장", "street", px, pz, ang, 7, 2, 3.5, 1.0, null, () -> busStop(stop)));
                        used.add(((long) (int) Math.floor(px) << 32) ^ ((int) Math.floor(pz) & 0xffffffffL));
                    }
                }
                along += len;
            }
        }
        return out;
    }

    private static boolean inAny(List<Polygon> areas, double x, double z) {
        for (Polygon p : areas) {
            if (p.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /** 그 점과 앞뒤 3칸이 모두 인도인지 (교차로·다리 위는 뺌) */
    private static boolean sidewalkRun(CityTerrain t, double x, double z, double ux, double uz) {
        for (int k = -3; k <= 3; k++) {
            Column c = t.column((int) Math.floor(x + ux * k), (int) Math.floor(z + uz * k));
            if (c.surface != Surface.SIDEWALK || c.deck) {
                return false;
            }
        }
        return true;
    }

    private static boolean near(Set<Long> used, int x, int z, int r) {
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (used.contains(((long) (x + dx) << 32) ^ ((z + dz) & 0xffffffffL))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 시설 모양 (정면 +j = 차도 쪽)

    /** 가로수: 나무 구덩이, 줄기, 둥근 잎 (참나무·자작나무 잎) */
    private static Voxels tree(long seed) {
        Random r = new Random(seed);
        Voxels v = new Voxels(5, 5, -1, 9);
        v.set(2, -1, 2, r.nextBoolean() ? COARSE_DIRT : GRASS);
        int h = 3 + r.nextInt(2);
        v.fill(2, 0, 2, 2, h, 2, r.nextInt(3) == 0 ? Block.of("birch_log[axis=y]", 0xD8D3C5) : OAK_LOG);
        Block leaves = r.nextInt(3) == 0 ? Block.of("birch_leaves[persistent=true]", 0x6B8F3F) : OAK_LEAVES;
        v.ellipsoid(2.5, h + 2.2, 2.5, 2.3, 2.0, 2.3, leaves);
        v.set(2, h + 1, 2, OAK_LOG);
        return v;
    }

    /** 가로등: 회색 기둥, 차도 쪽으로 뻗은 팔, 아래를 비추는 등 */
    private static Voxels lamp() {
        Voxels v = new Voxels(1, 4, -1, 8);
        v.fill(0, 0, 0, 0, 7, 0, POST);
        v.fill(0, 8, 0, 0, 8, 3, TOP_SLAB);
        v.set(0, 7, 3, LAMP);
        return v;
    }

    /** 전봇대: 높은 기둥, 전깃줄과 직각인 위 가로대, 가끔 변압기 (기둥은 가운데 칸) */
    private static Voxels pole(long seed) {
        Random r = new Random(seed);
        Voxels v = new Voxels(1, 3, -1, 11);
        v.fill(0, 0, 1, 0, 10, 1, POST);
        v.fill(0, 9, 0, 0, 9, 2, TOP_SLAB);
        if (r.nextInt(3) == 0) {
            v.set(0, 7, 0, LIGHT_GRAY_CONCRETE); // 변압기 (인도 쪽)
        }
        return v;
    }

    /** 두 전봇대 사이 전깃줄 (가로·세로 길만): 두 가닥 */
    private static Placement wire(double ax, double az, double bx, double bz) {
        int x0 = (int) Math.floor(Math.min(ax, bx)), x1 = (int) Math.floor(Math.max(ax, bx));
        int z0 = (int) Math.floor(Math.min(az, bz)), z1 = (int) Math.floor(Math.max(az, bz));
        boolean alongX = x1 - x0 >= z1 - z0;
        return Placement.rect("전깃줄", "street", alongX ? x0 + 1 : x0, alongX ? z0 : z0 + 1, alongX ? x1 - 1 : x1, alongX ? z1 : z1 - 1, "south",
                (w, d) -> {
                    Voxels v = new Voxels(w, d, 8, 9);
                    Block chain = Block.of("iron_chain[axis=" + (alongX ? "x" : "z") + ",waterlogged=false]", 0x404040);
                    v.fill(0, 8, 0, w - 1, 9, d - 1, chain);
                    return v;
                });
    }

    /** 버스 정류장: 유리 벽, 지붕, 긴 의자, 정류장 이름 표지판 */
    private static Voxels busStop(String name) {
        Voxels v = new Voxels(7, 2, -1, 4);
        v.fill(0, 3, 0, 6, 3, 1, TOP_SLAB);
        v.fill(0, 0, 0, 0, 2, 0, POST);
        v.fill(6, 0, 0, 6, 2, 0, POST);
        for (int i = 1; i <= 5; i++) {
            v.fill(i, 0, 0, i, 2, 0, GLASS_PANE);
        }
        v.fill(1, 0, 1, 5, 0, 1, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        v.set(3, 2, 1, Blocks.hangingSign("dark_oak", 0, "white", true, "버스", name, "정류장"));
        return v;
    }

    private StreetPlan() {
    }
}
