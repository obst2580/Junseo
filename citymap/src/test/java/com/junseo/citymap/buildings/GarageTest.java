package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 공영 차고와 차 꺼내는 자리 */
class GarageTest {
    /** 차 자리마다: 바퀴 아래는 바닥, 차가 서는 두 칸은 비어 있음 */
    private static void assertSpotsClear(Voxels v, String what) {
        assertFalse(v.carSpots().isEmpty(), what + ": 차 자리가 없어요");
        for (double[] s : v.carSpots()) {
            int i = (int) Math.floor(s[0]), y = (int) s[1], j = (int) Math.floor(s[2]);
            Block floor = v.get(i, y - 1, j);
            assertTrue(floor != null && !floor.isAir(), what + ": 차 자리 (" + i + ", " + y + ", " + j + ") 아래가 비었어요");
            for (int k = 0; k < 2; k++) {
                Block b = v.get(i, y + k, j);
                assertTrue(b == null || b.isAir(), what + ": 차 자리 (" + i + ", " + (y + k) + ", " + j + ") 가 막혔어요: " + b);
            }
        }
    }

    @Test
    void parkingLotsMarkEveryStall() {
        for (int w = 10; w <= 50; w += 7) {
            for (int d = 9; d <= 50; d += 6) {
                Voxels v = ParkingLot.build(w, d, new Random(w * 31L + d));
                assertSpotsClear(v, "주차장 " + w + "×" + d);
            }
        }
        assertSpotsClear(Garage.lot(20, 24, "시험", new Random(1)), "공영주차장");
        for (int levels = 2; levels <= 6; levels++) {
            assertSpotsClear(Garage.tower(levels, "시험", new Random(levels)), "주차타워 " + levels + "단");
        }
    }

    @Test
    void everyFilledDistrictHasAGarage() {
        CityBuildings b = TestCity.buildings();
        List<Placement> garages = b.garages();
        for (String id : DistrictFill.DISTRICTS) {
            double[] c = b.terrain().districtCenter(id);
            String name = b.terrain().districtName((int) c[0], (int) c[1]);
            boolean any = garages.stream().anyMatch(p -> {
                double[] bb = p.bounds();
                String dn = b.terrain().districtName((int) ((bb[0] + bb[2]) / 2), (int) ((bb[1] + bb[3]) / 2));
                return dn != null && dn.equals(name);
            });
            assertTrue(any, id + " 에 공영 차고가 없어요");
        }
        for (Placement p : garages) {
            assertSpotsClear(p.voxels(), p.toString());
        }
    }

    @Test
    void carSpotsNearAGarageAreFoundClosestFirst() {
        CityBuildings b = TestCity.buildings();
        Placement g = b.garages().get(0);
        double[] bb = g.bounds();
        double x = (bb[0] + bb[2]) / 2, z = (bb[1] + bb[3]) / 2;
        List<Placement.CarSpot> spots = b.carSpots(x, z, 30);
        assertFalse(spots.isEmpty());
        for (int k = 1; k < spots.size(); k++) {
            double d0 = Math.hypot(spots.get(k - 1).x() - x, spots.get(k - 1).z() - z);
            double d1 = Math.hypot(spots.get(k).x() - x, spots.get(k).z() - z);
            assertTrue(d0 <= d1 + 1e-9);
        }
        assertEquals(b.terrain().groundY() + 1, spots.get(0).y(), "땅 위 주차장은 땅 바로 위");
    }
}
