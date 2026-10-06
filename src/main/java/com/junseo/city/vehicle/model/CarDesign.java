package com.junseo.city.vehicle.model;

import java.util.List;

/**
 * 차 한 종의 모양과 칠. 차 좌표(미터): x 는 차 왼쪽(+, 운전석 쪽), y 는 위, z 는 앞(+). 원점은 차 한가운데 바닥.
 * 차체는 앞뒤로 얇게 썬 조각을 쌓아 만들고({@link CarModelMaker}), 그림은 옆·앞·뒤·위에서 본 그림 넷을 면에 비춰 입힙니다.
 */
public interface CarDesign {
    /** 덧붙이는 상자 (경광등, 택시 표시등 등): 차 좌표 상자, 한 색. sign 이 있으면 앞·뒤·옆면에 그 그림 (u, v: 0..1) */
    record Extra(double x0, double y0, double z0, double x1, double y1, double z1, int color, Tex.Shader sign) {
        public Extra(double x0, double y0, double z0, double x1, double y1, double z1, int color) {
            this(x0, y0, z0, x1, y1, z1, color, null);
        }
    }

    /** 리소스팩 이름 (예: morning → junseocity:car_morning) */
    String id();

    double length();

    double width();

    /** 가장 높은 곳 (그림 크기를 정할 때) */
    double height();

    /** 모델 단위: 1 미터 = 몇 칸. 모델은 -16..32 칸 안에 들어가야 해서 긴 차는 줄임 */
    default double unit() {
        return length() / 2 * 8 + 8 <= 31 ? 8 : Math.floor(23 / (length() / 2));
    }

    /** 바퀴 반지름, 타이어 폭, 좌우 바퀴 중심 사이, 앞·뒤 차축 z */
    double wheelRadius();

    double tireWidth();

    double track();

    double frontAxle();

    double rearAxle();

    /** 바퀴집 반지름 */
    double archRadius();

    /** 조각 하나(z)의 층들 {y0, y1, 반폭}. 층 수는 z 와 상관없이 같아야 함 (빈 층은 y1 <= y0) */
    double[][] layers(double z);

    /** 비스듬한 판 (보닛·앞유리·뒷유리 위를 덮어 계단을 가림): {z0, y0, z1, y1, 반폭}. 판 가운데 선이 (z0,y0)-(z1,y1) */
    double[][] skins();

    /** 둥근 앞뒤 모서리를 덮는 세운 판 (왼쪽, 오른쪽은 대칭): {za, xa, zb, xb, y0, y1} */
    double[][] cornerSkins();

    /** 바닥 판 (바퀴집 안쪽 가림): {반폭, y0, y1, z0, z1} */
    double[] floor();

    /** 사이드미러 {x0, y0, z0, x1, y1, z1} (왼쪽, 오른쪽은 대칭) */
    double[] mirror();

    /** 번호판 가운데 {앞 y, 앞 z, 뒤 y, 뒤 z} */
    double[] plates();

    /** 덧붙이는 상자들 */
    List<Extra> extras();

    /** 옆에서 본 그림 (차 왼쪽, z·y) */
    int side(double z, double y);

    /** 차 오른쪽 옆 그림 (글씨처럼 방향이 있는 것만 다름) */
    default int sideRight(double z, double y) {
        return side(z, y);
    }

    /** 앞에서 본 그림 (x·y) */
    int front(double x, double y);

    /** 뒤에서 본 그림 (x·y) */
    int rear(double x, double y);

    /** 위에서 본 그림 (x·z) */
    int top(double x, double z);

    /** 바퀴 옆면 그림 (중심에서 r 미터, 각도 th), 바깥은 투명 */
    int wheel(double r, double th);

    /** 타이어 바닥 그림 (u: 폭 0..1, v: 둘레 0..1) */
    int tread(double u, double v);

    int paint();

    int glass();

    int trim();

    int underbody();
}
