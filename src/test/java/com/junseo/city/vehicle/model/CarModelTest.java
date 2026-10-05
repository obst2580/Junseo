package com.junseo.city.vehicle.model;

import com.junseo.city.vehicle.model.ItemModel.Element;
import com.junseo.city.vehicle.model.ItemModel.Face;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CarModelTest {
    private static final CarDesign MORNING = new MorningDesign();

    /** 마인크래프트가 받아들이는 모델인지: 좌표 -16..32, 돌림 각도, uv 0..16 */
    @Test
    void modelsStayInsideMinecraftLimits() {
        CarModelMaker.Assets a = CarModelMaker.make(MORNING);
        for (ItemModel m : new ItemModel[]{a.body(), a.wheel()}) {
            for (Element e : m.elements) {
                for (int i = 0; i < 3; i++) {
                    assertTrue(e.from()[i] >= -16 && e.to()[i] <= 32 && e.from()[i] <= e.to()[i], "좌표 범위");
                }
                assertTrue(e.axis() == null || Math.abs(e.angle()) <= 45, "각도 " + e.angle());
                for (Face f : e.faces().values()) {
                    for (double v : new double[]{f.u1(), f.v1(), f.u2(), f.v2()}) {
                        assertTrue(v >= 0 && v <= 16, "uv " + v);
                    }
                }
            }
        }
        assertTrue(a.body().elements.size() < 500, "차체 상자 수 " + a.body().elements.size());
        assertTrue(a.body().json().startsWith("{\"textures\""));
    }

    /** 리소스팩에 들어가는 파일: 아이템 정의·모델 JSON 은 읽히고, 그림은 PNG */
    @Test
    void packFilesAreValid() {
        Map<String, byte[]> files = CarModels.packFiles();
        for (String path : new String[]{"assets/junseocity/items/car_morning.json", "assets/junseocity/items/car_morning_wheel.json",
                "assets/junseocity/models/item/car/morning.json", "assets/junseocity/models/item/car/wheel_morning.json",
                "assets/junseocity/textures/item/car/morning.png", "assets/junseocity/textures/item/car/wheel_morning.png"}) {
            assertTrue(files.containsKey(path), path);
            byte[] b = files.get(path);
            if (path.endsWith(".json")) {
                com.google.gson.JsonParser.parseString(new String(b, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                assertTrue(b[1] == 'P' && b[2] == 'N' && b[3] == 'G', path);
            }
        }
        System.out.println("차 모델 JSON " + files.get("assets/junseocity/models/item/car/morning.json").length / 1024 + " KB");
    }

    @Test
    void renderPreview() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        CarModelMaker.Assets a = CarModelMaker.make(MORNING);
        System.out.println("차체 상자 " + a.body().elements.size() + "개, 바퀴 " + a.wheel().elements.size() + "개");
        File dir = new File("build/preview");
        dir.mkdirs();
        ModelRaster r = scene(a, MORNING, 18);
        Map<String, double[][]> views = Map.of(
                "car-morning-front", new double[][]{{-3.4, 1.25, 4.3}, {0, 0.62, 0.35}},
                "car-morning-rear", new double[][]{{3.6, 1.7, -4.4}, {0, 0.7, -0.3}},
                "car-morning-side", new double[][]{{-7.5, 0.85, 0}, {0, 0.72, 0}});
        for (Map.Entry<String, double[][]> v : views.entrySet()) {
            BufferedImage img = r.render(v.getValue()[0], v.getValue()[1], 32, 1000, 640, 1.05, 2.0);
            ImageIO.write(img, "png", new File(dir, v.getKey() + ".png"));
        }
        // 그림 원본 (3배)
        BufferedImage tex = new BufferedImage(a.bodyTex().w * 3, a.bodyTex().h * 3, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < tex.getHeight(); y++) {
            for (int x = 0; x < tex.getWidth(); x++) {
                tex.setRGB(x, y, a.bodyTex().get(x / 3, y / 3));
            }
        }
        ImageIO.write(tex, "png", new File(dir, "car-morning-texture.png"));
    }

    /** 차체 + 바퀴 넷 (앞바퀴는 steer 도 꺾음) */
    static ModelRaster scene(CarModelMaker.Assets a, CarDesign d, double steer) {
        ModelRaster r = new ModelRaster();
        double u = CarModelMaker.UNIT;
        r.add(a.body(), a.bodyTex(), p -> new double[]{(p[0] - 8) / u, p[1] / u, (p[2] - 8) / u});
        for (double ax : new double[]{d.frontAxle(), d.rearAxle()}) {
            for (int s : new int[]{1, -1}) {
                double cx = s * d.track() / 2, st = ax == d.frontAxle() ? Math.toRadians(steer) : 0;
                r.add(a.wheel(), a.wheelTex(), p -> {
                    double x = (p[0] - 8) / u, y = (p[1] - 8) / u, z = (p[2] - 8) / u;
                    double rx = x * Math.cos(st) + z * Math.sin(st), rz = -x * Math.sin(st) + z * Math.cos(st);
                    return new double[]{cx + rx, d.wheelRadius() + y, ax + rz};
                });
            }
        }
        return r;
    }
}
