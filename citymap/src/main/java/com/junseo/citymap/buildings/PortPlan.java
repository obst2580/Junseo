package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 구인천 항만 (인천항 같은 컨테이너 부두): 구인천 남쪽 바다를 메운 매립지(설계도의 바다 모양을 고쳐 만든 땅) 위.
 * <ul>
 *   <li>컨테이너 터미널: 남쪽 안벽(콘크리트 케이슨, 계선주·방충재, 노란 안전선), 안벽 크레인 2대(레일 위),
 *       장치장 블록(컨테이너 1~4단, 냉동 컨테이너 블록과 전원 랙), 하역 장비 대기장(지게차·리치스태커 자리),
 *       차로 표시, 조명탑, 보안 울타리. 북쪽 길가에 정문(반입·반출 차로, 검수 부스, 차단기, 지붕),
 *       하역 사무소(2층, 앞마당에 거점), 트레일러 픽업장(화물 트럭 자리), 직원 주차장</li>
 *   <li>호송선 선착장 (서쪽 끝): 바다를 판 작은 물칸, 계단식 승선장, 대기실, 정문 경비 초소, 호송 차량 자리 — 교도소 섬 쪽</li>
 *   <li>방파제 (동쪽 끝): 매립지 동쪽 끝에서 남쪽으로 뻗은 방파제, 바다 쪽 테트라포드, 끝에 빨간 등대.
 *       해안 산책로로 길에서 걸어가고(낚시), 옆에 낚시객 주차장</li>
 * </ul>
 * 매립지·물칸·방파제 모양은 지형(바다 칸)에서 찾아서, 설계도가 조금 바뀌어도 따라갑니다.
 * 차·배 모형은 두지 않고 자리(차 꺼내는 자리)만 표시합니다.
 */
final class PortPlan {

    record R(int x0, int z0, int x1, int z1) {
        boolean has(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        boolean overlaps(double[] b) {
            return b[0] < x1 + 1 && b[2] > x0 && b[1] < z1 + 1 && b[3] > z0;
        }
    }

    static List<Placement> plan(CityTerrain t, List<Placement> existing) {
        Site s = Site.find(t);
        if (s == null) {
            return List.of();
        }
        Random rnd = Plans.random(t, "port");
        List<Placement> out = new ArrayList<>();
        long[] seeds = new long[24];
        for (int k = 0; k < seeds.length; k++) {
            seeds[k] = rnd.nextLong();
        }
        // 터미널 바닥 (울타리 둘레만 미니맵에 그림)
        out.add(Placement.rect("구인천 컨테이너 터미널", "port", s.tx0, s.top, s.tx1, s.qz, "south",
                (w, d) -> s.fenceRing(), (w, d) -> terminal(s, new Random(seeds[0]))));
        out.add(Placement.rect("터미널 정문", "port", s.gate.x0, s.gate.z0, s.gate.x1, s.gate.z1, "north",
                (w, d) -> Harbor.gate(w, d, new String[]{"반출 2", "반출 1", "반입 2", "반입 1"}, new Random(seeds[1]))));
        out.add(Placement.rect("구인천항 하역 사무소", "port", s.office.x0, s.office.z0, s.office.x1, s.office.z1, "north",
                (w, d) -> Harbor.office(w, d, new Random(seeds[2]))));
        // 장치장 블록
        R[] blocks = {s.blockWA, s.blockMA, s.blockMB, s.blockEA, s.blockEB};
        String[] names = {"장치장 1A", "장치장 2A", "장치장 2B", "장치장 3A", "냉동 장치장 3B"};
        for (int k = 0; k < blocks.length; k++) {
            R b = blocks[k];
            boolean reefer = k == 4;
            long seed = seeds[3 + k];
            out.add(Placement.rect(names[k], "container", b.x0, b.z0, b.x1, b.z1, "south",
                    (w, d) -> Harbor.containerBlock(w, d, new Random(seed), reefer)));
        }
        // 안벽 크레인
        for (int k = 0; k < s.cranes.length; k++) {
            int cx = s.cranes[k], no = k + 1;
            long seed = seeds[10 + k];
            int jL = s.rL - s.craneZ0, jW = s.rW - s.craneZ0;
            out.add(Placement.rect("안벽 크레인 " + no + "호", "crane", cx - 16, s.craneZ0, cx + 15, s.craneZ1, "south",
                    (w, d) -> List.of(new double[]{4, jL - 10}, new double[]{28, jL - 10}, new double[]{28, jW + 1}, new double[]{4, jW + 1}),
                    (w, d) -> Harbor.crane(d, jL, jW, no, new Random(seed))));
        }
        // 트레일러 픽업장 (화물 트럭 일 시작하는 곳), 직원 주차장
        out.add(Placement.rect("트레일러 픽업장", "parking", s.truckLot.x0, s.truckLot.z0, s.truckLot.x1, s.truckLot.z1, "south",
                (w, d) -> truckLot(s)));
        out.add(Placement.rect("터미널 직원 주차장", "parking", s.staff.x0, s.staff.z0, s.staff.x1, s.staff.z1, "north",
                (w, d) -> ParkingLot.build(w, d, null)));
        // 호송선 선착장
        out.add(Placement.rect("호송선 선착장", "pier", s.convoy.x0, s.convoy.z0, s.convoy.x1, s.convoy.z1, "south",
                (w, d) -> List.of(new double[]{s.bx0 - 5 - s.convoy.x0, s.bz0 - 8 - s.convoy.z0}, new double[]{s.convoy.x1 + 1 - s.convoy.x0, s.bz0 - 8 - s.convoy.z0},
                        new double[]{s.convoy.x1 + 1 - s.convoy.x0, s.qz + 1 - s.convoy.z0}, new double[]{s.bx0 - 5 - s.convoy.x0, s.qz + 1 - s.convoy.z0}),
                (w, d) -> convoy(s, new Random(seeds[14]))));
        // 해안 산책로와 방파제·등대
        out.add(Placement.rect("구인천 방파제", "pier", s.strip.x0, s.strip.z0, s.strip.x1, s.strip.z1, "south",
                (w, d) -> List.of(new double[]{s.wx0 - s.strip.x0, s.qz + 1 - s.strip.z0}, new double[]{s.wx1 + 1 - s.strip.x0, s.qz + 1 - s.strip.z0},
                        new double[]{s.wx1 + 1 - s.strip.x0, s.wz1 + 1 - s.strip.z0}, new double[]{s.wx0 - s.strip.x0, s.wz1 + 1 - s.strip.z0}),
                (w, d) -> breakwater(s, new Random(seeds[15]))));
        if (s.anglers != null) {
            out.add(Placement.rect("방파제 낚시객 주차장", "parking", s.anglers.x0, s.anglers.z0, s.anglers.x1, s.anglers.z1, "north",
                    (w, d) -> ParkingLot.build(w, d, null)));
        }
        // 다른 계획이 먼저 차지한 자리와 겹치면 그 부분은 짓지 않음
        List<Placement> kept = new ArrayList<>();
        for (Placement p : out) {
            if (clear(p, existing, (x, z) -> s.land(x, z) && t.column(x, z).district == null)) {
                kept.add(p);
            }
        }
        kept.addAll(Mudflat.plan(t, s, existing, seeds[16]));
        return kept;
    }

    /** 이미 있는 (땅에 깔린 것이 아닌) 건물이 이 자리의 구역 밖 땅을 차지하지 않았는지 (2칸 간격으로 찍어 봄) */
    static boolean clear(Placement p, List<Placement> existing) {
        return clear(p, existing, null);
    }

    /**
     * 이미 있는 (땅에 깔린 것이 아닌) 건물이 이 자리를 차지하지 않았는지 (2칸 간격으로 찍어 봄).
     * mine 이 null 이 아니면 그 칸(이 계획이 실제로 짓는 칸)만 봅니다.
     */
    static boolean clear(Placement p, List<Placement> existing, java.util.function.BiPredicate<Integer, Integer> mine) {
        double[] b = p.bounds();
        for (Placement q : existing) {
            if (CityBuildings.isGround(q)) {
                continue;
            }
            double[] c = q.bounds();
            if (!(b[0] < c[2] && b[2] > c[0] && b[1] < c[3] && b[3] > c[1])) {
                continue;
            }
            for (double z = Math.max(b[1], c[1]) + 0.5; z < Math.min(b[3], c[3]); z += 2) {
                for (double x = Math.max(b[0], c[0]) + 0.5; x < Math.min(b[2], c[2]); x += 2) {
                    if (q.covers(x, z) && (mine == null || mine.test((int) Math.floor(x), (int) Math.floor(z)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 자리 찾기

    /** 매립지와 그 위 시설들의 자리 (월드 좌표) */
    static final class Site {
        final CityTerrain t;
        /** 안벽 (남쪽 끝 땅 줄) z, 매립지 서·동 끝 x */
        final int qz, rx0, rx1;
        /** 호송선 물칸 x 범위와 북쪽 끝 z */
        final int bx0, bx1, bz0;
        /** 방파제 x 범위와 남쪽 끝 z */
        final int wx0, wx1, wz1;
        /** 터미널 울타리 서·동 x, 북쪽 울타리 z, 크레인 레일 (바다 쪽, 육지 쪽) */
        final int tx0, tx1, fz, rW, rL;
        /** 터미널 상자 북쪽 끝 (길 가장자리 중 가장 북쪽) */
        final int top;
        final int[] edge;
        final R gate, office, forecourt, truckLot, staff, convoy, strip, anglers;
        final R blockWA, blockMA, blockMB, blockEA, blockEB, equip;
        final int[] cranes;
        final int craneZ0, craneZ1;
        final int hubX, hubZ;

        private Site(CityTerrain t, int qz, int rx0, int rx1, int bx0, int bx1, int bz0, int wx0, int wx1, int wz1, int hubX, int hubZ) {
            this.t = t;
            this.qz = qz;
            this.rx0 = rx0;
            this.rx1 = rx1;
            this.bx0 = bx0;
            this.bx1 = bx1;
            this.bz0 = bz0;
            this.wx0 = wx0;
            this.wx1 = wx1;
            this.wz1 = wz1;
            this.hubX = hubX;
            this.hubZ = hubZ;
            tx0 = bx1 + 12;
            tx1 = wx0 - 3;
            fz = qz - 87;
            rW = qz - 3;
            rL = qz - 21;
            edge = new int[rx1 - rx0 + 1];
            int north = Integer.MAX_VALUE;
            for (int x = rx0; x <= rx1; x++) {
                edge[x - rx0] = roadEdge(t, x, qz);
                if (x >= tx0 && x <= tx1) {
                    north = Math.min(north, edge[x - rx0]);
                }
            }
            top = north;
            gate = new R(tx0 + 37, fz - 14, tx0 + 62, fz);
            office = new R(tx0 + 68, fz - 14, tx0 + 92, fz);
            forecourt = new R(tx0 + 68, edge(tx0 + 80), tx0 + 92, fz - 15);
            truckLot = new R(tx0 + 97, edgeMin(tx0 + 97, tx1 - 2), tx1 - 2, fz - 1);
            staff = new R(tx0 + 1, fz - 13, tx0 + 34, fz - 1);
            int ra0 = qz - 55, ra1 = qz - 38, rb0 = qz - 82, rb1 = qz - 65;
            blockWA = new R(tx0 + 4, ra0, tx0 + 34, ra1);
            equip = new R(tx0 + 4, rb0, tx0 + 34, rb1);
            blockMA = new R(tx0 + 65, ra0, tx0 + 112, ra1);
            blockMB = new R(tx0 + 65, rb0, tx0 + 112, rb1);
            blockEA = new R(tx0 + 122, ra0, tx0 + 173, ra1);
            blockEB = new R(tx0 + 122, rb0, tx0 + 173, rb1);
            cranes = new int[]{tx0 + 50, tx0 + 130};
            craneZ0 = rL - 15;
            craneZ1 = qz + 39;
            convoy = new R(rx0, edgeMin(rx0, tx0 - 1), tx0 - 1, qz);
            strip = new R(tx1 + 1, edgeMin(tx1 + 1, rx1), rx1, wz1);
            int az0 = roadEdge(t, rx1 + 3, qz) + 1;
            R a = new R(rx1 + 2, az0, rx1 + 35, az0 + 27);
            boolean ok = true;
            for (int z = a.z0; z <= a.z1 && ok; z++) {
                for (int x = a.x0; x <= a.x1 && ok; x++) {
                    Column c = t.column(x, z);
                    ok = !c.isWater() && !c.isRoad() && c.district == null && c.mountainHeight == 0;
                }
            }
            anglers = ok ? a : null;
        }

        int edge(int x) {
            return x >= rx0 && x <= rx1 ? edge[x - rx0] : roadEdge(t, x, qz);
        }

        int edgeMin(int xa, int xb) {
            int m = Integer.MAX_VALUE;
            for (int x = xa; x <= xb; x++) {
                m = Math.min(m, edge(x));
            }
            return m;
        }

        /** x 줄에서 길(큰길) 바로 남쪽 첫 땅 z */
        static int roadEdge(CityTerrain t, int x, int qz) {
            int z = qz - 40;
            while (z > qz - 260 && !t.column(x, z - 1).isRoad()) {
                z--;
            }
            return z;
        }

        boolean water(int x, int z) {
            return t.column(x, z).isWater();
        }

        /** 길·물이 아닌 땅 */
        boolean land(int x, int z) {
            Column c = t.column(x, z);
            return !c.isWater() && !c.isRoad() && !c.deck;
        }

        /** 미니맵용: 터미널 울타리 띠 (건물 좌표) */
        List<double[]> fenceRing() {
            double ox0 = 0, oz0 = fz - top, ox1 = tx1 + 1 - tx0, oz1 = qz + 1 - top;
            List<double[]> p = new ArrayList<>();
            p.add(new double[]{ox0, oz1});
            p.add(new double[]{ox0, oz0});
            p.add(new double[]{ox1, oz0});
            p.add(new double[]{ox1, oz1});
            p.add(new double[]{ox1 - 1, oz1});
            p.add(new double[]{ox1 - 1, oz0 + 1});
            p.add(new double[]{ox0 + 1, oz0 + 1});
            p.add(new double[]{ox0 + 1, oz1});
            return p;
        }

        /** 지형에서 매립지를 찾습니다 (거점 남쪽 바다 끝). 못 찾으면 null */
        static Site find(CityTerrain t) {
            Layout.Hub hub = Plans.hub(t, "port");
            if (hub == null) {
                return null;
            }
            int hx = (int) Math.floor(hub.x()), hz = (int) Math.floor(hub.z());
            int qz = hz;
            while (qz < hz + 300 && !t.column(hx, qz + 1).isWater()) {
                qz++;
            }
            if (qz - hz < 60 || qz - hz > 200) {
                return null;
            }
            int zr = qz - 25;
            int rx0 = hx, rx1 = hx;
            while (rx0 > hx - 400 && !t.column(rx0 - 1, zr).isWater()) {
                rx0--;
            }
            while (rx1 < hx + 400 && !t.column(rx1 + 1, zr).isWater()) {
                rx1++;
            }
            // 호송선 물칸 (서쪽 안벽에 파인 물)
            int bx0 = rx0;
            while (bx0 < rx0 + 40 && !t.column(bx0, qz).isWater()) {
                bx0++;
            }
            int bx1 = bx0;
            while (bx1 < rx0 + 60 && t.column(bx1 + 1, qz).isWater()) {
                bx1++;
            }
            int bz0 = qz;
            while (bz0 > qz - 40 && t.column(bx0, bz0 - 1).isWater()) {
                bz0--;
            }
            // 방파제 (동쪽 끝에서 남쪽으로 뻗은 땅)
            int wx1 = rx1;
            while (wx1 > rx1 - 40 && t.column(wx1, qz + 1).isWater()) {
                wx1--;
            }
            int wx0 = wx1;
            while (wx0 > rx1 - 60 && !t.column(wx0 - 1, qz + 1).isWater()) {
                wx0--;
            }
            int wz1 = qz + 1;
            while (wz1 < qz + 200 && !t.column(wx0, wz1 + 1).isWater()) {
                wz1++;
            }
            if (bx0 >= rx0 + 40 || bx1 - bx0 < 8 || wx1 <= rx1 - 40 || wx1 - wx0 < 8 || rx1 - rx0 < 200) {
                return null;
            }
            return new Site(t, qz, rx0, rx1, bx0, bx1, bz0, wx0, wx1, wz1, hx, hz);
        }
    }

    // ------------------------------------------------------------------ 터미널 바닥

    static final Block FENCE_POST = Block.of("andesite_wall", 0x888888);
    static final Block MESH = Block.of("iron_bars", 0x888888);
    static final Block FACE = Block.of("light_gray_concrete", 0x7D7D73);
    static final Block COPING = Block.of("smooth_stone", 0x9E9E9E);
    static final Block CURB_SLAB = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);

    /** 월드 좌표로 그리는 캔버스 (정면 남쪽 그대로) */
    static final class Canvas {
        final Voxels v;
        final int x0, z0;

        Canvas(int x0, int z0, int x1, int z1, int y0, int y1) {
            this.v = new Voxels(x1 - x0 + 1, z1 - z0 + 1, y0, y1);
            this.x0 = x0;
            this.z0 = z0;
        }

        void set(int x, int y, int z, Block b) {
            v.set(x - x0, y, z - z0, b);
        }

        Block get(int x, int y, int z) {
            return v.get(x - x0, y, z - z0);
        }

        void setIfEmpty(int x, int y, int z, Block b) {
            v.setIfEmpty(x - x0, y, z - z0, b);
        }

        void fill(int xa, int ya, int za, int xb, int yb, int zb, Block b) {
            v.fill(xa - x0, ya, za - z0, xb - x0, yb, zb - z0, b);
        }

        void carSpot(double x, int y, double z, int dx, int dz) {
            v.carSpot(x - x0, y, z - z0, dx, dz);
        }
    }

    /**
     * 터미널 바닥: 길가 앞마당·정문 진입로 포장, 보안 울타리, 장치장 차로(아스팔트, 흰 점선, 노란 블록 테두리),
     * 안벽 에이프런(밝은 콘크리트, 크레인 레일, 노란 안전선, 계선주, 방충재, 케이슨 면), 조명탑, 하역 장비 대기장.
     * 다른 시설(정문·사무소·장치장 블록·픽업장·주차장) 자리는 비워 둡니다.
     */
    static Voxels terminal(Site s, Random r) {
        Canvas c = new Canvas(s.tx0, s.top, s.tx1, s.qz, -5, 32);
        R[] own = {s.gate, s.office, s.truckLot, s.staff, s.blockWA, s.blockMA, s.blockMB, s.blockEA, s.blockEB};
        Block pave = Block.of("light_gray_concrete", 0x7D7D73);
        Block paveDark = Block.of("polished_andesite", 0x848685);
        for (int z = s.top; z <= s.qz; z++) {
            for (int x = s.tx0; x <= s.tx1; x++) {
                if (!s.land(x, z) || z < s.edge(x)) {
                    continue;
                }
                boolean taken = false;
                for (R o : own) {
                    taken |= o.has(x, z);
                }
                if (taken) {
                    continue;
                }
                Block g;
                if (z < s.fz) {
                    // 바깥 (길가): 정문 진입로는 아스팔트, 나머지는 보도 포장
                    boolean approach = x >= s.gate.x0 && x <= s.gate.x1;
                    g = approach ? Harbor.ASPHALT : (Math.floorMod(x + z, 6) == 0 ? paveDark : pave);
                } else if (z >= s.rL - 12) {
                    g = Harbor.APRON;
                } else {
                    g = Harbor.ASPHALT;
                }
                c.set(x, -1, z, g);
            }
        }
        // 정문 진입로 차선 (정문 섬 줄을 길까지 이음)
        for (int z = s.edge(s.gate.x0 + 13); z < s.gate.z0; z++) {
            for (int k = 0; k <= 4; k++) {
                int x = s.gate.x1 - k * 6 - 1;
                if (k == 2) {
                    c.set(x, -1, z, Harbor.YELLOW);
                    c.set(x - 1, -1, z, Harbor.YELLOW);
                } else if (k > 0 && k < 4 && Math.floorMod(z, 4) < 2) {
                    c.set(x, -1, z, Harbor.LINE);
                }
            }
        }
        // ---- 보안 울타리: 북쪽 (정문·사무소 자리 빼고), 서쪽, 동쪽
        for (int x = s.tx0; x <= s.tx1; x++) {
            if (x >= s.gate.x0 && x <= s.gate.x1 || x >= s.office.x0 && x <= s.office.x1) {
                continue;
            }
            fence(c, x, s.fz, x - s.tx0);
        }
        for (int z = s.fz; z <= s.qz - 1; z++) {
            fence(c, s.tx0, z, z - s.fz);
            fence(c, s.tx1, z, z - s.fz);
        }
        // ---- 장치장 차로: 블록 테두리 노란 선, 차로 가운데 흰 점선
        for (R b : new R[]{s.blockWA, s.blockMA, s.blockMB, s.blockEA, s.blockEB, s.equip}) {
            for (int x = b.x0 - 1; x <= b.x1 + 1; x++) {
                c.set(x, -1, b.z0 - 1, Harbor.YELLOW);
                c.set(x, -1, b.z1 + 1, Harbor.YELLOW);
            }
            for (int z = b.z0 - 1; z <= b.z1 + 1; z++) {
                c.set(b.x0 - 1, -1, z, Harbor.YELLOW);
                c.set(b.x1 + 1, -1, z, Harbor.YELLOW);
            }
        }
        int laneZ = (s.blockMB.z1 + s.blockMA.z0) / 2;
        for (int x = s.tx0 + 2; x < s.tx1 - 1; x++) {
            if (Math.floorMod(x, 6) < 3) {
                c.set(x, -1, laneZ, Harbor.LINE);
                c.set(x, -1, s.fz + 2, Harbor.LINE);
                c.set(x, -1, s.blockMA.z1 + 3, Harbor.LINE);
            }
        }
        for (int lx : new int[]{(s.blockWA.x1 + s.blockMA.x0) / 2, (s.blockMA.x1 + s.blockEA.x0) / 2, (s.blockEA.x1 + s.tx1) / 2}) {
            for (int z = s.fz + 3; z < s.blockMA.z1 + 2; z++) {
                if (Math.floorMod(z, 6) < 3) {
                    c.set(lx, -1, z, Harbor.LINE);
                }
            }
        }
        // 정문 안쪽: 보행자 횡단보도 (사무소 뒷문 → 차로 건너)
        int door = s.office.x0 + 2; // 사무소 뒷문 (건물을 돌려 놓아 서쪽)
        for (int z = s.fz + 1; z <= s.blockMB.z0 - 2; z++) {
            for (int x = door - 1; x <= door + 1; x++) {
                c.set(x, -1, z, Math.floorMod(z, 2) == 0 ? Harbor.LINE : Harbor.ASPHALT);
            }
        }
        // ---- 하역 장비 대기장 (지게차·리치스태커): 노란 칸, 차 꺼내는 자리
        R e = s.equip;
        int stalls = (e.x1 - e.x0 + 1) / 5;
        for (int k = 0; k <= stalls; k++) {
            int x = e.x0 + k * 5;
            for (int z = e.z0 + 6; z <= e.z1; z++) {
                c.set(Math.min(x, e.x1), -1, z, Harbor.YELLOW);
            }
            if (k < stalls) {
                c.carSpot(x + 3.0, 0, e.z0 + 12.5, 0, 1);
            }
        }
        for (int x = e.x0; x <= e.x1; x++) {
            c.set(x, -1, e.z0 + 6, Harbor.YELLOW);
        }
        // 장비 대기장 표지판 (정비 컨테이너 옆), 정비용 컨테이너 사무실 하나
        Harbor.container(c.v, e.x0 - s.tx0, 0, e.z0 - s.top, 12, Harbor.BOX[4], true);
        c.set(e.x0 + 12, 1, e.z0 + 1, Blocks.wallSign("birch", "east", "black", false, "", "하역 장비", "대기장"));
        // ---- 에이프런: 크레인 레일, 차로 선, 노란 안전선, 안벽 끝 (갓돌·연석·계선주·방충재·케이슨 면)
        for (int x = s.tx0 + 1; x <= s.tx1 - 1; x++) {
            c.set(x, -1, s.rW, Harbor.RAIL);
            c.set(x, -1, s.rL, Harbor.RAIL);
            for (int lz : new int[]{s.rL + 5, s.rL + 9, s.rL + 13}) {
                if (Math.floorMod(x, 8) < 4) {
                    c.set(x, -1, lz, Harbor.LINE);
                }
            }
            c.set(x, -1, s.qz - 1, Harbor.YELLOW);
            c.set(x, -1, s.rL - 12, Harbor.LINE);
        }
        for (int rz : new int[]{s.rW, s.rL}) {
            c.set(s.tx0 + 1, 0, rz, Harbor.YELLOW); // 레일 끝 멈춤 장치
            c.set(s.tx1 - 1, 0, rz, Harbor.YELLOW);
        }
        quayEdge(c, s.tx0 + 1, s.tx1 - 1, s.qz, s.tx0);
        // 해치 커버 놓는 칸 (백리치 밑, 크레인 사이)
        for (int k = 0; k + 1 < s.cranes.length; k++) {
            int x0 = s.cranes[k] + 18, x1 = s.cranes[k + 1] - 19;
            box(c, x0, s.rL - 10, x1, s.rL - 2, Harbor.YELLOW);
        }
        // ---- 조명탑: 백리치 차로, 장치장 가운데 차로
        int[][] masts = {{s.tx0 + 6, s.rL - 14}, {(s.cranes[0] + s.cranes[1]) / 2, s.rL - 14}, {s.tx1 - 8, s.rL - 14},
                {s.tx0 + 50, laneZ}, {s.tx0 + 117, laneZ}, {s.tx1 - 9, laneZ}};
        for (int[] m : masts) {
            Harbor.lightMast(c.v, m[0] - s.tx0, m[1] - s.top, 26);
        }
        // 장치장 블록 이름 (조명탑 밑동 표지판)
        c.set(s.tx0 + 50, 1, laneZ - 1, Blocks.wallSign("birch", "north", "black", false, "", "2B 블록", "냉동 3B →"));
        c.set(s.tx0 + 117, 1, laneZ + 1, Blocks.wallSign("birch", "south", "black", false, "", "2A·3A 블록"));
        // ---- 사무소 앞마당 (거점): 포장 무늬, 화단 나무, 국기 게양대, 이름 돌
        R fc = s.forecourt;
        for (int z = fc.z0; z <= fc.z1; z++) {
            for (int x = fc.x0; x <= fc.x1; x++) {
                if (s.land(x, z) && z >= s.edge(x)) {
                    c.set(x, -1, z, (x - fc.x0) % 4 == 0 || (z - fc.z0) % 4 == 0 ? paveDark : pave);
                }
            }
        }
        for (int x : new int[]{fc.x0 + 2, fc.x1 - 2}) {
            int z = fc.z1 - 3;
            if (Math.abs(x - s.hubX) > 5 && s.land(x, z - 2)) {
                planter(c, x, z);
            }
        }
        for (int k = 0; k < 3; k++) {
            int x = fc.x0 + 7 + k * 2, z = fc.z1;
            if (Math.abs(x - s.hubX) > 4 || Math.abs(z - s.hubZ) > 4) {
                flagpole(c, x, z);
            }
        }
        // 길가 화단 (서쪽 직원 주차장 앞, 사무소와 픽업장 사이)
        for (int x = s.tx0 + 1; x <= s.tx0 + 34; x++) {
            for (int z = s.edge(x); z < s.staff.z0; z++) {
                if (s.land(x, z)) {
                    c.set(x, -1, z, (z == s.staff.z0 - 1) ? COPING : GRASS);
                }
            }
        }
        // 안전 표지판 (정문 안쪽)
        c.set(s.gate.x0 - 2, 1, s.fz + 1, Blocks.wallSign("birch", "south", "red", false, "보행자 주의", "안전모 착용", "제한속도 30"));
        c.v.connect();
        return c.v;
    }

    private static void flagpole(Canvas c, int x, int z) {
        c.set(x, 0, z, Block.of("polished_andesite", 0x848685));
        c.fill(x, 1, z, x, 9, z, Block.of("end_rod[facing=up]", 0xE8E2D8));
    }

    private static void planter(Canvas c, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                c.set(x + dx, -1, z + dz, GRASS);
                if (dx != 0 || dz != 0) {
                    c.set(x + dx, 0, z + dz, Block.of("stone_brick_slab[type=bottom,waterlogged=false]", 0x7A7979));
                }
            }
        }
        c.set(x, -1, z, COARSE_DIRT);
        c.fill(x, 0, z, x, 3, z, OAK_LOG);
        c.v.ellipsoid(x - c.x0 + 0.5, 5.2, z - c.z0 + 0.5, 2.3, 2.0, 2.3, OAK_LEAVES);
        c.set(x, 4, z, OAK_LOG);
    }

    /** 보안 울타리 한 칸: 3칸마다 기둥, 사이 철망 (위 철조망 한 줄) */
    static void fence(Canvas c, int x, int z, int along) {
        if (Math.floorMod(along, 3) == 0) {
            c.fill(x, 0, z, x, 2, z, FENCE_POST);
        } else {
            c.fill(x, 0, z, x, 2, z, MESH);
        }
    }

    /** 바닥 사각 테두리 선 */
    static void box(Canvas c, int x0, int z0, int x1, int z1, Block b) {
        for (int x = x0; x <= x1; x++) {
            c.set(x, -1, z0, b);
            c.set(x, -1, z1, b);
        }
        for (int z = z0; z <= z1; z++) {
            c.set(x0, -1, z, b);
            c.set(x1, -1, z, b);
        }
    }

    /**
     * 안벽 끝 줄 (z 줄, x0..x1): 갓돌, 케이슨 면(땅 칸 아래까지 콘크리트), 10칸마다 계선주, 사이사이 검은 방충재 면.
     * along0 은 계선주 간격을 맞출 기준 x.
     */
    static void quayEdge(Canvas c, int x0, int x1, int z, int along0) {
        for (int x = x0; x <= x1; x++) {
            int k = Math.floorMod(x - along0, 10);
            c.set(x, -1, z, COPING);
            c.fill(x, -4, z, x, -2, z, FACE);
            if (k == 5 || k == 6) {
                c.fill(x, -3, z, x, -1, z, Harbor.BLACK); // 방충재
            }
            if (k == 0) {
                c.set(x, 0, z, Harbor.BOLLARD);
            }
        }
    }

    // ------------------------------------------------------------------ 트레일러 픽업장

    /**
     * 트레일러 픽업장 (화물 트럭 일): 아스팔트, 너비 4·깊이 18 트럭 칸(흰 선)을 울타리 쪽 한 줄과 길 쪽 한 줄(들어가는 만큼),
     * 가운데 통로(노란 점선), 칸마다 번호 표지판과 차 꺼내는 자리, 조명.
     */
    static Voxels truckLot(Site s) {
        R l = s.truckLot;
        Canvas c = new Canvas(l.x0, l.z0, l.x1, l.z1, -1, 6);
        int row1 = l.z1 - 17, aisle0 = row1 - 11, row2 = aisle0 - 18;
        for (int z = l.z0; z <= l.z1; z++) {
            for (int x = l.x0; x <= l.x1; x++) {
                if (s.land(x, z) && z >= s.edge(x)) {
                    c.set(x, -1, z, Harbor.ASPHALT);
                }
            }
        }
        int n = 0;
        for (int x = l.x0 + 1; x + 4 <= l.x1; x += 4) {
            // 울타리 쪽 줄 (앞이 통로 = 북쪽)
            c.fill(x, -1, row1, x, -1, l.z1, Harbor.LINE);
            c.fill(x + 4, -1, row1, x + 4, -1, l.z1, Harbor.LINE);
            c.carSpot(x + 2.5, 0, row1 + 9.0, 0, -1);
            n++;
            c.set(x + 2, 0, l.z1, Block.of("oak_sign[rotation=8,waterlogged=false]", 0xA2834F)
                    .withText("black", false, "", String.format("A-%02d", n)));
            // 길 쪽 줄 (들어갈 때만, 앞이 통로 = 남쪽)
            boolean fits = true;
            for (int xx = x; xx <= x + 4; xx++) {
                fits &= s.edge(xx) <= row2 - 1;
            }
            if (fits) {
                c.fill(x, -1, row2, x, -1, aisle0 - 1, Harbor.LINE);
                c.fill(x + 4, -1, row2, x + 4, -1, aisle0 - 1, Harbor.LINE);
                c.carSpot(x + 2.5, 0, row2 + 9.0, 0, 1);
            }
        }
        // 통로 가운데 노란 점선, 조명
        int mid = (aisle0 + row1 - 1) / 2;
        for (int x = l.x0 + 2; x < l.x1 - 1; x++) {
            if (Math.floorMod(x, 6) < 3) {
                c.set(x, -1, mid, Harbor.YELLOW);
            }
        }
        // 안내판 (서쪽 입구)
        c.fill(l.x0, 0, mid - 3, l.x0, 1, mid - 3, Block.of("polished_andesite", 0x848685));
        c.set(l.x0, 2, mid - 3, Block.of("polished_andesite", 0x848685));
        c.set(l.x0 + 1, 2, mid - 3, Blocks.wallSign("birch", "east", "black", false, "트레일러", "픽업장", "화물차 전용"));
        return c.v;
    }

    // ------------------------------------------------------------------ 호송선 선착장

    /**
     * 호송선 선착장: 길에서 차단기 정문(경비 초소) → 차로 → 물칸 앞 회차 마당. 물칸 둘레 안벽(계선주·방충재·노란 선),
     * 물칸 안쪽 끝 계단식 승선장, 승선 대기 차양, 호송 대기실(의자·경찰 책상·화장실), 호송 버스·차 자리. 서쪽 땅과는 울타리.
     */
    static Voxels convoy(Site s, Random r) {
        R b = s.convoy;
        Canvas c = new Canvas(b.x0, b.z0, b.x1, b.z1, -5, 8);
        int gz = s.edgeMin(b.x0, b.x1) + 9; // 정문 줄
        int dx0 = (s.bx0 + s.bx1) / 2 - 4, dx1 = dx0 + 7; // 차로
        Block pave = Block.of("light_gray_concrete", 0x7D7D73);
        for (int z = b.z0; z <= b.z1; z++) {
            for (int x = b.x0; x <= b.x1; x++) {
                if (!s.land(x, z) || z < s.edge(x)) {
                    continue;
                }
                boolean lane = x >= dx0 && x <= dx1 && z < s.bz0 - 6;
                c.set(x, -1, z, lane ? Harbor.ASPHALT : z < gz ? GRASS : pave);
            }
        }
        // 길에서 정문까지 차로
        for (int z = s.edgeMin(dx0, dx1); z <= gz; z++) {
            for (int x = dx0; x <= dx1; x++) {
                if (s.land(x, z) && z >= s.edge(x)) {
                    c.set(x, -1, z, Harbor.ASPHALT);
                }
            }
        }
        // 울타리: 정문 줄 (차로 빼고), 서쪽 (육지 쪽만)
        for (int x = b.x0; x <= b.x1; x++) {
            if (x < dx0 || x > dx1) {
                fence(c, x, gz, x - b.x0);
            }
        }
        for (int z = gz; z <= s.qz; z++) {
            if (s.land(b.x0, z) && s.land(b.x0 - 1, z)) {
                fence(c, b.x0, z, z - gz);
            }
        }
        // 정문 기둥과 차단기 (팔은 들어 올림), 경비 초소
        c.fill(dx0 - 1, 0, gz, dx0 - 1, 2, gz, Block.of("light_gray_concrete", 0x7D7D73));
        c.fill(dx1 + 1, 0, gz, dx1 + 1, 2, gz, Block.of("light_gray_concrete", 0x7D7D73));
        c.set(dx1 + 1, 3, gz, Harbor.YELLOW);
        c.fill(dx1 + 1, 4, gz, dx1 + 1, 6, gz, Block.of("end_rod[facing=up]", 0xE8E2D8));
        c.set(dx0 - 1, 2, gz - 1, Blocks.wallSign("birch", "north", "black", false, "", "호송선 선착장", "관계자 외", "출입금지"));
        Harbor.booth(c.v, dx1 + 3 - b.x0, gz + 1 - b.z0, "경비실");
        // 물칸 둘레: 안벽 끝(물에 닿는 땅 칸), 계선주·방충재·노란 선
        for (int z = s.bz0 - 1; z <= s.qz; z++) {
            for (int x = b.x0; x <= b.x1; x++) {
                if (!s.land(x, z)) {
                    continue;
                }
                boolean edge = s.water(x - 1, z) || s.water(x + 1, z) || s.water(x, z + 1) || s.water(x, z - 1);
                if (edge) {
                    c.set(x, -1, z, COPING);
                    c.fill(x, -4, z, x, -2, z, FACE);
                    int k = Math.floorMod(x + z, 6);
                    if (k == 0) {
                        c.set(x, 0, z, Harbor.BOLLARD);
                    } else if (k == 3) {
                        c.fill(x, -3, z, x, -1, z, Harbor.BLACK);
                    }
                } else if (s.water(x - 2, z) || s.water(x + 2, z) || s.water(x, z + 2) || s.water(x, z - 2)) {
                    c.set(x, -1, z, Harbor.YELLOW);
                }
            }
        }
        // 계단식 승선장 (물칸 안쪽 끝 가운데): 물로 내려가는 돌계단
        int sx0 = dx0 + 2, sx1 = dx1 - 2, hz = s.bz0 - 1;
        for (int x = sx0; x <= sx1; x++) {
            c.set(x, 0, hz, AIR);
            c.set(x, -1, hz, AIR);
            c.set(x, -2, hz, Block.of("stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]", 0x7A7979));
            c.set(x, -3, hz, Block.of("stone_bricks", 0x7A7979));
            c.set(x, -1, hz - 1, Block.of("stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x7A7979));
            c.set(x, 0, hz - 1, AIR);
        }
        // 승선 대기 차양 (물칸 동쪽): 기둥 넷, 지붕, 의자
        int ax = s.bx1 + 2, az = s.bz0 - 7;
        for (int[] p : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
            c.fill(ax + p[0], 0, az + p[1], ax + p[0], 2, az + p[1], Block.of("polished_blackstone_wall", 0x353038));
        }
        c.fill(ax, 3, az, ax + 4, 3, az + 4, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        Kit.bench(c.v, ax + 1 - b.x0, 0, az + 3 - b.z0, 3, true, "north");
        c.set(ax + 2, 2, az + 4, Blocks.hangingSign("birch", 0, "black", false, "", "승선 대기", "호송 인원 확인"));
        // 호송 대기실 (동쪽): 문은 서쪽 (차로 마당 쪽)
        int wx0 = b.x1 - 8, wz0 = s.bz0 - 22, wx1 = b.x1, wz1 = s.bz0 - 10;
        waitingRoom(c, wx0, wz0, wx1, wz1, r);
        // 호송 차량 자리: 동쪽 울타리 따라 버스 둘(길게), 승용차 셋
        int pz = gz + 6;
        for (int k = 0; k < 2; k++) {
            int z0 = pz + k * 13;
            box(c, b.x1 - 3, z0, b.x1, z0 + 12, Harbor.LINE);
            c.carSpot(b.x1 - 1.5, 0, z0 + 6.5, 0, -1);
        }
        for (int k = 0; k < 3; k++) {
            int z0 = pz + 26 + k * 3;
            if (z0 + 3 < wz0 - 1) {
                c.fill(b.x1 - 9, -1, z0, b.x1 - 5, -1, z0, Harbor.LINE);
                c.carSpot(b.x1 - 7.0, 0, z0 + 2.0, -1, 0);
            }
        }
        // 차로 보행등
        for (int z = gz + 4; z < s.bz0 - 8; z += 9) {
            c.fill(dx0 - 2, 0, z, dx0 - 2, 3, z, StreetPlan.POST);
            c.set(dx0 - 2, 4, z, LANTERN);
        }
        c.v.connect();
        return c.v;
    }

    /** 호송 대기실 (작은 1층, 문은 서쪽): 벽 의자, 경찰 책상, 철창 대기 칸, 화장실, 등, 이름표 */
    private static void waitingRoom(Canvas c, int x0, int z0, int x1, int z1, Random r) {
        Block wall = Block.of("white_concrete", 0xCFD5D6);
        c.fill(x0, -1, z0, x1, -1, z1, Block.of("polished_andesite", 0x848685));
        c.v.walls(x0 - c.x0, 0, z0 - c.z0, x1 - c.x0, 2, z1 - c.z0, wall);
        c.fill(x0, 3, z0, x1, 3, z1, Block.of("light_gray_concrete", 0x7D7D73));
        c.v.walls(x0 - c.x0, 4, z0 - c.z0, x1 - c.x0, 4, z1 - c.z0, Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E));
        int mz = (z0 + z1) / 2;
        c.set(x0, 0, mz, Blocks.door("spruce", "west", false));
        c.set(x0, 1, mz, Blocks.door("spruce", "west", true));
        for (int z = z0 + 2; z < z1 - 1; z += 3) {
            c.set(x0, 1, z, Harbor.GLASS);
            c.set(x1, 1, z, Harbor.GLASS);
        }
        // 의자 (북쪽 벽), 경찰 책상 (남서쪽), 철창 칸 (북동쪽), 화장실 (남동쪽)
        for (int x = x0 + 1; x <= x1 - 4; x++) {
            c.set(x, 0, z0 + 1, Block.of("spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0x725430));
        }
        c.set(x0 + 2, 0, z1 - 2, Furniture.DESK_TOP);
        c.set(x0 + 2, 1, z1 - 2, Block.of("iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
        c.set(x0 + 2, 0, z1 - 1, Block.of("dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", 0x432B14));
        c.fill(x1 - 3, 0, z0 + 1, x1 - 3, 2, z0 + 3, Harbor.BARS);
        c.fill(x1 - 3, 0, z0 + 3, x1 - 1, 2, z0 + 3, Harbor.BARS);
        c.set(x1 - 2, 0, z0 + 1, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
        c.fill(x1 - 3, 0, z1 - 3, x1 - 1, 2, z1 - 3, Block.of("white_terracotta", 0xD1B2A1));
        c.set(x1 - 3, 0, z1 - 2, Blocks.door("pale_oak", "west", false));
        c.set(x1 - 3, 1, z1 - 2, Blocks.door("pale_oak", "west", true));
        c.set(x1 - 1, 0, z1 - 1, Block.of("quartz_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
        c.set(x1 - 1, 0, z1 - 2, CAULDRON);
        c.set((x0 + x1) / 2, 2, mz, Interior.LIGHT);
        c.set(x1 - 2, 2, z1 - 2, Interior.LIGHT);
        c.set(x0 - 1, 2, mz + 1, Blocks.wallSign("birch", "west", "black", false, "", "호송 대기실"));
    }

    // ------------------------------------------------------------------ 해안 산책로·방파제

    /**
     * 터미널 동쪽 울타리 밖 해안 산책로(길 → 방파제)와 방파제: 항내 쪽 걷는 길(콘크리트, 계선주),
     * 파라펫(2칸 높이 벽), 바다 쪽 테트라포드 더미, 끝에 빨간 등대. 산책로 바다 쪽 난간·보행등·의자.
     */
    static Voxels breakwater(Site s, Random r) {
        R b = s.strip;
        Canvas c = new Canvas(b.x0, b.z0, b.x1, b.z1, -5, 17);
        Block deck = Block.of("light_gray_concrete", 0x7D7D73);
        Block path = Block.of("polished_andesite", 0x848685);
        int walk1 = s.wx0 + 4, parapet = s.wx0 + 5;
        for (int z = b.z0; z <= b.z1; z++) {
            for (int x = b.x0; x <= b.x1; x++) {
                if (!s.land(x, z) || z < s.edge(x)) {
                    continue;
                }
                if (z <= s.qz) {
                    // 해안 산책로
                    c.set(x, -1, z, (x - b.x0) % 5 == 2 ? path : deck);
                    if (s.water(x + 1, z) || (z > s.qz - 2 && s.water(x, z + 1) && (x < s.wx0 || x > s.wx1))) {
                        c.set(x, 0, z, Harbor.BARS);
                        c.fill(x, -4, z, x, -2, z, FACE);
                    }
                    continue;
                }
                // 방파제
                if (x <= walk1) {
                    c.set(x, -1, z, deck);
                    if (x == s.wx0) {
                        c.set(x, -1, z, COPING);
                        c.fill(x, -4, z, x, -2, z, FACE);
                        if (Math.floorMod(z - s.qz, 8) == 4) {
                            c.set(x, 0, z, Harbor.BOLLARD);
                        }
                    }
                } else if (x == parapet) {
                    c.fill(x, -4, z, x, 0, z, FACE);
                    c.set(x, 1, z, COPING);
                } else {
                    long seed = ((long) x * 341873128712L) ^ ((long) z * 132897987541L);
                    int top = 1 - Math.max(0, (x - parapet - 3));
                    Harbor.tetrapod(c.v, x - b.x0, z - b.z0, top, seed);
                }
            }
        }
        // 끝: 남쪽 끝 두 줄도 테트라포드, 그 위 등대
        int lz = s.wz1 - 4, lx = s.wx0 + 2;
        for (int z = s.wz1 - 1; z <= s.wz1; z++) {
            for (int x = s.wx0; x <= s.wx1; x++) {
                Harbor.tetrapod(c.v, x - b.x0, z - b.z0, 0, ((long) x * 7919L) ^ ((long) z * 104729L));
            }
        }
        Harbor.lighthouse(c.v, lx - b.x0, lz - b.z0, "north");
        // 방파제 입구 표지판 (서 있는 표지판), 보행등
        int ez = s.qz - 2;
        c.set(s.wx0, 0, ez, Block.of("oak_sign[rotation=8,waterlogged=false]", 0xA2834F)
                .withText("black", false, "구인천 방파제", "낚시 시", "구명조끼 착용"));
        c.set(walk1, 0, ez, Block.of("oak_sign[rotation=8,waterlogged=false]", 0xA2834F)
                .withText("red", false, "테트라포드", "출입 금지", "추락 위험"));
        for (int z = s.qz + 6; z < lz - 4; z += 10) {
            c.fill(walk1, 0, z, walk1, 2, z, StreetPlan.POST);
            c.set(walk1, 3, z, LANTERN);
        }
        // 산책로 보행등과 의자
        for (int z = s.edge(b.x0) + 6; z < s.qz - 3; z += 12) {
            c.fill(b.x1 - 1, 0, z, b.x1 - 1, 3, z, StreetPlan.POST);
            c.set(b.x1 - 1, 4, z, LANTERN);
            if (z + 5 < s.qz - 3) {
                Kit.bench(c.v, b.x1 - 1 - b.x0, 0, z + 4 - b.z0, 3, false, "east");
            }
        }
        c.v.connect();
        return c.v;
    }

    private PortPlan() {
    }
}
