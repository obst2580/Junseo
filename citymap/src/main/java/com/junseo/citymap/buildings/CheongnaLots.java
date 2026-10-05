package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 청라 신도시 분양 필지 (부동산 회사가 플레이어에게 파는 땅, 집 짓기용).
 * 빈 풀밭으로 두지 않고 실제 신도시 택지처럼: 블록을 길가 필지와 안쪽 필지로 나누고, 필지마다 경계석과
 * 모서리 말뚝, 길 쪽에 번호 안내판(「청라 A-12 / 분양 중」)을 둡니다. 안쪽 필지로 들어가는 단지 길은 포장.
 * 거점(쇼핑몰) 가까이에 분양 홍보관 하나.
 * <p>
 * 필지 이름은 "청라 필지 A-12" (kind "lot"): 블록 글자 + 블록 안 번호. 플러그인 분양 기능이 이 이름으로 땅을 찾습니다.
 */
final class CheongnaLots {
    private static final Block CURB = Block.of("stone_bricks", 0x7A7979);
    private static final Block PEG = Block.of("andesite_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x888888);
    private static final Block BOARD = WHITE_CONCRETE;

    static List<Placement> plan(CityTerrain t, List<Placement> existing) {
        Polygon area = Plans.district(t, "cheongna");
        if (area == null) {
            return List.of();
        }
        BuildMask m = BuildMask.of(t, area, 2);
        GuincheonPlan.claimAll(m, existing.stream().filter(p -> !CityBuildings.isGround(p)).toList());
        Random rnd = Plans.random(t, "cheongna-lots");
        MainRoads roads = MainRoads.of(t);
        List<Placement> out = new ArrayList<>();
        BlockLayout.Spec spec = new BlockLayout.Spec();
        spec.front = 24;
        spec.lotMin = 18;
        spec.lotMax = 24;
        spec.innerDepthMin = 20;
        spec.innerDepthMax = 24;
        spec.innerMin = 18;
        spec.innerMax = 24;
        spec.innerGap = 0;
        spec.alley = 6;
        // 분양 홍보관: 쇼핑몰 거점에서 가까운 블록 길가
        boolean office = false;
        char letter = 'A';
        for (int[] block : m.blocks()) {
            if (block[4] < 400) {
                continue;
            }
            BlockLayout.Plan plan = BlockLayout.plan(m, t, roads, block, spec, rnd);
            if (plan.lots().isEmpty()) {
                continue;
            }
            int no = 1;
            for (BlockLayout.Lot l : plan.lots()) {
                if (!m.rectFree(l.x0(), l.z0(), l.x1(), l.z1())) {
                    continue;
                }
                String code = letter + "-" + no++;
                long seed = rnd.nextLong();
                if (!office && l.width() >= 18 && l.tier() != BlockLayout.Tier.INNER) {
                    office = true;
                    out.add(Placement.rect("청라 분양 홍보관", "sales", l.x0(), l.z0(), l.x1(), l.z1(), l.front(),
                            (w, d) -> salesOffice(w, d, new Random(seed))));
                } else {
                    out.add(Placement.rect("청라 필지 " + code, "lot", l.x0(), l.z0(), l.x1(), l.z1(), l.front(),
                            (w, d) -> lot(w, d, code)));
                }
                m.claim(l.x0(), l.z0(), l.x1(), l.z1());
            }
            for (int[] a : plan.alleys()) {
                out.add(Placement.rect("단지 길", "plaza", a[0], a[1], a[2], a[3], "south", CheongnaLots::lane));
                m.claim(a[0], a[1], a[2], a[3]);
            }
            letter = letter == 'Z' ? 'A' : (char) (letter + 1);
        }
        return out;
    }

    /** 필지 하나: 둘레 경계석, 네 모서리 말뚝, 정면 가운데 번호 안내판 (안은 풀밭 그대로) */
    static Voxels lot(int w, int d, String code) {
        Voxels v = new Voxels(w, d, -1, 2);
        v.walls(0, -1, 0, w - 1, -1, d - 1, CURB);
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
            v.set(c[0], 0, c[1], PEG);
        }
        // 안내판: 말뚝 둘 사이 흰 판, 길 쪽(정면)에 표지판
        int mid = w / 2;
        v.set(mid - 1, 0, d - 2, PEG);
        v.set(mid + 1, 0, d - 2, PEG);
        v.fill(mid - 1, 1, d - 2, mid + 1, 1, d - 2, BOARD);
        v.set(mid, 1, d - 1, wallSign("birch", "south", "black", false, "청라 " + code, "분양 중", w + "×" + d + "m", "청라부동산"));
        return v;
    }

    /** 단지 길: 아스팔트, 가운데 점선 */
    static Voxels lane(int w, int d) {
        Voxels v = new Voxels(w, d, -1, 0);
        v.fill(0, -1, 0, w - 1, -1, d - 1, Block.of("gray_concrete", 0x36393D));
        boolean alongX = w > d;
        int mid = alongX ? d / 2 : w / 2;
        int len = alongX ? w : d;
        for (int k = 1; k < len - 1; k += 4) {
            if (alongX) {
                v.fill(k, -1, mid, Math.min(w - 2, k + 1), -1, mid, WHITE_CONCRETE);
            } else {
                v.fill(mid, -1, k, mid, -1, Math.min(d - 2, k + 1), WHITE_CONCRETE);
            }
        }
        return v;
    }

    /** 분양 홍보관: 단층 유리 건물, 안에 상담 탁자·안내 데스크·필지 배치 모형 탁자, 앞 잔디와 깃대 */
    static Voxels salesOffice(int w, int d, Random r) {
        Voxels v = new Voxels(w, d, -1, 8);
        int i0 = 2, i1 = w - 3, j0 = 2, j1 = d - 6;
        v.fill(0, -1, 0, w - 1, -1, d - 1, LIGHT_GRAY_CONCRETE);
        v.fill(i0, -1, j0, i1, -1, j1, POLISHED_DIORITE);
        v.walls(i0, 0, j0, i1, 4, j1, WHITE_CONCRETE);
        v.fill(i0 + 1, 0, j0 + 1, i1 - 1, 4, j1 - 1, AIR);
        v.fill(i0, 5, j0, i1, 5, j1, SMOOTH_STONE);
        // 정면 통유리, 가운데 문
        v.fill(i0 + 1, 0, j1, i1 - 1, 3, j1, GLASS_PANE);
        int dx = (i0 + i1) / 2;
        v.fill(dx - 1, 0, j1, dx + 1, 1, j1, AIR);
        v.set(dx, 0, j1, Block.of("iron_door[facing=north,half=lower,hinge=left,open=false,powered=false]", 0xC0C0C0));
        v.set(dx, 1, j1, Block.of("iron_door[facing=north,half=upper,hinge=left,open=false,powered=false]", 0xC0C0C0));
        // 안: 안내 데스크(뒤), 가운데 필지 배치 모형 탁자, 양옆 상담 탁자
        v.fill(dx - 3, 0, j0 + 2, dx + 3, 0, j0 + 2, Block.of("smooth_quartz", 0xEBE5DE));
        v.fill(dx - 3, 0, (j0 + j1) / 2 - 1, dx + 3, 0, (j0 + j1) / 2 + 1, Block.of("white_wool", 0xE9ECEC));
        v.fill(dx - 2, 1, (j0 + j1) / 2 - 1, dx + 2, 1, (j0 + j1) / 2 + 1, Block.of("moss_carpet", 0x596D2F));
        for (int i = i0 + 3; i < i1 - 2; i += 5) {
            if (Math.abs(i - dx) <= 5) {
                continue;
            }
            v.set(i, 0, j1 - 3, Block.of("birch_planks", 0xC5B57C));
            v.set(i, 0, j1 - 2, Block.of("birch_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xC5B57C));
            v.set(i, 0, j1 - 4, Block.of("birch_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", 0xC5B57C));
        }
        for (int i = i0 + 3; i < i1; i += 4) {
            v.set(i, 4, (j0 + j1) / 2, Interior.LIGHT);
        }
        // 간판 띠와 표지판
        v.fill(i0, 5, j1 + 1, i1, 5, j1 + 1, Block.of("blue_concrete", 0x2C2E8F));
        v.set(dx - 3, 4, j1 + 1, wallSign("spruce", "south", "white", true, "청라 신도시", "분양 홍보관"));
        v.set(dx + 3, 4, j1 + 1, wallSign("spruce", "south", "white", true, "필지 분양 상담", "청라부동산"));
        // 앞 잔디와 깃대
        for (int i = 1; i < w - 1; i++) {
            v.set(i, -1, d - 3, Block.of("grass_block", 0x7CB25A));
        }
        for (int i = 3; i < w - 3; i += 6) {
            v.fill(i, 0, d - 4, i, 6, d - 4, Block.of("iron_bars", 0x888888));
        }
        return v;
    }

    private CheongnaLots() {
    }
}
