package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 단지·학교 검사 도구: 가짜 땅 지도, 랜드마크만 지은 실제 도시, 겹침·땅 밖·블록 이름·표지판 검사, 합친 그림 */
final class ComplexTestKit {
    /**
     * 가짜 땅 지도: (x, z) 가 land 이면 땅, 아니면 도로. 도로에서 setback 칸 넘게 떨어진 땅만 지을 수 있음
     * (BuildMask 는 지형으로만 만들 수 있어서 검사에서는 리플렉션으로 채움).
     */
    static BuildMask mask(int x0, int z0, int w, int h, int setback, BiPredicate<Integer, Integer> land) {
        try {
            Constructor<BuildMask> c = BuildMask.class.getDeclaredConstructor(int.class, int.class, int.class, int.class);
            c.setAccessible(true);
            BuildMask m = c.newInstance(x0, z0, w, h);
            boolean[] ok = (boolean[]) field("ok").get(m);
            short[] road = (short[]) field("road").get(m);
            boolean[] base = (boolean[]) field("base").get(m);
            ArrayDeque<Integer> q = new ArrayDeque<>();
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int k = j * w + i;
                    boolean l = land.test(x0 + i, z0 + j);
                    road[k] = l ? Short.MAX_VALUE : 0;
                    if (!l) {
                        q.add(k);
                    }
                }
            }
            while (!q.isEmpty()) {
                int k = q.poll();
                int i = k % w, j = k / w;
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] dd : dirs) {
                    int ni = i + dd[0], nj = j + dd[1];
                    if (ni < 0 || nj < 0 || ni >= w || nj >= h) {
                        continue;
                    }
                    int nk = nj * w + ni;
                    if (road[nk] > road[k] + 1) {
                        road[nk] = (short) (road[k] + 1);
                        q.add(nk);
                    }
                }
            }
            for (int k = 0; k < w * h; k++) {
                ok[k] = road[k] > setback && road[k] != Short.MAX_VALUE;
                base[k] = ok[k];
            }
            return m;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = BuildMask.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /** 실제 도시에서 랜드마크까지만 지은 배치 (동네 채우기 전, CityBuildings.plan 과 같은 순서) */
    static synchronized List<Placement> landmarks() {
        if (landmarks == null) {
            CityTerrain t = TestCity.terrain();
            List<Placement> all = new ArrayList<>();
            all.addAll(StreetPlan.paving(t));
            all.addAll(AirportPlan.plan(t));
            all.addAll(CheongnaPlan.plan(t));
            all.addAll(GuincheonPlan.plan(t));
            all.addAll(MarketPlan.plan(t));
            all.addAll(NamsanPlan.plan(t));
            all.addAll(PrisonPlan.plan(t));
            all.addAll(QuarryPlan.plan(t));
            all.addAll(YongsanPlan.plan(t));
            all.addAll(GangnamPlan.plan(t));
            all.addAll(HongdaePlan.plan(t));
            all.addAll(UniversityPlan.plan(t));
            all.addAll(GwangjinPlan.plan(t));
            all.addAll(YeouidoPlan.plan(t));
            all.addAll(JungguPlan.plan(t));
            landmarks = all;
        }
        return landmarks;
    }

    private static List<Placement> landmarks;

    /** 구역 땅 지도 (랜드마크 자리는 이미 씀) */
    static BuildMask districtMask(String id) {
        CityTerrain t = TestCity.terrain();
        BuildMask m = BuildMask.of(t, Plans.district(t, id), 2);
        GuincheonPlan.claimAll(m, landmarks().stream().filter(p -> !CityBuildings.isGround(p)).toList());
        return m;
    }

    /** 땅 위 낮은 곳(y 0..3)에 블록이 있는 칸들 */
    static Set<Long> lowCells(Placement p) {
        Set<Long> out = new HashSet<>();
        double[] b = p.bounds();
        for (int z = (int) Math.floor(b[1]); z <= (int) Math.ceil(b[3]); z++) {
            for (int x = (int) Math.floor(b[0]); x <= (int) Math.ceil(b[2]); x++) {
                boolean[] low = {false};
                p.column(x, z, 0, Integer.MIN_VALUE, (y, blk) -> {
                    if (!blk.isAir() && y >= 0 && y <= 3) {
                        low[0] = true;
                    }
                });
                if (low[0]) {
                    out.add(key(x, z));
                }
            }
        }
        return out;
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /**
     * 배치 검사: 서로 낮은 곳에서 겹치지 않음, 땅 위 블록(y ≥ 0)은 원래 지을 수 있던 칸에만,
     * 땅 높이(y -1) 이음도 도로 위에는 없음 (생성기가 어차피 안 놓지만), 블록 이름이 26.2 에 있음, 표지판 글씨가 들어감.
     */
    static void assertSound(String what, List<Placement> ps, BuildMask before, CityTerrain terrain) {
        Map<Long, String> owner = new HashMap<>();
        List<String> bad = new ArrayList<>();
        for (Placement p : ps) {
            for (long k : lowCells(p)) {
                String prev = owner.put(k, p.name);
                if (prev != null && !prev.equals(p.name)) {
                    bad.add("겹침 " + prev + " ↔ " + p.name);
                }
                int x = (int) (k >> 32), z = (int) k;
                if (!before.free(x, z)) {
                    bad.add("땅 밖 " + p.name + " (" + x + ", " + z + ")");
                }
            }
            if (terrain != null) {
                double[] b = p.bounds();
                for (int z = (int) Math.floor(b[1]); z <= (int) Math.ceil(b[3]); z++) {
                    for (int x = (int) Math.floor(b[0]); x <= (int) Math.ceil(b[2]); x++) {
                        Column c = terrain.column(x, z);
                        if (!(c.isRoad() || c.isWater() || c.deck)) {
                            continue;
                        }
                        final int fx = x, fz = z;
                        p.column(x, z, 0, Integer.MIN_VALUE, (y, blk) -> {
                            if (!blk.isAir() && y >= 0 && y < CityBuildings.ROAD_CLEARANCE) {
                                bad.add("도로 위 " + p.name + " (" + fx + ", " + fz + ")");
                            }
                        });
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), what + ": " + bad.stream().distinct().limit(12).toList());
        assertBlocksAndSigns(what, ps);
    }

    private static Set<String> known;

    static synchronized Set<String> knownBlocks() {
        if (known == null) {
            Set<String> k = new HashSet<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    ComplexTestKit.class.getResourceAsStream("/block-ids-26.2.txt"), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.isBlank() && !line.startsWith("#")) {
                        k.add("minecraft:" + line.trim());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            known = k;
        }
        return known;
    }

    /** 블록 이름이 26.2 에 있고 블록 엔티티가 필요한 블록이 없고, 표지판 글씨가 한 줄 90픽셀 안 */
    static void assertBlocksAndSigns(String what, List<Placement> ps) {
        Set<String> bad = new java.util.TreeSet<>();
        Set<String> forbidden = Set.of("chest", "bed", "banner", "shulker_box", "decorated_pot", "skull", "head", "bell",
                "conduit", "beacon", "enchanting_table", "lectern", "chiseled_bookshelf", "jukebox", "spawner", "brewing_stand");
        for (Placement p : ps) {
            Voxels v = p.voxels();
            for (Block b : v.palette()) {
                String id = b.id();
                if (!knownBlocks().contains(id)) {
                    bad.add("없는 블록 " + id + " (" + p.name + ")");
                }
                String n = id.substring(id.indexOf(':') + 1);
                for (String f : forbidden) {
                    if (n.equals(f) || n.endsWith("_" + f)) {
                        bad.add("블록 엔티티 " + id + " (" + p.name + ")");
                    }
                }
                for (String line : b.textLines()) {
                    int px = 0;
                    for (char ch : line.toCharArray()) {
                        px += ch >= 0xAC00 && ch <= 0xD7A3 ? 9 : 6;
                    }
                    if (px > 90) {
                        bad.add("긴 글씨 " + p.name + ": " + line);
                    }
                }
                if (b.textLines().length > 4) {
                    bad.add("표지판 줄 " + p.name);
                }
            }
        }
        assertTrue(bad.isEmpty(), what + ": " + bad);
    }

    /** 배치들을 한 상자에 합침 (월드 좌표 상자, y 범위) */
    static Voxels composite(List<Placement> ps, int x0, int z0, int x1, int z1, int yMin, int yMax) {
        Voxels v = new Voxels(x1 - x0 + 1, z1 - z0 + 1, yMin, yMax);
        for (Placement p : ps) {
            double[] b = p.bounds();
            for (int z = Math.max(z0, (int) Math.floor(b[1])); z <= Math.min(z1, (int) Math.ceil(b[3])); z++) {
                for (int x = Math.max(x0, (int) Math.floor(b[0])); x <= Math.min(x1, (int) Math.ceil(b[2])); x++) {
                    final int i = x - x0, j = z - z0;
                    p.column(x, z, 0, Integer.MIN_VALUE, (y, blk) -> v.set(i, y, j, blk));
                }
            }
        }
        return v;
    }

    private ComplexTestKit() {
    }
}
