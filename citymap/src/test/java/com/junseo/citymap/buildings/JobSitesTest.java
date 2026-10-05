package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 혼자 하는 직업 일터 (구인천 항만·갯벌, 북한산 벌채 구역·목재 집하장, 청라 농지·축산 농장):
 * 실제 지형과 건물을 합친 세상 조각에서 큰길부터 걸어서 일터까지 가는지, 건물 층마다 계단으로 가는지, 미리보기 그림.
 */
class JobSitesTest {

    static Placement find(String name) {
        for (Placement p : TestCity.buildings().placements()) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    static List<Placement> findAll(String prefix) {
        List<Placement> out = new ArrayList<>();
        for (Placement p : TestCity.buildings().placements()) {
            if (p.name.startsWith(prefix)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * 실제 세상 조각 (지형 + 모든 건물, 게임에서 놓이는 그대로). 건물 y (땅 위 첫 칸 = 0) 를 SH 만큼 올려 담아서
     * WalkCheck 가 땅 밑(갯벌·물가)도 다룹니다. 물은 걸을 수도 설 수도 없는 칸(철창)으로 바꿉니다.
     */
    static final class Slice {
        static final int SH = 8;
        final int x0, z0;
        final Voxels v;

        Slice(int x0, int z0, int x1, int z1, int ytop) {
            this.x0 = x0;
            this.z0 = z0;
            CityTerrain t = TestCity.terrain();
            CityBuildings b = TestCity.buildings();
            v = new Voxels(x1 - x0 + 1, z1 - z0 + 1, 0, ytop + SH);
            int base = t.groundY() + 1;
            Block stone = Block.of("stone", 0x7E7E7E), bars = Block.of("iron_bars", 0x888888);
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    Column c = t.column(x, z);
                    int i = x - x0, j = z - z0;
                    if (c.isWater() && !c.deck) {
                        int bed = c.groundY - base;
                        v.fill(i, 0, j, i, bed + SH, j, stone);
                        v.fill(i, bed + 1 + SH, j, i, c.waterTop - base + SH, j, bars);
                    } else {
                        int top = (c.deck ? t.groundY() : c.groundY) - base;
                        v.fill(i, 0, j, i, top + SH, j, stone);
                    }
                    b.column(x, z, c, (y, blk) -> v.set(i, y - base + SH, j, blk.id().equals("minecraft:water") ? bars : blk));
                }
            }
        }

        /** 걸어서 갈 수 있는 곳: 월드 (x, z) 의 서는 높이 y (건물 y) 에서 출발 */
        WalkCheck walk(int x, int y, int z) {
            return new WalkCheck(v).run(x - x0 + 1, y + SH, z - z0 + 1);
        }

        boolean reached(WalkCheck w, int x, int y, int z) {
            return w.reached(x - x0, y + SH, z - z0);
        }

        /** 직사각형 안에서 서는 높이 y 로 간 칸 수 */
        int count(WalkCheck w, int xa, int za, int xb, int zb, int y) {
            int n = 0;
            for (int z = za; z <= zb; z++) {
                for (int x = xa; x <= xb; x++) {
                    if (reached(w, x, y, z)) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    // ------------------------------------------------------------------ 항만

    @Test
    void portBuildingsHaveStairsToEveryFloor() {
        InteriorTest.assertAllFloorsReachable("하역 사무소", Harbor.office(25, 15, new Random(1)), new int[]{0, 4, 8});
        Voxels crane = Harbor.crane(76, 15, 33, 1, new Random(2));
        WalkCheck walk = new WalkCheck(crane).run(Harbor.LEG_L - 2 + 3, 0, Harbor.STAIR_J + 1);
        int[] lv = Harbor.stairLevels();
        for (int y : lv) {
            assertTrue(walk.reachedAt(y) > 0, "크레인 계단 " + y + "층에 못 가요");
        }
        assertTrue(walk.reached(Harbor.LEG_L, Harbor.G + 3, 20), "크레인 거더 위 통로");
        assertTrue(walk.reached(Harbor.LEG_L + 7, Harbor.CAB, Harbor.TROLLEY_J + 5), "크레인 운전실");
    }

    @Test
    void portIsWalkableFromTheRoad() {
        PortPlan.Site s = PortPlan.Site.find(TestCity.terrain());
        assertNotNull(s, "매립지를 못 찾음");
        Slice w = new Slice(s.rx0 - 160, s.top - 30, s.rx1 + 4, s.wz1 + 2, 52);
        // 출발: 사무소 앞 큰길 인도
        int sx = s.hubX, sz = s.edge(sx) - 2;
        WalkCheck walk = w.walk(sx, 0, sz);
        assertTrue(w.count(walk, s.office.x0(), s.office.z0() + 1, s.office.x1(), s.office.z1(), 4) > 40, "하역 사무소 2층");
        assertTrue(w.count(walk, s.office.x0(), s.office.z0() + 1, s.office.x1(), s.office.z1(), 8) > 40, "하역 사무소 옥상");
        for (int k = 0; k < s.cranes.length; k++) {
            int cx = s.cranes[k];
            // 크레인 계단탑 문 앞 (육지 쪽) 과 거더 위 통로
            assertTrue(w.count(walk, cx - 16, s.craneZ0, cx - 9, s.rL - 2, 0) > 5, (k + 1) + "호 크레인 계단 밑");
            assertTrue(w.count(walk, cx - 9, s.craneZ0, cx - 8, s.rW, Harbor.G + 3) > 10, (k + 1) + "호 크레인 거더 위");
            assertTrue(w.count(walk, cx - 16, s.craneZ0, cx + 15, s.craneZ1, Harbor.CAB) > 4, (k + 1) + "호 크레인 운전실");
        }
        // 안벽, 트레일러 픽업장, 장치장 차로
        assertTrue(w.count(walk, s.tx0 + 1, s.qz - 1, s.tx1 - 1, s.qz - 1, 0) > 150, "안벽");
        assertTrue(w.count(walk, s.truckLot.x0(), s.truckLot.z0(), s.truckLot.x1(), s.truckLot.z1(), 0) > 500, "트레일러 픽업장");
        // 방파제 끝 (등대 앞)
        assertTrue(w.count(walk, s.wx0, s.wz1 - 12, s.wx0 + 4, s.wz1 - 7, 0) > 5, "방파제 끝");
        // 호송선 선착장: 물칸 앞 승선장, 대기실
        assertTrue(w.count(walk, s.bx0, s.bz0 - 3, s.bx1, s.bz0 - 2, 0) > 5, "호송선 승선장");
        assertTrue(w.count(walk, s.convoy.x1() - 7, s.bz0 - 21, s.convoy.x1() - 1, s.bz0 - 11, 0) > 20, "호송 대기실");
        // 갯벌 (바닷물 높이로 낮춘 펄)
        Placement flat = find("구인천 갯벌 체험장");
        assertNotNull(flat, "갯벌");
        double[] b = flat.bounds();
        assertTrue(w.count(walk, (int) b[0], (int) b[1], (int) b[2] - 1, (int) b[3] - 1, -1) > 500, "갯벌에 내려가기");
    }

    @Test
    void portHubIsOnTheOfficeForecourt() {
        Layout.Hub hub = Plans.hub(TestCity.terrain(), "port");
        PortPlan.Site s = PortPlan.Site.find(TestCity.terrain());
        assertTrue(s.forecourt.has((int) Math.floor(hub.x()), (int) Math.floor(hub.z())), "거점이 사무소 앞마당에");
        assertFalse(findAll("안벽 크레인").isEmpty());
    }

    // ------------------------------------------------------------------ 벌채 구역·목재 집하장

    @Test
    void lumberYardIsWalkableFromTheRoad() {
        Layout.Hub hub = Plans.hub(TestCity.terrain(), "lumber");
        assertNotNull(hub, "lumber 거점");
        assertNotNull(find("목재 집하장"), "목재 집하장");
        assertNotNull(find("북한산 벌채 구역"), "벌채 구역");
        int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
        int edge = LumberPlan.edge(TestCity.terrain(), hx, hz);
        Slice w = new Slice(hx - 40, hz - 180, hx + 100, edge + 6, 50);
        WalkCheck walk = w.walk(hx, 0, edge + 3);
        // 사무소 안 (카운터 앞), 계근대, 원목 더미 마당, 벌채 구역 북쪽 끝 임도와 가로 임도
        assertTrue(w.count(walk, hx - 16, hz - 8, hx - 8, hz - 4, 0) > 10, "목재 매입 사무소 안");
        assertTrue(w.count(walk, hx - 2, hz - 17, hx + 2, hz - 8, 0) > 20, "계근대");
        assertTrue(w.count(walk, hx - 2, hz - 170, hx + 2, hz - 160, 0) > 20, "벌채 구역 안쪽 임도");
        assertTrue(w.count(walk, hx - 30, hz - 112, hx + 30, hz - 108, 0) > 60, "벌채 구역 가로 임도");
        // 나무는 진짜 원목과 사라지지 않는 잎
        Voxels v = find("북한산 벌채 구역").voxels();
        int logs = 0, leaves = 0;
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = 0; j < v.d; j += 2) {
                for (int i = 0; i < v.w; i += 2) {
                    Block b = v.get(i, y, j);
                    if (b != null && b.id().endsWith("_log")) {
                        logs++;
                    }
                    if (b != null && b.id().endsWith("_leaves")) {
                        leaves++;
                        assertTrue("true".equals(b.property("persistent")), "잎이 사라지면 안 됨: " + b.data());
                    }
                }
            }
        }
        assertTrue(logs > 300 && leaves > 1000, "나무 " + logs + " 원목, " + leaves + " 잎");
    }

    // ------------------------------------------------------------------ 청라 농지·축산 농장

    @Test
    void farmAndRanchAreWalkableFromTheRoad() {
        CityTerrain t = TestCity.terrain();
        Layout.Hub farm = Plans.hub(t, "farm"), ranch = Plans.hub(t, "ranch");
        assertNotNull(farm, "farm 거점");
        assertNotNull(ranch, "ranch 거점");
        for (String n : new String[]{"청라 농지", "청라 농산물 공판장", "청라 축산 농장", "우사", "착유실", "돈사", "계사", "축산 농장 사무실"}) {
            assertNotNull(find(n), n);
        }
        FarmPlan.Fields f = new FarmPlan.Fields(t, (int) Math.floor(farm.x()), (int) Math.floor(farm.z()));
        FarmPlan.Ranch r = new FarmPlan.Ranch(t, (int) Math.floor(ranch.x()), (int) Math.floor(ranch.z()));
        Slice w = new Slice(f.x0 - 2, r.z0, f.roadEast + 30, f.zS + 2, 20);
        // 출발: 농로 들머리 고속도로 가장자리
        WalkCheck walk = w.walk(f.roadEast + 2, 0, (f.rz0 + f.rz1) / 2);
        assertTrue(w.count(walk, f.hall.x0() + 1, f.hall.z0() + 2, f.hall.x1() - 1, f.hall.z1() - 1, 0) > 150, "공판장 안");
        assertTrue(w.count(walk, f.x0, f.zN, f.x0 + 60, f.zN + 100, 0) > 300, "서쪽 논두렁·농로");
        assertTrue(w.count(walk, f.x0, f.rz1 + 2, f.x0 + 100, f.zS - 8, 0) > 200, "밭 사이 길");
        WalkCheck rw = w.walk(r.x1 + 2, 0, (r.rd0 + r.rd1) / 2);
        PortPlan.R[] barns = {r.cattle, r.milk, r.pig, r.chicken, r.office};
        String[] names = {"우사", "착유실", "돈사", "계사", "농장 사무실"};
        for (int k = 0; k < barns.length; k++) {
            PortPlan.R b = barns[k];
            assertTrue(w.count(rw, b.x0() + 1, b.z0() + 1, b.x1() - 1, b.z1() - 1, 0) > 6, names[k] + " 안");
        }
    }

    // ------------------------------------------------------------------ 미리보기

    record View(String name, int x0, int z0, int x1, int z1, int ymin, int ymax, int scale, int cut, boolean top) {
    }

    record Eye(String name, double x, double y, double z, double yaw, double pitch, int ymin, int ymax) {
    }

    static final View[] VIEWS = {
            new View("dz-port-iso", -650, 630, -395, 860, -8, 52, 3, 0, false),
            new View("dz-port-top", -800, 620, -340, 860, -8, 52, 2, 0, true),
            new View("dz-port-gate", -612, 655, -500, 735, -6, 16, 6, 0, false),
            new View("dz-port-quay", -600, 735, -480, 830, -6, 52, 5, 0, false),
            new View("dz-port-convoy", -645, 680, -600, 790, -6, 14, 7, 0, false),
            new View("dz-port-breakwater", -425, 760, -395, 856, -6, 20, 7, 0, false),
            new View("dz-mudflat", -795, 685, -640, 775, -6, 14, 4, 0, false),
            new View("dz-lumber-iso", -405, -670, -265, -470, -4, 60, 3, 0, false),
            new View("dz-lumber-yard", -402, -536, -330, -470, -4, 16, 6, 0, false),
            new View("dz-lumber-top", -405, -670, -265, -470, -4, 60, 3, 0, true),
            new View("dz-farm-iso", -1095, -50, -800, 200, -4, 16, 2, 0, false),
            new View("dz-farm-top", -1095, -50, -800, 200, -4, 16, 2, 0, true),
            new View("dz-farm-hall", -910, 112, -845, 160, -4, 14, 7, 0, false),
            new View("dz-farm-ranch", -1005, -45, -840, 16, -4, 16, 4, 0, false),
    };

    static final Eye[] EYES = {
            new Eye("dz-port-eye-gate", -556, 0, 664, 0, -6, -6, 60),
            new Eye("dz-port-eye-quay", -428, 0, 782, 95, 8, -6, 60),
            new Eye("dz-port-eye-yard", -556, 0, 724, -60, 2, -6, 60),
            new Eye("dz-port-eye-sea", -520, 18, 850, 180, 8, -6, 60),
            new Eye("dz-port-eye-breakwater", -410, 0, 800, 0, 0, -6, 40),
            new Eye("dz-mudflat-eye", -697, 0, 724, 0, -14, -6, 30),
            new Eye("dz-lumber-eye-yard", -366, 0, -488, 160, -4, -4, 60),
            new Eye("dz-lumber-eye-forest", -366, 0, -560, 180, 0, -4, 70),
            new Eye("dz-farm-eye-paddy", -884, 0, 110, 120, -10, -4, 30),
            new Eye("dz-farm-eye-ranch", -895, 0, -1, 80, -6, -4, 30),
    };

    @Test
    void renderPreviews() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        CityTerrain terrain = TestCity.terrain();
        CityBuildings b = TestCity.buildings();
        File dir = new File("build/preview");
        dir.mkdirs();
        String only = System.getProperty("previewOnly", "");
        int g = terrain.groundY();
        for (View v : VIEWS) {
            if (!only.isEmpty() && !v.name.contains(only)) {
                continue;
            }
            IsoRender r = new IsoRender(terrain, b, v.x0, v.z0, v.x1, v.z1, g + v.ymin, g + v.ymax);
            BufferedImage img = v.top ? r.top(v.scale) : r.iso(v.scale, v.cut == 0 ? Integer.MAX_VALUE : g + v.cut);
            ImageIO.write(img, "png", new File(dir, v.name + ".png"));
        }
        for (Eye e : EYES) {
            if (!only.isEmpty() && !e.name.contains(only)) {
                continue;
            }
            int r = 170;
            int gy = terrain.standY((int) e.x, (int) e.z);
            IsoRender ir = new IsoRender(terrain, b, (int) e.x - r, (int) e.z - r, (int) e.x + r, (int) e.z + r, g + e.ymin, g + e.ymax);
            double ey = e.y == 0 ? gy + 1.6 : g + 1 + e.y;
            BufferedImage img = ir.perspective(e.x, ey, e.z, e.yaw, e.pitch, 80, 1280, 720);
            ImageIO.write(img, "png", new File(dir, e.name + ".png"));
        }
        if (only.isEmpty() || "dz-port-crane".contains(only) || only.contains("crane")) {
            Voxels crane = Harbor.crane(76, 15, 33, 1, new Random(2));
            ImageIO.write(new IsoRender(crane, 2).iso(5, Integer.MAX_VALUE), "png", new File(dir, "dz-port-crane.png"));
        }
        if (only.isEmpty() || only.contains("office")) {
            Voxels o = Harbor.office(25, 15, new Random(1));
            ImageIO.write(new IsoRender(o, 2).iso(12, Integer.MAX_VALUE), "png", new File(dir, "dz-port-office.png"));
            ImageIO.write(new IsoRender(o, 2).iso(12, 2), "png", new File(dir, "dz-port-office-1f.png"));
            ImageIO.write(new IsoRender(o, 2).iso(12, 6), "png", new File(dir, "dz-port-office-2f.png"));
        }
    }
}
