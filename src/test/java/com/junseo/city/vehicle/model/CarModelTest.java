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
    private static final CarDesign MORNING = Designs.morning(CarModels.Paint.WHITE.rgb);

    /** 마인크래프트가 받아들이는 모델인지: 좌표 -16..32, 돌림 각도, uv 0..16 */
    @Test
    void modelsStayInsideMinecraftLimits() {
        for (String id : CarModels.ids()) {
        CarModelMaker.Assets a = CarModelMaker.make(CarModels.design(id));
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
        assertTrue(a.body().elements.size() < 600, id + " 차체 상자 수 " + a.body().elements.size());
        assertTrue(a.body().json().startsWith("{\"textures\""));
        }
    }

    /** 리소스팩에 들어가는 파일: 아이템 정의·모델 JSON 은 읽히고, 그림은 PNG */
    @Test
    void packFilesAreValid() {
        Map<String, byte[]> files = CarModels.packFiles();
        for (String path : new String[]{"assets/junseocity/items/car_morning_red.json", "assets/junseocity/items/car_morning_wheel.json",
                "assets/junseocity/models/item/car/morning.json", "assets/junseocity/models/item/car/morning_red.json",
                "assets/junseocity/models/item/car/wheel_morning.json", "assets/junseocity/textures/item/car/morning_red.png",
                "assets/junseocity/textures/item/car/wheel_morning.png", "assets/junseocity/items/car_police.json",
                "assets/junseocity/textures/item/car/garbage.png"}) {
            assertTrue(files.containsKey(path), path);
            byte[] b = files.get(path);
            if (path.endsWith(".json")) {
                com.google.gson.JsonParser.parseString(new String(b, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                assertTrue(b[1] == 'P' && b[2] == 'N' && b[3] == 'G', path);
            }
        }
        long total = files.values().stream().mapToLong(b -> b.length).sum();
        System.out.println("차 리소스팩 파일 " + files.size() + "개, " + total / 1024 + " KB");
    }

    /** 모든 차를 앞 3/4·뒤 3/4 로 한 장씩 (차종마다 칸 하나). -DmapPreview=true 일 때만 */
    @Test
    void renderPreview() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        File dir = new File("build/preview");
        dir.mkdirs();
        java.util.List<String> ids = new java.util.ArrayList<>();
        CarModels.ids().forEach(ids::add);
        int cw = 480, ch = 300, cols = 3, rows = (ids.size() + cols - 1) / cols;
        for (boolean front : new boolean[]{true, false}) {
            BufferedImage sheet = new BufferedImage(cw * cols, ch * rows, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = sheet.createGraphics();
            for (int k = 0; k < ids.size(); k++) {
                CarDesign d = CarModels.design(ids.get(k));
                CarModelMaker.Assets a = CarModelMaker.make(d);
                double L = d.length(), s = front ? 1 : -1;
                double[] eye = {-s * L * 0.95, d.height() * 0.95 + 0.4, s * L * 1.2}, at = {0, d.height() * 0.45, s * 0.1};
                BufferedImage img = scene(a, d, front ? 18 : 0).render(eye, at, 32, cw, ch, d.width() * 0.62, L * 0.55);
                g.drawImage(img, (k % cols) * cw, (k / cols) * ch, null);
                g.setColor(java.awt.Color.RED);
                g.drawString(ids.get(k) + " (" + a.body().elements.size() + ")", (k % cols) * cw + 6, (k / cols) * ch + 14);
            }
            g.dispose();
            ImageIO.write(sheet, "png", new File(dir, front ? "car-lineup-front.png" : "car-lineup-rear.png"));
        }
        // 색 6가지 (세단)
        BufferedImage colors = new BufferedImage(cw * 3, ch * 2, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = colors.createGraphics();
        CarModels.Paint[] ps = CarModels.Paint.values();
        for (int k = 0; k < ps.length; k++) {
            CarDesign d = Designs.sedan(ps[k].rgb);
            CarModelMaker.Assets a = CarModelMaker.make(d);
            BufferedImage img = scene(a, d, 0).render(new double[]{-4.6, 1.8, 5.6}, new double[]{0, 0.6, 0.1}, 32, cw, ch, 1.15, 2.7);
            g.drawImage(img, (k % 3) * cw, (k / 3) * ch, null);
        }
        g.dispose();
        ImageIO.write(colors, "png", new File(dir, "car-colors.png"));
    }

    /** 차체 + 바퀴 넷 (앞바퀴는 steer 도 꺾음) */
    static ModelRaster scene(CarModelMaker.Assets a, CarDesign d, double steer) {
        ModelRaster r = new ModelRaster();
        double u = d.unit();
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
