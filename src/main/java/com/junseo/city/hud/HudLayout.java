package com.junseo.city.hud;

import com.junseo.city.map.RadarHud;

/** 액션바 미니맵의 화면 위치 계산 (GUI 칸, 화면 가운데 기준) */
public final class HudLayout {
    /** 핫바(폭 182) 가장자리에서 미니맵까지 (왼손 칸 자리 비움) */
    private static final int GAP = 33;
    private static final int HOTBAR_HALF = 91;

    public enum Side {
        LEFT, RIGHT
    }

    /** 미니맵 왼쪽 끝의 화면 위치 */
    public static int radarLeft(Side side) {
        return side == Side.LEFT ? -(HOTBAR_HALF + GAP + RadarHud.W) : HOTBAR_HALF + GAP;
    }

    /**
     * 글자열 맨 앞에서 미니맵 왼쪽 끝까지 띄울 칸 수.
     * 클라이언트는 액션바 글자열을 x = −(폭/2) 에서 그리기 시작합니다 (폭은 올림한 정수, 나눗셈은 버림).
     */
    public static int radarLead(float textWidth, Side side) {
        int total = (int) Math.ceil(textWidth);
        int start = -(total / 2);
        return radarLeft(side) - start;
    }

    private HudLayout() {
    }
}
