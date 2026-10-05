package com.junseo.city.vehicle.model;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 리소스팩에 넣는 차 모델 목록. 처음 부를 때 한 번 만들고 그대로 씁니다.
 * 아이템 모델 이름: 차체 junseocity:car_&lt;id&gt;, 바퀴 junseocity:car_&lt;id&gt;_wheel.
 */
public final class CarModels {
    public static final CarDesign MORNING = new MorningDesign();
    private static final List<CarDesign> ALL = List.of(MORNING);
    private static Map<String, byte[]> files;

    private CarModels() {
    }

    public static String bodyModel(CarDesign d) {
        return "junseocity:car_" + d.id();
    }

    public static String wheelModel(CarDesign d) {
        return "junseocity:car_" + d.id() + "_wheel";
    }

    /** 리소스팩 안 경로 → 내용 */
    public static synchronized Map<String, byte[]> packFiles() {
        if (files == null) {
            Map<String, byte[]> out = new LinkedHashMap<>();
            for (CarDesign d : ALL) {
                CarModelMaker.Assets a = CarModelMaker.make(d);
                String id = d.id();
                out.put("assets/junseocity/items/car_" + id + ".json", item("car/" + id));
                out.put("assets/junseocity/items/car_" + id + "_wheel.json", item("car/wheel_" + id));
                out.put("assets/junseocity/models/item/car/" + id + ".json", a.body().json().getBytes(StandardCharsets.UTF_8));
                out.put("assets/junseocity/models/item/car/wheel_" + id + ".json", a.wheel().json().getBytes(StandardCharsets.UTF_8));
                out.put("assets/junseocity/textures/item/car/" + id + ".png", a.bodyTex().png());
                out.put("assets/junseocity/textures/item/car/wheel_" + id + ".png", a.wheelTex().png());
            }
            files = out;
        }
        return files;
    }

    private static byte[] item(String model) {
        return ("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"junseocity:item/" + model + "\"}}\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
