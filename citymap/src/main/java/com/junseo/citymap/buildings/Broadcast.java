package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;

import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 방송국 「준서방송」: 거점 쪽(서쪽) 앞마당을 보는 본관과 그 남쪽 스튜디오동.
 * <ul>
 *   <li>본관(16층, 흰 띠와 푸른 유리): 1층 높은 로비, 2층 보도국(뉴스룸: 기자석 줄, 모니터 벽, 편집 데스크),
 *       나머지 사무실, 옥상 헬기장(취재 헬기)과 빨강·흰 송신탑, 중계용 접시 안테나</li>
 *   <li>스튜디오동(창 없는 높은 덩어리): A 스튜디오(뉴스 세트: 앵커 데스크, 큰 화면 뒷벽, 카메라),
 *       B 스튜디오(예능·드라마 세트: 거실 세트, 무대, 방청석). 천장 조명 격자. 앞쪽 3개 층에 로비,
 *       부조정실(스튜디오를 내려다보는 창, 콘솔, 모니터 벽), 분장실·출연자 대기실, 옥상 위성 접시</li>
 *   <li>앞마당: 거점, 표석, 화단, 보행등, 국기 게양대</li>
 * </ul>
 */
final class Broadcast {
    static final int TOWER_W = 46, TOWER_D = 30, TOWER_FLOORS = 16;
    static final int STUDIO_W = 36, STUDIO_D = 38;
    /** 스튜디오동 앞쪽 층들 (서는 높이) */
    static final int[] STUDIO_LEVELS = {0, 5, 10, 15};

    static void plan(CityTerrain t, Polygon area, BuildMask m, Layout.Hub hub, List<Placement> out) {
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        Random rnd = Plans.random(t, "준서방송");
        // 본관: 거점 동쪽, 정면 서쪽
        int[] tw = find(m, hx + 16, hz - 12, TOWER_D, TOWER_W);
        if (tw == null) {
            return;
        }
        long seed = rnd.nextLong();
        out.add(Placement.rect("준서방송 본관", "studio", tw[0], tw[1], tw[0] + TOWER_D - 1, tw[1] + TOWER_W - 1, "west",
                (w, d) -> tower(w, d, new Random(seed))));
        // 스튜디오동: 본관 남쪽
        int[] sw = find(m, tw[0], tw[1] + TOWER_W + 4, STUDIO_D, STUDIO_W);
        long seed2 = rnd.nextLong();
        if (sw != null) {
            out.add(Placement.rect("준서방송 스튜디오", "studio", sw[0], sw[1], sw[0] + STUDIO_D - 1, sw[1] + STUDIO_W - 1, "west",
                    (w, d) -> studio(w, d, new Random(seed2))));
        }
        // 동쪽 직원·중계차 주차장 (주차선만)
        int px0 = tw[0] + TOWER_D + 3, px1 = px0 + 27, pz0 = tw[1], pz1 = sw != null ? sw[1] + STUDIO_W - 1 : tw[1] + TOWER_W - 1;
        if (m.count(px0, pz0, px1, pz1) > (px1 - px0 + 1) * (pz1 - pz0 + 1) / 3) {
            out.add(Placement.rect("준서방송 주차장", "parking", px0, pz0, px1, pz1, "south", (w, d) -> {
                Site s = new Site(t, area, px0, pz0, px1, pz1);
                return parking(s);
            }));
        }
        // 앞마당: 본관·스튜디오 서쪽 줄 전체
        int[] blk = m.componentNear(hx, hz, 30);
        int fx0 = (blk == null ? hx - 12 : blk[0]) - 3, fx1 = tw[0] - 1;
        int fz0 = hz - 18, fz1 = sw != null ? sw[1] + STUDIO_W + 2 : tw[1] + TOWER_W + 2;
        out.add(Placement.rect("준서방송 앞마당", "plaza", fx0, fz0, fx1, fz1, "south", (w, d) -> {
            Site s = new Site(t, area, fx0, fz0, fx1, fz1);
            return forecourt(s, hx - fx0, hz - fz0, tw[1] - fz0, sw == null ? -1 : sw[1] - fz0);
        }));
    }

    /** (x0, z0) 근처(동쪽·남쪽으로 조금씩 밀며)에서 sx × sz 땅 찾기 */
    private static int[] find(BuildMask m, int x0, int z0, int sx, int sz) {
        for (int dx = 0; dx <= 8; dx++) {
            for (int dz = 0; dz <= 8; dz++) {
                if (m.rectFree(x0 + dx, z0 + dz, x0 + dx + sx - 1, z0 + dz + sz - 1)) {
                    return new int[]{x0 + dx, z0 + dz};
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 본관

    static Voxels tower(int w, int d, Random r) {
        Tower.Spec s = new Tower.Spec(w, d, TOWER_FLOORS);
        s.lobbyH = Floors.HALL;
        s.glass = Block.of("light_blue_stained_glass", 0x6699D8);
        s.mullion = WHITE_CONCRETE;
        s.spandrel = WHITE_CONCRETE;
        s.podium = POLISHED_ANDESITE;
        s.elevators = 2;
        s.restrooms = 2;
        s.helipad = true;
        s.extraTop = 36;
        s.name = "준서방송";
        s.directory = new String[]{"준서방송 본관", "1층 로비·안내", "2층 보도국", "3~16층 사무실"};
        s.use = k -> k == 0 ? Tower.Use.LOBBY : k == 1 ? Tower.Use.CUSTOM : Tower.Use.OFFICE;
        s.custom = (t, k, level, h) -> newsroom(t, level, h);
        Tower t = Tower.build(s, r);
        Voxels v = t.v;
        // 송신탑 (코어 기계실 위): 빨강·흰 격자 탑, 중계 접시, 항공 장애등
        int ci = (t.ci0 + t.ci1) / 2, cj = (t.cj0 + t.cj1) / 2, base = t.roofLevel + 4;
        int top = Math.min(v.y0 + v.h - 2, base + 24);
        for (int y = base; y <= top; y++) {
            Block b = ((y - base) / 4) % 2 == 0 ? RED_CONCRETE : WHITE_CONCRETE;
            for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                v.set(ci + c[0], y, cj + c[1], b);
            }
            if ((y - base) % 4 == 0) {
                v.fill(ci - 1, y, cj - 1, ci + 1, y, cj + 1, IRON_BARS);
                for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                    v.set(ci + c[0], y, cj + c[1], b);
                }
            }
        }
        v.fill(ci, top + 1, cj, ci, top + 4, cj, IRON_BARS);
        v.set(ci, top + 5, cj, SHROOMLIGHT);
        dish(v, ci + 2, base + 10, cj, 1, "east");
        dish(v, ci - 2, base + 14, cj, 1, "west");
        v.connect();
        return v;
    }

    /** 2층 보도국: 기자석 줄, 뒤쪽 모니터 벽, 가운데 편집 데스크 */
    private static void newsroom(Tower t, int level, int h) {
        Voxels v = t.v;
        Frame f = Frame.of(v);
        Random r = new Random(t.r.nextLong());
        int k = 1;
        int[] b = t.bounds(k);
        Rooms.office(f, r, b[0], b[1], b[2], b[3], level, h, (i, j) -> t.free(i, j, k) && j > t.corridorJ + 4);
        // 코어 뒤 모니터 벽 (뒷벽을 따라)
        for (int i = b[0] + 2; i <= b[2] - 2; i++) {
            if (t.inside(i, b[1] + 1, k) && i % 7 != 0) {
                v.fill(i, level + 1, b[1] + 1, i, level + 2, b[1] + 1, BLACK_CONCRETE);
            }
        }
        // 편집 데스크 (코어 뒤 가운데, 긴 탁자)
        int mid = (b[0] + b[2]) / 2;
        for (int i = mid - 6; i <= mid + 6; i++) {
            if (t.free(i, b[1] + 4, k)) {
                v.set(i, level, b[1] + 4, Furniture.WHITE_TOP);
                if (i % 2 == 0) {
                    Furniture.chair(f, i, level, b[1] + 5, "south", "dark_oak");
                    v.set(i, level + 1, b[1] + 4, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
                }
            }
        }
        v.set(t.ci0 + 6, level + 1, t.corridorJ, Blocks.wallSign("dark_oak", "south", "white", true, "", "보도국", "뉴스룸"));
    }

    /**
     * 접시 안테나: 중심 (ci, cy, cj), 반지름 rr, 접시가 face 쪽(수평)과 위로 45도를 봄. 받침 기둥과 수신기 팔.
     */
    static void dish(Voxels v, int ci, int cy, int cj, int rr, String face) {
        int[] d = Furniture.step(face);
        double nx = d[0] * 0.707, ny = 0.707, nz = d[1] * 0.707;
        double R = rr + 0.6;
        for (int y = cy - rr - 1; y <= cy + rr + 1; y++) {
            for (int j = cj - rr - 1; j <= cj + rr + 1; j++) {
                for (int i = ci - rr - 1; i <= ci + rr + 1; i++) {
                    double px = i - ci, py = y - cy, pz = j - cj;
                    double dot = px * nx + py * ny + pz * nz;
                    double qx = px - dot * nx, qy = py - dot * ny, qz = pz - dot * nz;
                    if (Math.abs(dot) < 0.55 && qx * qx + qy * qy + qz * qz <= R * R) {
                        v.set(i, y, j, WHITE_CONCRETE);
                    }
                }
            }
        }
        v.set(ci + d[0], cy + 1, cj + d[1], IRON_BARS);
        v.set(ci + 2 * d[0], cy + 2, cj + 2 * d[1], Block.of("light_gray_concrete", 0x7D7D73));
    }

    // ------------------------------------------------------------------ 스튜디오동

    /**
     * 스튜디오동 (정면 남쪽 j = d-1). 뒤쪽은 A·B 두 스튜디오(높이 14), 앞쪽 띠는 3개 층과 옥상.
     */
    static Voxels studio(int w, int d, Random r) {
        int[] lv = STUDIO_LEVELS;
        int roof = lv[lv.length - 1];
        Voxels v = new Voxels(w, d, -1, roof + 8);
        Frame f = Frame.of(v);
        Block panel = Block.of("light_gray_concrete", 0x7D7D73);
        Block band = WHITE_CONCRETE;
        int sw = 27;                 // 스튜디오와 앞쪽 띠 사이 벽 줄
        int mid = w / 2;
        // 바닥, 겉벽, 지붕
        v.fill(0, -1, 0, w - 1, -1, d - 1, POLISHED_ANDESITE);
        v.walls(0, 0, 0, w - 1, roof - 1, d - 1, panel);
        for (int y = 4; y < roof; y += 5) {
            v.walls(0, y, 0, w - 1, y, d - 1, band);
        }
        v.fill(0, roof - 1, 0, w - 1, roof - 1, d - 1, SMOOTH_STONE);
        v.walls(0, roof, 0, w - 1, roof, d - 1, panel);
        v.walls(0, roof + 1, 0, w - 1, roof + 1, d - 1, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        // 스튜디오 두 칸 (안쪽 비움)과 사이 벽
        v.fill(1, 0, 1, w - 2, roof - 2, sw - 1, AIR);
        v.fill(mid, 0, 1, mid, roof - 2, sw - 1, Block.of("gray_concrete", 0x36393D));
        v.fill(1, 0, sw, w - 2, roof - 2, sw, Block.of("gray_concrete", 0x36393D));
        Interior.floor(f, 1, 1, w - 2, sw - 1, 0, Block.of("gray_concrete", 0x36393D));
        // 앞쪽 띠: 층 바닥과 창
        v.fill(1, 0, sw + 1, w - 2, roof - 2, d - 2, AIR);
        for (int k = 1; k < lv.length - 1; k++) {
            v.fill(1, lv[k] - 1, sw + 1, w - 2, lv[k] - 1, d - 2, SMOOTH_STONE);
        }
        for (int k = 0; k < lv.length - 1; k++) {
            for (int i = 1; i < w - 1; i++) {
                if (i % 4 != 0) {
                    v.fill(i, lv[k] + 1, d - 1, i, lv[k] + 2, d - 1, Block.of("light_blue_stained_glass", 0x6699D8));
                }
            }
        }
        // 계단실 (서쪽 끝, 출입구가 정면 쪽 복도를 봄)
        Frame st = Frame.facing(v, 5, d - 4, "north");
        int sd = Interior.stairDepth(lv);
        Interior.stairCore(st, lv, Interior.CORE_WALL, "polished_andesite", 0x848685);
        st.fill(-1, roof + 3, -1, 5, roof + 3, sd, SMOOTH_STONE);
        // 정문 (가운데) + 차양 + 이름
        v.fill(mid - 1, 0, d - 1, mid + 1, 2, d - 1, AIR);
        v.fill(mid - 4, 0, d - 1, mid - 2, 2, d - 1, Block.of("light_blue_stained_glass", 0x6699D8));
        v.fill(mid + 2, 0, d - 1, mid + 4, 2, d - 1, Block.of("light_blue_stained_glass", 0x6699D8));

        ground(v, f, r, w, d, sw, mid);
        controlRooms(v, f, r, w, d, sw, mid, lv[1], lv[2] - lv[1]);
        upper(v, f, r, w, d, sw, mid, lv[2], lv[3] - lv[2]);
        newsSet(v, f, r, 1, mid - 1, sw);
        varietySet(v, f, r, mid + 1, w - 2, sw);
        grid(v, 1, mid - 1, sw, roof);
        grid(v, mid + 1, w - 2, sw, roof);
        // 옥상: 위성 접시
        for (int k = 0; k < 3; k++) {
            int ci = 8 + k * 9, cj = 9;
            v.fill(ci, roof, cj, ci, roof + 1, cj, IRON_BARS);
            dish(v, ci, roof + 3, cj, 2, "south");
        }
        v.connect();
        return v;
    }

    /** 1층 앞쪽 띠: 로비(안내 데스크, 대기 소파), 스튜디오 큰 문 둘, 화장실 */
    private static void ground(Voxels v, Frame f, Random r, int w, int d, int sw, int mid) {
        Interior.floor(f, 1, sw + 1, w - 2, d - 2, 0, Block.of("smooth_quartz", 0xECE6DF));
        // 안내 데스크와 소파
        for (int i = mid - 3; i <= mid + 3; i++) {
            v.set(i, 0, d - 6, Furniture.COUNTER);
        }
        Furniture.chair(f, mid, 0, d - 7, "north", "dark_oak");
        v.set(mid + 2, 3, d - 6, Blocks.hangingSign("dark_oak", 0, "white", true, "", "안내", "준서방송"));
        Furniture.sofa(f, r, 9, 0, d - 3, 4, "north");
        // 스튜디오 문 (두 짝) + 방송 중 표시
        for (int[] door : new int[][]{{10, 0}, {mid + 3, 1}}) {
            int a = door[0];
            Interior.door(f, a, 0, sw, "spruce", "south");
            Interior.door(f, a + 1, 0, sw, "spruce", "south");
            v.set(a, 2, sw + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", door[1] == 0 ? "A 스튜디오" : "B 스튜디오"));
            v.set(a + 1, 3, sw + 1, Blocks.wallSign("dark_oak", "south", "red", true, "", "ON AIR"));
        }
        // 화장실 (동쪽 끝)
        Interior.restroom(f, w - 11, sw + 2, w - 8, d - 5, 0, 5, "남자 화장실", w - 9);
        Interior.restroom(f, w - 5, sw + 2, w - 2, d - 5, 0, 5, "여자 화장실", w - 3);
        v.fill(w - 1, 0, sw + 1, w - 1, 3, d - 2, Block.of("light_gray_concrete", 0x7D7D73));   // 겉벽 다시
        Interior.lights(f, 7, sw + 1, w - 13, d - 2, 0, 5, 4, Interior.LIGHT);
    }

    /** 2층: 부조정실 둘 (스튜디오를 내려다보는 창, 모니터 벽, 콘솔), 복도 */
    private static void controlRooms(Voxels v, Frame f, Random r, int w, int d, int sw, int mid, int L, int h) {
        int top = L + h - 2, wall = d - 4;     // 방과 복도 사이 벽 (복도는 d-3..d-2)
        Block in = Interior.INNER_WALL;
        Interior.floor(f, 7, sw + 1, w - 2, d - 2, L, Block.of("gray_concrete_powder", 0x4C5155));
        v.fill(7, L, wall, w - 2, top, wall, in);
        v.fill(mid, L, sw + 1, mid, top, wall, in);
        for (int[] room : new int[][]{{7, mid - 1, 0}, {mid + 1, w - 2, 1}}) {
            int a0 = room[0], a1 = room[1];
            Interior.door(f, a0 + 2, L, wall, "dark_oak", "south");
            v.set(a0 + 3, L + 1, wall + 1, Blocks.wallSign("birch", "south", "black", false, "", room[2] == 0 ? "A 부조정실" : "B 부조정실"));
            // 스튜디오 쪽 창 (반) + 모니터 벽 (반)
            int split = (a0 + a1) / 2;
            for (int i = a0; i <= a1; i++) {
                if (i <= split) {
                    v.fill(i, L + 1, sw, i, L + 2, sw, Block.of("glass_pane", 0xC8DCE4));
                } else {
                    v.fill(i, L, sw + 1, i, L + 2, sw + 1, BLACK_CONCRETE);
                }
            }
            // 콘솔 줄과 의자
            for (int i = a0 + 1; i <= a1 - 1; i++) {
                v.set(i, L, sw + 3, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
                if (i % 2 == 0) {
                    v.set(i, L + 1, sw + 3, Block.of("stone_button[face=floor,facing=north,powered=false]", 0x7E7E7E));
                    Furniture.chair(f, i, L, sw + 4, "south", "dark_oak");
                } else {
                    v.set(i, L + 1, sw + 3, Block.of("lever[face=floor,facing=north,powered=false]", 0x6B5839));
                }
            }
            v.set((a0 + a1) / 2, top, sw + 4, Interior.LIGHT);
        }
        Interior.lights(f, 7, wall + 1, w - 2, d - 2, L, h, 4, Interior.LIGHT);
    }

    /** 3층: 분장실, 출연자 대기실 */
    private static void upper(Voxels v, Frame f, Random r, int w, int d, int sw, int mid, int L, int h) {
        int top = L + h - 2, wall = d - 4;
        Block in = Interior.INNER_WALL;
        Interior.floor(f, 7, sw + 1, w - 2, d - 2, L, Block.of("light_gray_concrete", 0x7D7D73));
        v.fill(7, L, wall, w - 2, top, wall, in);
        v.fill(mid, L, sw + 1, mid, top, wall, in);
        // 분장실: 거울 벽과 의자 줄
        Interior.door(f, 9, L, wall, "pale_oak", "south");
        v.set(10, L + 1, wall + 1, Blocks.wallSign("birch", "south", "black", false, "", "분장실"));
        for (int i = 8; i < mid; i++) {
            v.set(i, L + 1, sw + 1, Block.of("light_blue_stained_glass", 0x6699D8));
            v.set(i, L, sw + 2, Furniture.WHITE_TOP);
            if (i % 2 == 0) {
                Furniture.chair(f, i, L, sw + 3, "south", "pale_oak");
                v.set(i, L + 2, sw + 1, SHROOMLIGHT);
            }
        }
        // 출연자 대기실: 소파, 탁자, 화면
        Interior.door(f, mid + 2, L, wall, "pale_oak", "south");
        v.set(mid + 3, L + 1, wall + 1, Blocks.wallSign("birch", "south", "black", false, "", "출연자 대기실"));
        Furniture.sofa(f, r, mid + 2, L, sw + 1, 5, "south");
        v.set(mid + 4, L, sw + 3, Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
        v.set(w - 2, L + 1, sw + 2, Furniture.TV);
        Furniture.plant(f, r, w - 2, L, wall - 1);
        Interior.lights(f, 7, sw + 1, w - 2, d - 2, L, h, 4, Interior.LIGHT);
    }

    /** A 스튜디오 뉴스 세트: 뒷벽 큰 화면과 파란 판, 앵커 데스크, 카메라 셋 */
    private static void newsSet(Voxels v, Frame f, Random r, int a0, int a1, int sw) {
        int ci = (a0 + a1) / 2;
        // 뒷벽 판과 화면
        for (int i = a0 + 1; i <= a1 - 1; i++) {
            v.fill(i, 0, 2, i, 7, 2, Block.of("blue_concrete", 0x2C2E8F));
        }
        v.fill(ci - 5, 2, 3, ci + 5, 6, 3, BLACK_CONCRETE);
        v.fill(ci - 4, 3, 3, ci + 4, 5, 3, Block.of("light_blue_stained_glass", 0x6699D8));
        // 앵커 데스크 (둥근 앞면)
        for (int i = ci - 4; i <= ci + 4; i++) {
            int j = 8 + (Math.abs(i - ci) >= 3 ? -1 : 0);
            v.set(i, 0, j, Block.of("smooth_quartz", 0xECE6DF));
            v.set(i, 0, j + 1, Block.of("light_blue_concrete", 0x2389C7));
        }
        Furniture.chair(f, ci - 1, 0, 6, "north", "dark_oak");
        Furniture.chair(f, ci + 1, 0, 6, "north", "dark_oak");
        // 카메라 (받침 기둥 + 몸통 + 프롬프터 유리)
        for (int k = -1; k <= 1; k++) {
            int i = ci + k * 5, j = 17 - Math.abs(k) * 2;
            v.set(i, 0, j, Block.of("polished_blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]", 0x353038));
            v.set(i, 1, j, BLACK_CONCRETE);
            v.set(i, 1, j - 1, Block.of("black_stained_glass", 0x191919));
        }
    }

    /** B 스튜디오 예능·드라마 세트: 거실 세트, 낮은 무대, 방청석 */
    private static void varietySet(Voxels v, Frame f, Random r, int a0, int a1, int sw) {
        // 거실 세트 (벽 두 면, 창틀, 소파, 탁자, TV)
        Block setWall = Block.of("white_terracotta", 0xD1B2A1);
        v.fill(a0 + 1, 0, 2, a0 + 9, 5, 2, setWall);
        v.fill(a0 + 1, 0, 2, a0 + 1, 5, 9, setWall);
        v.fill(a0 + 4, 2, 2, a0 + 6, 3, 2, Block.of("glass_pane", 0xC8DCE4));
        Interior.floor(f, a0 + 2, 3, a0 + 9, 9, 0, Rooms.MARU);
        Furniture.sofa(f, r, a0 + 4, 0, 4, 3, "south");
        v.set(a0 + 5, 0, 6, Block.of("oak_slab[type=top,waterlogged=false]", 0xA2834F));
        Furniture.plant(f, r, a0 + 2, 0, 3);
        // 무대 (1칸) + 앞 계단
        int s0 = a0 + 11, s1 = a1 - 1;
        v.fill(s0, 0, 2, s1, 0, 9, Block.of("dark_oak_planks", 0x432B14));
        for (int i = s0; i <= s1; i++) {
            v.set(i, 0, 10, Blocks.stairs("dark_oak", "north", 0x432B14));
        }
        // 방청석 (계단식 세 줄, 뒤가 높음)
        for (int k = 0; k < 3; k++) {
            int j = 17 + k * 2;
            for (int i = a0 + 2; i <= a1 - 1; i++) {
                if (k > 0) {
                    v.fill(i, 0, j, i, k - 1, j + 1, Block.of("gray_concrete", 0x36393D));
                }
                v.set(i, k, j + 1, Blocks.stairs("red_nether_brick", "south", 0x450709));   // 의자 (무대를 봄)
            }
            if (k > 0) {
                v.set(a0 + 1, k - 1, j, Blocks.stairs("dark_oak", "south", 0x432B14));     // 통로 계단
            }
        }
    }

    /** 천장 조명 격자: 3칸 간격 쇠 파이프, 교차점 등, 지붕에 매단 사슬 */
    private static void grid(Voxels v, int a0, int a1, int sw, int roof) {
        int y = roof - 4;
        for (int j = 2; j < sw - 1; j++) {
            for (int i = a0 + 1; i < a1; i++) {
                boolean li = (i - a0) % 3 == 0, lj = j % 3 == 0;
                if (li || lj) {
                    v.set(i, y, j, IRON_BARS);
                }
                if (li && lj) {
                    v.set(i, y - 1, j, LANTERN_HANGING);
                    if ((i + j) % 2 == 0) {
                        v.fill(i, y + 1, j, i, roof - 2, j, Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050));
                    }
                }
            }
        }
    }

    /** 주차장: 아스팔트, 주차선(차는 없음), 가장자리 나무 */
    static Voxels parking(Site s) {
        Voxels v = new Voxels(s.w, s.d, -1, 8);
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (s.land(i, j)) {
                    v.set(i, -1, j, Block.of("gray_concrete", 0x36393D));
                }
            }
        }
        for (int i = 2; i + 5 < s.w; i += 13) {
            for (int j = 2; j + 3 < s.d - 2; j += 3) {
                for (int[] row : new int[][]{{i, 0}, {i + 8, 0}}) {
                    if (s.solid(row[0], j) && s.solid(row[0] + 4, j)) {
                        v.fill(row[0], -1, j, row[0] + 4, -1, j, WHITE_CONCRETE);
                    }
                }
            }
        }
        for (int j = 3; j < s.d - 3; j += 9) {
            if (s.solid(s.w - 2, j, 2)) {
                Site.tree(v, s.w - 2, j, false);
            }
        }
        return v;
    }

    // ------------------------------------------------------------------ 앞마당

    /** 앞마당 (i = x - x0, j = z - z0): 보도 포장, 거점, 표석, 화단, 보행등, 국기 게양대 */
    static Voxels forecourt(Site s, int hi, int hj, int towerJ, int studioJ) {
        Voxels v = new Voxels(s.w, s.d, -1, 14);
        for (int j = 0; j < s.d; j++) {
            for (int i = 0; i < s.w; i++) {
                if (s.land(i, j)) {
                    v.set(i, -1, j, Math.floorMod(i - hi, 5) == 0 || Math.floorMod(j - hj, 5) == 0
                            ? Block.of("light_gray_concrete", 0x7D7D73) : POLISHED_ANDESITE);
                }
            }
        }
        // 국기 게양대 셋 (거점 동쪽)
        for (int k = -1; k <= 1; k++) {
            int i = s.w - 4, j = hj + k * 4;
            if (s.solid(i, j)) {
                Site.flagpole(v, i, j, 10);
            }
        }
        // 표석
        int mi = hi + 1, mj = hj + 8;
        if (s.solid(mi, mj, 1) && s.solid(mi + 3, mj, 1)) {
            v.fill(mi, 0, mj, mi + 3, 1, mj, POLISHED_GRANITE);
            v.set(mi + 1, 1, mj - 1, Blocks.wallSign("dark_oak", "north", "white", false, "", "준서방송", "JSBC"));
            v.set(mi + 2, 1, mj + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", "준서방송", "JSBC"));
        }
        // 화단 나무와 보행등 (건물 앞 줄)
        for (int j = hj + 14; j < s.d - 3; j += 8) {
            if (s.solid(s.w - 6, j, 2)) {
                Site.planter(v, s.w - 6, j);
            }
            if (s.solid(s.w - 3, j + 4)) {
                Site.lamp(v, s.w - 3, j + 4);
            }
        }
        for (int j = hj + 16; j < s.d - 3; j += 10) {
            if (s.solid(hi, j, 1)) {
                Site.bench(v, hi, j, 3, false);
            }
        }
        v.connect();
        return v;
    }
}
