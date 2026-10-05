package com.junseo.citymap.buildings;

import com.junseo.citymap.layout.Layout;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 건물 배치 검사: 구역별로 지어졌는지, 길·거점을 막지 않는지, 서로 겹치지 않는지, 블록 이름이 맞는지 */
class BuildingsTest {
    static CityTerrain terrain;
    static CityBuildings buildings;

    @BeforeAll
    static void setUp() {
        terrain = TestCity.terrain();
        buildings = TestCity.buildings();
    }

    static Map<String, Integer> kinds() {
        Map<String, Integer> n = new TreeMap<>();
        for (Placement p : buildings.placements()) {
            n.merge(p.kind, 1, Integer::sum);
        }
        return n;
    }

    @Test
    void everyDistrictGetsItsBuildings() {
        Map<String, Integer> n = kinds();
        System.out.println("건물 종류별 수: " + n);
        assertEquals(1, n.getOrDefault("terminal", 0), "공항 터미널");
        assertEquals(1, n.getOrDefault("tower", 0), "관제탑");
        assertEquals(3, n.getOrDefault("plane", 0), "여객기");
        assertEquals(2, n.getOrDefault("gate", 0), "홍살문");
        assertTrue(n.getOrDefault("statue", 0) >= 13, "무인석 12 + 지구본");
        assertEquals(1, n.getOrDefault("dome", 0), "청라돔");
        assertEquals(1, n.getOrDefault("market", 0), "대형시장 본관");
        assertTrue(n.getOrDefault("factory", 0) >= 6, "구인천 공장");
        assertTrue(n.getOrDefault("house", 0) >= 100, "구인천 다세대");
        assertTrue(n.getOrDefault("shop", 0) >= 60, "시장 상가");
        assertEquals(1, n.getOrDefault("police", 0), "강남경찰서 (경찰서는 한 곳)");
        assertEquals(2, n.getOrDefault("hotel", 0), "이태원 호텔, 공항 호텔");
        assertTrue(n.getOrDefault("restaurant", 0) >= 8, "이태원 거리 건물");
        assertTrue(n.getOrDefault("nightlife", 0) >= 10, "홍대 클럽 거리 건물");
        assertEquals(1, n.getOrDefault("hospital", 0), "대학병원");
        assertTrue(n.getOrDefault("campus", 0) >= 8, "서울대학교 정문·건물·기숙사");
        assertEquals(1, n.getOrDefault("casino", 0), "카지노 호텔");
        assertTrue(n.getOrDefault("park", 0) >= 3, "대공원 정문·화장실·매점");
        assertEquals(1, n.getOrDefault("landmark", 0), "남산타워");
        assertEquals(7, n.getOrDefault("prison", 0), "교도소 (담·본관·의료동·수용동 둘·노역장·식당)");
        assertEquals(3, n.getOrDefault("quarry", 0), "북한산 채석장 + 진입로 두 구간");
        assertEquals(1, n.getOrDefault("bank", 0), "63빌딩 (은행 본점)");
        assertEquals(2, n.getOrDefault("studio", 0), "방송국 본관·스튜디오");
        assertEquals(2, n.getOrDefault("cityhall", 0), "시청 옛·새 청사");
        assertEquals(1, n.getOrDefault("depot", 0), "시청 차고지 (환경미화)");
        assertEquals(3, n.getOrDefault("station", 0), "서울역 새·옛 역사, 승강장");
        assertEquals(1, n.getOrDefault("jewelry", 0), "명동 보석상");
        assertEquals(1, n.getOrDefault("cafe", 0), "명동 카페");
        assertTrue(n.getOrDefault("retail", 0) >= 8, "명동 상가");
        assertTrue(n.getOrDefault("site", 0) >= 1, "송파 테마파크 건설 예정 부지");
        assertTrue(n.getOrDefault("skyscraper", 0) >= 1, "송파 준서월드타워");
        assertTrue(n.getOrDefault("ballpark", 0) >= 1, "잠실 야구장");
        assertTrue(n.getOrDefault("stadium", 0) >= 1, "서울월드컵경기장");
        long hill = buildings.placements().stream().filter(p -> p.name.endsWith(" 골목·계단길") || p.name.endsWith(" 골목·석축")).count();
        assertTrue(hill >= 6, "비탈 마을 (해방촌·후암동·보광동·평창동) " + hill);
        assertTrue(buildings.placements().stream().anyMatch(p -> p.name.equals("북한산 광장")), "북한산 등산로 입구");
        long hangang = buildings.placements().stream().filter(p -> p.name.startsWith("한강공원")).count();
        assertTrue(hangang >= 40, "한강공원 토막·편의점·화장실·주차장 " + hangang);
        assertTrue(n.getOrDefault("garage", 0) >= 2, "등산로 공영주차장 + 한강공원 주차장");
        assertEquals(3, n.getOrDefault("port", 0), "구인천 항만 터미널 바닥·정문·하역 사무소");
        assertEquals(2, n.getOrDefault("crane", 0), "안벽 크레인 2대");
        assertEquals(5, n.getOrDefault("container", 0), "컨테이너 장치장 블록");
        assertEquals(2, n.getOrDefault("pier", 0), "호송선 선착장, 방파제");
        assertEquals(1, n.getOrDefault("mudflat", 0), "구인천 갯벌 체험장");
        assertEquals(2, n.getOrDefault("lumber", 0), "목재 집하장, 북한산 벌채 구역");
        assertEquals(3, n.getOrDefault("farm", 0), "청라 농지, 농산물 공판장, 축산 농장 마당");
        assertEquals(5, n.getOrDefault("barn", 0), "우사·착유실·돈사·계사·농장 사무실");
    }

    /** 칸 (x, z) 에 이 건물이 땅 위로 놓는 블록 높이들 (건물 기준 y, 공기 제외) */
    static List<Integer> solidsAt(Placement p, int x, int z) {
        List<Integer> ys = new ArrayList<>();
        int base = terrain.groundY() + 1;
        p.column(x, z, base, Integer.MIN_VALUE, (y, b) -> {
            if (!b.isAir()) {
                ys.add(y - base);
            }
        });
        return ys;
    }

    /** 길·인도 위에 일부러 놓는 것 (거리 시설, 포장, 거점 표시 걷기) */
    static boolean streetLayer(Placement p) {
        return p.kind.equals("street") || p.kind.equals("pave") || p.kind.equals("hubcover");
    }

    @Test
    void buildingsDoNotBlockRoadsOrWater() {
        List<String> bad = new ArrayList<>();
        for (Placement p : buildings.placements()) {
            if (streetLayer(p)) {
                continue;
            }
            double[] b = p.bounds();
            int hits = 0;
            for (int z = (int) Math.floor(b[1]); z <= (int) Math.ceil(b[3]); z++) {
                for (int x = (int) Math.floor(b[0]); x <= (int) Math.ceil(b[2]); x++) {
                    Column c = terrain.column(x, z);
                    if (!(c.isRoad() || c.isWater() || c.deck)) {
                        continue;
                    }
                    for (int y : solidsAt(p, x, z)) {
                        if (y >= 0 && y < CityBuildings.ROAD_CLEARANCE) {
                            hits++;
                            break;
                        }
                    }
                }
            }
            if (hits > 0) {
                bad.add(p + " 도로·물 위 " + hits + "칸");
            }
        }
        assertTrue(bad.isEmpty(), "길이나 물 위에 낮게 놓인 건물: " + bad);
    }

    @Test
    void hubsStayOpen() {
        List<String> bad = new ArrayList<>();
        for (Layout.Hub h : terrain.layout().hubs()) {
            // 거점 바닥 5×5 와 스폰 자리(+2) 위 3칸은 비어 있어야 함
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int x = (int) Math.floor(h.x()) + dx, z = (int) Math.floor(h.z()) + dz;
                    Column c = terrain.column(x, z);
                    List<Integer> ys = new ArrayList<>();
                    buildings.column(x, z, c, (y, b) -> {
                        if (!b.isAir()) {
                            ys.add(y - terrain.groundY() - 1);
                        }
                    });
                    if (ys.stream().anyMatch(y -> y >= 0 && y < 3)) {
                        bad.add(h.id() + " (" + x + ", " + z + ")");
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "거점 자리를 막은 건물: " + bad);
    }

    @Test
    void buildingsDoNotOverlap() {
        Map<Long, Integer> owner = new HashMap<>();
        Set<String> bad = new HashSet<>();
        List<Placement> all = buildings.placements();
        for (int k = 0; k < all.size(); k++) {
            Placement p = all.get(k);
            if (streetLayer(p)) {
                continue;
            }
            double[] b = p.bounds();
            for (int z = (int) Math.floor(b[1]); z <= (int) Math.ceil(b[3]); z++) {
                for (int x = (int) Math.floor(b[0]); x <= (int) Math.ceil(b[2]); x++) {
                    boolean low = solidsAt(p, x, z).stream().anyMatch(y -> y >= 0 && y <= 3);
                    if (!low) {
                        continue;
                    }
                    Integer prev = owner.put(((long) x << 32) ^ (z & 0xffffffffL), k);
                    if (prev != null && prev != k) {
                        bad.add(all.get(prev) + " ↔ " + p);
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "겹친 건물 " + bad.size() + "쌍: " + bad.stream().limit(10).toList());
    }

    /** 가로수·가로등·전봇대·정류장은 인도에만 서고, 차도 위로는 높은 곳(나뭇가지·가로등 팔)만 지나감 */
    @Test
    void streetFurnitureStaysOnSidewalks() {
        List<String> bad = new ArrayList<>();
        int items = 0;
        int base = terrain.groundY() + 1;
        for (Placement p : buildings.placements()) {
            if (!p.kind.equals("street")) {
                continue;
            }
            items++;
            double[] b = p.bounds();
            for (int z = (int) Math.floor(b[1]); z <= (int) Math.ceil(b[3]); z++) {
                for (int x = (int) Math.floor(b[0]); x <= (int) Math.ceil(b[2]); x++) {
                    Column c = terrain.column(x, z);
                    boolean carriage = (c.isRoad() && c.surface != com.junseo.citymap.terrain.Surface.SIDEWALK) || c.deck || c.isWater();
                    if (!carriage) {
                        continue;
                    }
                    List<Integer> low = new ArrayList<>();
                    buildings.column(x, z, c, (y, blk) -> {
                        if (!blk.isAir() && y - base < CityBuildings.STREET_CLEARANCE) {
                            low.add(y);
                        }
                    });
                    if (!low.isEmpty()) {
                        bad.add(p + " (" + x + ", " + z + ")");
                    }
                }
            }
        }
        assertTrue(items > 300, "거리 시설 " + items);
        assertTrue(bad.isEmpty(), "차도 위에 낮게 놓인 거리 시설: " + bad.stream().limit(10).toList());
    }

    /** 표지판 글씨가 한 줄에 들어가는지 (한 줄 폭 90픽셀: 한글 약 9, 영문·숫자 약 6) */
    @Test
    void signTextFitsOnSigns() {
        List<String> bad = new ArrayList<>();
        int signs = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Placement p : buildings.placements()) {
            if (!seen.add(p.kind + p.name) && !p.kind.equals("house") && !p.kind.equals("shop")) {
                continue;
            }
            for (Placement.SignSpot s : p.signs(terrain.groundY() + 1)) {
                signs++;
                String[] lines = s.block().textLines();
                if (lines.length > 4) {
                    bad.add(p.name + ": " + lines.length + "줄");
                }
                for (String line : lines) {
                    int px = 0;
                    for (char ch : line.toCharArray()) {
                        px += ch >= 0xAC00 && ch <= 0xD7A3 ? 9 : 6;
                    }
                    if (px > 90) {
                        bad.add(p.name + ": " + line);
                    }
                }
            }
        }
        assertTrue(signs > 300, "표지판 " + signs);
        assertTrue(bad.isEmpty(), "표지판에 안 들어가는 글씨: " + bad);
    }

    @Test
    void sameLayoutGivesSameCity() {
        CityBuildings again = CityBuildings.plan(terrain);
        assertEquals(buildings.placements().size(), again.placements().size());
        for (int k = 0; k < again.placements().size(); k += 7) {
            Placement a = buildings.placements().get(k), b = again.placements().get(k);
            assertEquals(a.toString(), b.toString());
            assertEquals(digest(a), digest(b), a.toString());
        }
    }

    private static long digest(Placement p) {
        Voxels v = p.voxels();
        long h = 17;
        for (int y = v.y0; y < v.y0 + v.h; y++) {
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    h = h * 31 + (b == null ? 0 : b.data().hashCode());
                }
            }
        }
        return h;
    }

    @Test
    void allBlocksExistInMinecraft262() throws IOException {
        Set<String> known = new HashSet<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                BuildingsTest.class.getResourceAsStream("/block-ids-26.2.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    known.add("minecraft:" + line.trim());
                }
            }
        }
        assertTrue(known.size() > 1000);
        Set<String> unknown = new TreeSet<>();
        Set<String> seen = new HashSet<>();
        for (Placement p : buildings.placements()) {
            if (!seen.add(p.kind + p.name) && !p.kind.equals("statue")) {
                continue; // 같은 종류는 한 번만 (시간 절약)
            }
            Voxels v = p.voxels();
            for (Block b : v.palette()) {
                if (!known.contains(b.id())) {
                    unknown.add(b.id() + " (" + p.name + ")");
                }
            }
        }
        // 같은 종류라도 무작위 재료가 다를 수 있어서 상수 목록도 확인
        for (java.lang.reflect.Field f : Blocks.class.getFields()) {
            if (f.getType() == Block.class) {
                try {
                    Block b = (Block) f.get(null);
                    if (!known.contains(b.id())) {
                        unknown.add(b.id() + " (Blocks." + f.getName() + ")");
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        for (String c : Blocks.COLORS) {
            for (Block b : List.of(Blocks.concrete(c), Blocks.wool(c), Blocks.terracotta(c), Blocks.glass(c))) {
                if (!known.contains(b.id())) {
                    unknown.add(b.id());
                }
            }
        }
        assertTrue(unknown.isEmpty(), "26.2 에 없는 블록: " + unknown);
    }

    @Test
    void spawnHasATerminalInFront() {
        Layout.Hub spawn = terrain.layout().hubs().stream().filter(h -> h.id().equals("spawn")).findFirst().orElseThrow();
        Placement terminal = buildings.placements().stream().filter(p -> p.kind.equals("terminal")).findFirst().orElseThrow();
        // 스폰에서 30칸 안에 터미널 건물이 있어야 함
        boolean near = false;
        for (int dz = -30; dz <= 30 && !near; dz++) {
            for (int dx = -30; dx <= 30 && !near; dx++) {
                near = dx * dx + dz * dz <= 900 && terminal.covers(spawn.x() + dx + 0.5, spawn.z() + dz + 0.5);
            }
        }
        assertTrue(near, "스폰 바로 앞에 터미널");
        assertNotNull(buildings.at(spawn.x(), spawn.z() - 25));
    }

    @Test
    void rotatedBlockStatesFollowTheBuilding() {
        assertEquals("minecraft:oak_door[facing=west,half=lower]",
                Block.of("oak_door[facing=south,half=lower]", 0).rotate(1).data());
        assertEquals("minecraft:glass_pane[east=true,south=false,west=true,north=false]",
                Block.of("glass_pane[north=true,east=false,south=true,west=false]", 0).rotate(1).data());
        assertEquals("minecraft:oak_log[axis=z]", Block.of("oak_log[axis=x]", 0).rotate(3).data());
    }
}
