package com.junseo.city.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarPhysicsTest {
    private static final double MAX = 1.0;
    private static final double ACCEL = 0.02;

    @Test
    void acceleratesUpToMaxSpeed() {
        double speed = 0;
        for (int i = 0; i < 200; i++) {
            speed = CarPhysics.nextSpeed(speed, true, false, false, MAX, ACCEL);
        }
        assertEquals(MAX, speed, 1e-9);
    }

    @Test
    void reverseIsSlowerThanForward() {
        double speed = 0;
        for (int i = 0; i < 500; i++) {
            speed = CarPhysics.nextSpeed(speed, false, true, false, MAX, ACCEL);
        }
        assertEquals(-MAX * CarPhysics.REVERSE_RATIO, speed, 1e-9);
    }

    @Test
    void coastsToAStop() {
        double speed = 0.5;
        for (int i = 0; i < 400; i++) {
            speed = CarPhysics.nextSpeed(speed, false, false, false, MAX, ACCEL);
        }
        assertEquals(0, speed);
    }

    @Test
    void brakeStopsFasterThanCoasting() {
        double braking = 0.8;
        double coasting = 0.8;
        for (int i = 0; i < 10; i++) {
            braking = CarPhysics.nextSpeed(braking, false, false, true, MAX, ACCEL);
            coasting = CarPhysics.nextSpeed(coasting, false, false, false, MAX, ACCEL);
        }
        assertTrue(braking < coasting / 2);
    }

    @Test
    void steeringRightIncreasesYawAndReverseFlipsIt() {
        assertTrue(CarPhysics.nextYaw(0, false, true, 0.5, MAX, 4) > 0);
        assertTrue(CarPhysics.nextYaw(0, true, false, 0.5, MAX, 4) < 0);
        assertTrue(CarPhysics.nextYaw(0, false, true, -0.2, MAX, 4) < 0);
        assertEquals(10f, CarPhysics.nextYaw(10, false, true, 0, MAX, 4));
    }

    @Test
    void forwardMatchesMinecraftYaw() {
        double[] south = CarPhysics.forward(0);
        assertEquals(0, south[0], 1e-9);
        assertEquals(1, south[1], 1e-9);
        double[] west = CarPhysics.forward(90);
        assertEquals(-1, west[0], 1e-9);
        assertEquals(0, west[1], 1e-9);
    }

    @Test
    void wrapsDegrees() {
        assertEquals(-170f, CarPhysics.wrapDegrees(190f), 1e-4);
        assertEquals(170f, CarPhysics.wrapDegrees(-190f), 1e-4);
        assertEquals(72, CarPhysics.kmh(1.0));
    }

    @Test
    void compassPointsTowardTarget() {
        // 남쪽(+z)을 보고 있을 때
        assertEquals("↑", Compass.arrow(0, 0, 10));
        assertEquals("↓", Compass.arrow(0, 0, -10));
        // 남쪽을 보면 오른쪽은 서쪽(-x)
        assertEquals("→", Compass.arrow(0, -10, 0));
        assertEquals("←", Compass.arrow(0, 10, 0));
        assertEquals("↗", Compass.arrow(0, -10, 10));
    }
}
