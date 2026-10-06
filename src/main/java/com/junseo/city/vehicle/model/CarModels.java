package com.junseo.city.vehicle.model;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static com.junseo.city.vehicle.model.Tex.rgb;

/**
 * 리소스팩에 넣는 차 모델 목록. 처음 부를 때 한 번 만들고 그대로 씁니다.
 * <ul>
 *   <li>일반 차: 색마다 그림만 다르고 모양은 같음. 아이템 모델 junseocity:car_&lt;id&gt;_&lt;색&gt;</li>
 *   <li>직업 차: 정해진 도색 하나. junseocity:car_&lt;id&gt;</li>
 *   <li>바퀴: junseocity:car_&lt;id&gt;_wheel</li>
 * </ul>
 */
public final class CarModels {
    /** 일반 차 색 */
    public enum Paint {
        WHITE("흰색", rgb(236, 236, 229)),
        BLACK("검정", rgb(30, 30, 32)),
        SILVER("은색", rgb(180, 184, 188)),
        GRAY("회색", rgb(96, 100, 104)),
        NAVY("남색", rgb(34, 46, 84)),
        RED("빨강", rgb(170, 28, 34));

        public final String label;
        public final int rgb;

        Paint(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Paint byId(String id) {
            for (Paint p : values()) {
                if (p.id().equalsIgnoreCase(id)) {
                    return p;
                }
            }
            return WHITE;
        }
    }

    private static final Map<String, IntFunction<CarDesign>> CIVILIAN = new LinkedHashMap<>();
    private static final Map<String, Supplier<CarDesign>> JOB = new LinkedHashMap<>();
    private static final Map<String, CarDesign> SHAPES = new LinkedHashMap<>();
    private static Map<String, byte[]> files;

    static {
        CIVILIAN.put("morning", Designs::morning);
        CIVILIAN.put("sedan", Designs::sedan);
        CIVILIAN.put("grandeur", Designs::grandeur);
        CIVILIAN.put("suv", Designs::suv);
        CIVILIAN.put("sports", Designs::sports);
        JOB.put("police", Designs::police);
        JOB.put("taxi", Designs::taxi);
        JOB.put("ambulance", Designs::ambulance);
        JOB.put("cashvan", Designs::cashVan);
        JOB.put("delivery", TruckDesign::delivery);
        JOB.put("garbage", TruckDesign::garbage);
    }

    private CarModels() {
    }

    /** 모양(크기·바퀴·번호판 자리)을 알려 주는 대표 설계 */
    public static synchronized CarDesign design(String id) {
        return SHAPES.computeIfAbsent(id, k -> CIVILIAN.containsKey(k) ? CIVILIAN.get(k).apply(Paint.WHITE.rgb) : JOB.get(k).get());
    }

    public static boolean hasPaints(String id) {
        return CIVILIAN.containsKey(id);
    }

    public static Iterable<String> ids() {
        java.util.List<String> all = new java.util.ArrayList<>(CIVILIAN.keySet());
        all.addAll(JOB.keySet());
        return all;
    }

    public static String bodyModel(String id, Paint paint) {
        return hasPaints(id) ? "junseocity:car_" + id + "_" + paint.id() : "junseocity:car_" + id;
    }

    public static String wheelModel(String id) {
        return "junseocity:car_" + id + "_wheel";
    }

    /** 리소스팩 안 경로 → 내용 */
    public static synchronized Map<String, byte[]> packFiles() {
        if (files == null) {
            Map<String, byte[]> out = new LinkedHashMap<>();
            for (Map.Entry<String, IntFunction<CarDesign>> e : CIVILIAN.entrySet()) {
                String id = e.getKey();
                boolean first = true;
                for (Paint p : Paint.values()) {
                    CarModelMaker.Assets a = CarModelMaker.make(e.getValue().apply(p.rgb));
                    String name = id + "_" + p.id();
                    if (first) {
                        a.body().textures.put("0", "junseocity:item/car/" + name);
                        a.body().textures.put("particle", "junseocity:item/car/" + name);
                        out.put("assets/junseocity/models/item/car/" + id + ".json", a.body().json().getBytes(StandardCharsets.UTF_8));
                        wheel(out, id, a);
                        first = false;
                    }
                    out.put("assets/junseocity/models/item/car/" + name + ".json", child(id, name));
                    out.put("assets/junseocity/items/car_" + name + ".json", item("car/" + name));
                    out.put("assets/junseocity/textures/item/car/" + name + ".png", a.bodyTex().png());
                }
            }
            for (Map.Entry<String, Supplier<CarDesign>> e : JOB.entrySet()) {
                String id = e.getKey();
                CarModelMaker.Assets a = CarModelMaker.make(e.getValue().get());
                out.put("assets/junseocity/models/item/car/" + id + ".json", a.body().json().getBytes(StandardCharsets.UTF_8));
                out.put("assets/junseocity/items/car_" + id + ".json", item("car/" + id));
                out.put("assets/junseocity/textures/item/car/" + id + ".png", a.bodyTex().png());
                wheel(out, id, a);
            }
            files = out;
        }
        return files;
    }

    private static void wheel(Map<String, byte[]> out, String id, CarModelMaker.Assets a) {
        out.put("assets/junseocity/items/car_" + id + "_wheel.json", item("car/wheel_" + id));
        out.put("assets/junseocity/models/item/car/wheel_" + id + ".json", a.wheel().json().getBytes(StandardCharsets.UTF_8));
        out.put("assets/junseocity/textures/item/car/wheel_" + id + ".png", a.wheelTex().png());
    }

    /** 같은 모양에 그림만 바꾼 모델 */
    private static byte[] child(String parent, String name) {
        return ("{\"parent\":\"junseocity:item/car/" + parent + "\",\"textures\":{\"0\":\"junseocity:item/car/" + name
                + "\",\"particle\":\"junseocity:item/car/" + name + "\"}}\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] item(String model) {
        return ("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"junseocity:item/" + model + "\"}}\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
