package com.junseo.city.vehicle.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 마인크래프트 아이템 모델 (Blockbench 의 Java 모델과 같은 형식): 상자(element) 목록과 면마다 그림 자리(uv).
 * 좌표는 모델 단위(0..16 이 아이템 한 칸, -16..32 까지 허용), uv 는 그림 전체가 0..16.
 */
public final class ItemModel {
    /** 면 방향 */
    public enum Dir { NORTH, SOUTH, EAST, WEST, UP, DOWN }

    /** 면 하나: 그림 위 (u1, v1) 이 밖에서 본 면의 왼쪽 위, (u2, v2) 가 오른쪽 아래 */
    public record Face(double u1, double v1, double u2, double v2, String texture) {
    }

    /** 상자 하나. axis 가 null 이면 돌리지 않음 (angle 은 -45, -22.5, 0, 22.5, 45 중 하나) */
    public record Element(double[] from, double[] to, String axis, double angle, double[] origin, Map<Dir, Face> faces) {
    }

    public final Map<String, String> textures = new LinkedHashMap<>();
    public final List<Element> elements = new ArrayList<>();

    public void add(double[] from, double[] to, Map<Dir, Face> faces) {
        elements.add(new Element(from, to, null, 0, null, faces));
    }

    public void add(double[] from, double[] to, String axis, double angle, double[] origin, Map<Dir, Face> faces) {
        elements.add(new Element(from, to, axis, angle, origin, faces));
    }

    public String json() {
        StringBuilder s = new StringBuilder("{\"textures\":{");
        boolean first = true;
        for (Map.Entry<String, String> t : textures.entrySet()) {
            s.append(first ? "" : ",").append('"').append(t.getKey()).append("\":\"").append(t.getValue()).append('"');
            first = false;
        }
        s.append("},\"elements\":[");
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            s.append(i == 0 ? "\n" : ",\n").append("{\"from\":").append(vec(e.from)).append(",\"to\":").append(vec(e.to));
            if (e.axis != null) {
                s.append(",\"rotation\":{\"angle\":").append(num(e.angle)).append(",\"axis\":\"").append(e.axis)
                        .append("\",\"origin\":").append(vec(e.origin)).append('}');
            }
            s.append(",\"faces\":{");
            boolean f1 = true;
            for (Map.Entry<Dir, Face> f : e.faces.entrySet()) {
                Face face = f.getValue();
                s.append(f1 ? "" : ",").append('"').append(f.getKey().name().toLowerCase(Locale.ROOT)).append("\":{\"uv\":[")
                        .append(num(face.u1)).append(',').append(num(face.v1)).append(',').append(num(face.u2)).append(',')
                        .append(num(face.v2)).append("],\"texture\":\"#").append(face.texture).append("\"}");
                f1 = false;
            }
            s.append("}}");
        }
        s.append("\n]}\n");
        return s.toString();
    }

    private static String vec(double[] v) {
        return "[" + num(v[0]) + "," + num(v[1]) + "," + num(v[2]) + "]";
    }

    static String num(double v) {
        double r = Math.round(v * 10000) / 10000.0;
        if (r == Math.rint(r)) {
            return Long.toString((long) r);
        }
        return Double.toString(r);
    }
}
