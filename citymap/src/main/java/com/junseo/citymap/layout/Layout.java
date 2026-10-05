package com.junseo.citymap.layout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * map/layout.json (도시 설계도) 의 내용. 좌표는 블록 단위이고 X 는 동쪽, Z 는 남쪽입니다.
 */
public record Layout(
        int version,
        double[] borderMin,
        double[] borderMax,
        TerrainSettings terrain,
        List<District> districts,
        List<Water> water,
        List<Island> islands,
        List<Road> roads,
        List<Road> bridges,
        List<Hub> hubs,
        List<Mountain> mountains) {

    /**
     * 지형 설정.
     * mountainEdgeFade: 산 구역 테두리에서 산이 제 높이가 될 때까지 들어가는 거리.
     * roadClear / roadFade: 도로 가장자리에서 이 거리까지는 산을 깎고, 이 거리부터는 그대로.
     */
    public record TerrainSettings(int groundY, int waterY, double mountainEdgeFade, double roadClear, double roadFade) {
    }

    public record District(String id, String name, int openPhase, List<double[]> polygon, Ellipse ellipse) {
    }

    public record Ellipse(double cx, double cz, double rx, double rz, double angle) {
    }

    /** kind 가 river 면 line + width, sea 면 polygon. depth 는 가장 깊은 곳의 깊이 (0 이면 기본값) */
    public record Water(String id, String name, String kind, double width, int depth, List<double[]> line, List<double[]> polygon) {
    }

    /** 섬. 원(중심 + 반지름) 또는 타원 */
    public record Island(String id, String name, double cx, double cz, double radius, Ellipse ellipse) {
    }

    /** kind: highway, arterial, tunnel, runway. 다리는 bridge(일반) / bridge-highway */
    public record Road(String id, String name, String kind, List<double[]> line) {
    }

    public record Hub(String id, String name, String district, double x, double z) {
    }

    public record Peak(double x, double z, double height, double radius) {
    }

    public record Flat(String name, double x, double z, double radius, double height) {
    }

    public record Mountain(String id, String name, String district, List<Peak> peaks, List<Flat> flats) {
    }

    public static Layout parse(Reader reader) {
        JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
        JsonObject border = o.getAsJsonObject("world_border");
        JsonObject terrain = o.has("terrain") ? o.getAsJsonObject("terrain") : new JsonObject();

        List<District> districts = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("districts")) {
            JsonObject d = e.getAsJsonObject();
            Ellipse ellipse = d.has("ellipse") ? ellipse(d.getAsJsonObject("ellipse")) : null;
            districts.add(new District(d.get("id").getAsString(), d.get("name").getAsString(),
                    d.get("open_phase").getAsInt(), d.has("polygon") ? points(d.getAsJsonArray("polygon")) : null, ellipse));
        }

        List<Water> water = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("water")) {
            JsonObject w = e.getAsJsonObject();
            water.add(new Water(w.get("id").getAsString(), w.get("name").getAsString(), w.get("kind").getAsString(),
                    w.has("width") ? w.get("width").getAsDouble() : 0,
                    w.has("depth") ? w.get("depth").getAsInt() : 0,
                    w.has("line") ? points(w.getAsJsonArray("line")) : null,
                    w.has("polygon") ? points(w.getAsJsonArray("polygon")) : null));
        }

        List<Island> islands = new ArrayList<>();
        if (o.has("islands")) {
            for (JsonElement e : o.getAsJsonArray("islands")) {
                JsonObject i = e.getAsJsonObject();
                if (i.has("ellipse")) {
                    Ellipse el = ellipse(i.getAsJsonObject("ellipse"));
                    islands.add(new Island(i.get("id").getAsString(), i.get("name").getAsString(), el.cx(), el.cz(),
                            Math.max(el.rx(), el.rz()), el));
                } else {
                    double[] c = point(i.getAsJsonArray("center"));
                    islands.add(new Island(i.get("id").getAsString(), i.get("name").getAsString(), c[0], c[1],
                            i.get("radius").getAsDouble(), null));
                }
            }
        }

        List<Road> roads = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("roads")) {
            JsonObject r = e.getAsJsonObject();
            roads.add(new Road(r.get("id").getAsString(), r.get("name").getAsString(), r.get("kind").getAsString(),
                    points(r.getAsJsonArray("line"))));
        }

        List<Road> bridges = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("bridges")) {
            JsonObject r = e.getAsJsonObject();
            String id = r.get("id").getAsString();
            // 고속도로 교량은 HB 로 시작합니다
            String kind = r.has("kind") ? r.get("kind").getAsString() : (id.startsWith("HB") ? "bridge-highway" : "bridge");
            bridges.add(new Road(id, r.get("name").getAsString(), kind, points(r.getAsJsonArray("line"))));
        }

        List<Hub> hubs = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("hubs")) {
            JsonObject h = e.getAsJsonObject();
            double[] p = point(h.getAsJsonArray("pos"));
            hubs.add(new Hub(h.get("id").getAsString(), h.get("name").getAsString(), h.get("district").getAsString(), p[0], p[1]));
        }

        List<Mountain> mountains = new ArrayList<>();
        if (o.has("mountains")) {
            for (JsonElement e : o.getAsJsonArray("mountains")) {
                JsonObject m = e.getAsJsonObject();
                List<Peak> peaks = new ArrayList<>();
                for (JsonElement pe : m.getAsJsonArray("peaks")) {
                    JsonObject p = pe.getAsJsonObject();
                    double[] c = point(p.getAsJsonArray("pos"));
                    peaks.add(new Peak(c[0], c[1], p.get("height").getAsDouble(), p.get("radius").getAsDouble()));
                }
                List<Flat> flats = new ArrayList<>();
                if (m.has("flats")) {
                    for (JsonElement fe : m.getAsJsonArray("flats")) {
                        JsonObject f = fe.getAsJsonObject();
                        double[] c = point(f.getAsJsonArray("pos"));
                        flats.add(new Flat(f.has("name") ? f.get("name").getAsString() : "", c[0], c[1],
                                f.get("radius").getAsDouble(), f.get("height").getAsDouble()));
                    }
                }
                mountains.add(new Mountain(m.get("id").getAsString(), m.get("name").getAsString(),
                        m.get("district").getAsString(), peaks, flats));
            }
        }

        TerrainSettings settings = new TerrainSettings(
                terrain.has("ground_y") ? terrain.get("ground_y").getAsInt() : 0,
                terrain.has("water_y") ? terrain.get("water_y").getAsInt() : -1,
                terrain.has("mountain_edge_fade") ? terrain.get("mountain_edge_fade").getAsDouble() : 220,
                terrain.has("road_clear") ? terrain.get("road_clear").getAsDouble() : 30,
                terrain.has("road_fade") ? terrain.get("road_fade").getAsDouble() : 150);
        return new Layout(o.get("version").getAsInt(), point(border.getAsJsonArray("min")), point(border.getAsJsonArray("max")),
                settings, districts, water, islands, roads, bridges, hubs, mountains);
    }

    private static Ellipse ellipse(JsonObject el) {
        double[] c = point(el.getAsJsonArray("center"));
        return new Ellipse(c[0], c[1], el.get("rx").getAsDouble(), el.get("rz").getAsDouble(),
                el.has("angle") ? el.get("angle").getAsDouble() : 0);
    }

    private static double[] point(JsonArray a) {
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()};
    }

    private static List<double[]> points(JsonArray a) {
        List<double[]> out = new ArrayList<>(a.size());
        for (JsonElement e : a) {
            out.add(point(e.getAsJsonArray()));
        }
        return out;
    }
}
