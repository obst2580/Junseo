package com.junseo.city.vehicle.model;

/**
 * 차 한 종의 모양과 칠. 차 좌표(미터): x 는 차 왼쪽(+, 운전석 쪽), y 는 위, z 는 앞(+). 원점은 차 한가운데 바닥.
 * 차체는 앞뒤로 얇게 썬 조각을 쌓아 만들고({@link CarModelMaker}), 그림은 옆·앞·뒤·위에서 본 그림 넷을 면에 비춰 입힙니다.
 */
public interface CarDesign {
    /** 리소스팩 이름 (예: morning → junseocity:car_morning) */
    String id();

    double length();

    double width();

    /** 바퀴 반지름, 타이어 폭, 좌우 바퀴 중심 사이, 앞·뒤 차축 z */
    double wheelRadius();

    double tireWidth();

    double track();

    double frontAxle();

    double rearAxle();

    /** 바퀴집 반지름 */
    double archRadius();

    /** 위 윤곽 (차체 맨 위 y) */
    double top(double z);

    /** 아래 윤곽 (차체 맨 아래 y, 바퀴집 빼고) */
    double bottom(double z);

    /** 위에서 본 반폭 */
    double halfWidth(double z);

    /** 창 아래선(벨트라인) 높이 */
    double belt(double z);

    /** y 높이에서의 반폭 비율 (위로 갈수록 좁아지는 유리 부분) — {@code layers} 의 경계와 짝 */
    double[][] layers(double z);

    /**
     * 비스듬한 판 (보닛·앞유리·뒷유리 위를 덮어 계단을 가림): {z0, y0, z1, y1, 반폭}. 판 가운데 선이 (z0,y0)-(z1,y1)
     */
    double[][] skins();

    /**
     * 둥근 앞뒤 모서리를 덮는 세운 판 (왼쪽, 오른쪽은 대칭): {za, xa, zb, xb, y0, y1}. 판이 위에서 본 선 (za,xa)-(zb,xb) 를 따라감
     */
    double[][] cornerSkins();

    /** 바닥 판 (바퀴집 안쪽 가림): {반폭, y0, y1, z0, z1} */
    double[] floor();

    /** 사이드미러 {x0, y0, z0, x1, y1, z1} (왼쪽, 오른쪽은 대칭) */
    double[] mirror();

    /** 번호판 가운데 {앞 y, 앞 z, 뒤 y, 뒤 z} */
    double[] plates();

    /** 옆에서 본 그림 (차 왼쪽, z·y) */
    int side(double z, double y);

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
