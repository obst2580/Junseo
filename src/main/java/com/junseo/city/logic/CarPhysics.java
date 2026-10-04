package com.junseo.city.logic;

/** 자동차 속도/방향 계산 (블록/틱 단위). */
public final class CarPhysics {
    /** 아무 키도 안 누를 때 매 틱 속도에 곱해지는 값. */
    static final double COAST_DRAG = 0.97;
    /** 스페이스바(브레이크) 를 누를 때 매 틱 속도에 곱해지는 값. */
    static final double BRAKE = 0.82;
    /** 후진 최고 속도 = 최고 속도 × 이 값. */
    static final double REVERSE_RATIO = 0.35;

    private CarPhysics() {
    }

    public static double nextSpeed(double speed, boolean forward, boolean backward, boolean brake,
                                   double maxSpeed, double accel) {
        if (brake) {
            speed *= BRAKE;
        }
        if (forward && !backward) {
            speed += speed < 0 ? accel * 3 : accel;
        } else if (backward && !forward) {
            speed -= speed > 0 ? accel * 3 : accel * 0.6;
        } else {
            speed *= COAST_DRAG;
            if (Math.abs(speed) < 0.003) {
                speed = 0;
            }
        }
        return Math.max(-maxSpeed * REVERSE_RATIO, Math.min(maxSpeed, speed));
    }

    /**
     * 좌우 키로 방향을 바꿉니다. 마인크래프트의 yaw 는 오른쪽으로 돌면 커집니다.
     * 후진할 때는 실제 자동차처럼 반대로 돕니다.
     */
    public static float nextYaw(float yaw, boolean left, boolean right, double speed, double maxSpeed, double turnDegrees) {
        if (left == right || Math.abs(speed) < 0.01) {
            return yaw;
        }
        double factor = Math.max(0.35, Math.min(1.0, Math.abs(speed) / maxSpeed * 1.6)) * Math.signum(speed);
        double delta = turnDegrees * factor;
        return wrapDegrees((float) (right ? yaw + delta : yaw - delta));
    }

    /** yaw 방향의 수평 단위 벡터 {x, z}. */
    public static double[] forward(float yaw) {
        double rad = Math.toRadians(yaw);
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    public static int kmh(double blocksPerTick) {
        return (int) Math.round(Math.abs(blocksPerTick) * 20 * 3.6);
    }

    public static float wrapDegrees(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }
}
