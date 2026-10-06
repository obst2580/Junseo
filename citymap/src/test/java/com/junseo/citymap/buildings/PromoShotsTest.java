package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 홍보용 랜드마크 그림 (1920×1080). 카메라 자리와 바라볼 곳만 정하면 방향은 계산합니다.
 * 멀리까지 보이게 안개를 늘리고, 산에는 게임처럼 숲을 심어서 그립니다.
 * <pre>./gradlew :citymap:test --tests '*PromoShotsTest*' -DmapPreview=true -PtestHeap=6g [-DpreviewOnly=promo-namsan]</pre>
 */
class PromoShotsTest {
    /** 카메라 (x, y, z) 에서 (tx, ty, tz) 를 봄. y 는 땅 위 높이 */
    record Shot(String name, double x, double y, double z, double tx, double ty, double tz, double fov) {
    }

    static final Shot[] SHOTS = {
            // 한강 건너 북쪽 강변에서 본 여의도 63빌딩과 금융가
            new Shot("promo-yeouido", -175, 26, -128, -55, 85, 85, 64),
            // 명동에서 남쪽으로 올려다본 남산타워
            new Shot("promo-namsan", 760, 46, -330, 767, 110, -41, 58),
            // 한강 건너 북쪽에서 본 잠실 초고층 타워와 야구장
            new Shot("promo-jamsil", 1240, 60, -70, 1230, 90, 330, 66),
            // 시청 앞 광장과 시청
            new Shot("promo-cityhall", 584, 34, -255, 584, 6, -390, 70),
            // 하늘에서 본 월드컵경기장
            new Shot("promo-worldcup", -470, 110, -150, -552, 0, -278, 60),
            // 테헤란로 빌딩 숲 (위에서 길을 따라)
            new Shot("promo-gangnam", 845, 75, 790, 845, 15, 470, 66),
            // 한강 위에서 본 용산·중구·남산 전경
            new Shot("promo-skyline", 250, 150, 160, 650, 40, -250, 70),
    };

    @Test
    void renderPromoShots() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean("mapPreview"), "-DmapPreview=true 일 때만 그립니다");
        String only = System.getProperty("previewOnly", "");
        CityTerrain terrain = TestCity.terrain();
        CityBuildings b = TestCity.buildings();
        int g = terrain.groundY();
        File dir = new File("build/preview");
        dir.mkdirs();
        int width = Integer.getInteger("promoWidth", 1920), height = width * 9 / 16;
        for (Shot s : SHOTS) {
            if (!only.isEmpty() && !s.name.contains(only)) {
                continue;
            }
            long t0 = System.currentTimeMillis();
            double dx = s.tx - s.x, dy = s.ty - s.y, dz = s.tz - s.z, hd = Math.hypot(dx, dz);
            double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = -Math.toDegrees(Math.atan2(dy, hd));
            int r = 240;
            int x0 = (int) Math.min(s.x, s.tx) - r, x1 = (int) Math.max(s.x, s.tx) + r;
            int z0 = (int) Math.min(s.z, s.tz) - r, z1 = (int) Math.max(s.z, s.tz) + r;
            IsoRender ir = new IsoRender(terrain, b, x0, z0, x1, z1, g - 12, g + 310).farView(620, 700).plantTrees();
            BufferedImage img = ir.perspective(s.x, g + s.y, s.z, yaw, pitch, s.fov, width, height);
            ImageIO.write(img, "png", new File(dir, s.name + ".png"));
            System.out.println(s.name + " " + (System.currentTimeMillis() - t0) + "ms");
        }
    }
}
