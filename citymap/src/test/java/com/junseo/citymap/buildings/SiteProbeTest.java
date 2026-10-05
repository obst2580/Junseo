package com.junseo.citymap.buildings;

import com.junseo.citymap.geo.Polygon;
import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class SiteProbeTest {
    @Test
    void probe() {
        Assumptions.assumeTrue(System.getProperty("previewOnly", "").startsWith("probe"));
        CityTerrain t = TestCity.terrain();
        for (String[] s : new String[][]{{"yeouido", "plaza"}, {"yeouido", "bank_hq"}, {"yeouido", "broadcast"},
                {"junggu", "cityhall"}, {"junggu", "station"}, {"junggu", "jewelry"}}) {
            Polygon area = Plans.district(t, s[0]);
            BuildMask m = BuildMask.of(t, area, 2);
            var h = Plans.hub(t, s[1]);
            int hx = (int) Math.floor(h.x()), hz = (int) Math.floor(h.z());
            int[] c = m.componentNear(hx, hz, 40);
            System.out.println(s[1] + " hub " + hx + "," + hz + " comp " + Arrays.toString(c));
        }
        String which = System.getProperty("previewOnly", "").replaceFirst("^probe:?", "");
        if (!which.isEmpty()) {
            String[] p = which.split(",");
            int x0 = Integer.parseInt(p[0]), z0 = Integer.parseInt(p[1]), x1 = Integer.parseInt(p[2]), z1 = Integer.parseInt(p[3]), st = Integer.parseInt(p[4]);
            Polygon area = Plans.district(t, p[5]);
            BuildMask m = BuildMask.of(t, area, 2);
            StringBuilder sb = new StringBuilder();
            sb.append("      ");
            for (int x = x0; x <= x1; x += st) {
                sb.append(Math.floorMod(x, 50) < st ? "|" : " ");
            }
            sb.append('\n');
            for (int z = z0; z <= z1; z += st) {
                sb.append(String.format("%5d ", z));
                for (int x = x0; x <= x1; x += st) {
                    Column c = t.column(x, z);
                    char ch;
                    if (c.isWater()) {
                        ch = '~';
                    } else if (c.deck) {
                        ch = '=';
                    } else if (c.surface == Surface.SIDEWALK) {
                        ch = ':';
                    } else if (c.isRoad()) {
                        ch = '#';
                    } else if (c.surface == Surface.PAD) {
                        ch = 'P';
                    } else if (c.mountainHeight > 0) {
                        ch = '^';
                    } else if (m.free(x, z)) {
                        ch = '.';
                    } else if (!area.contains(x + 0.5, z + 0.5)) {
                        ch = '_';
                    } else {
                        ch = ',';
                    }
                    sb.append(ch);
                }
                sb.append('\n');
            }
            System.out.println(sb);
        }
    }
}
