package com.junseo.citymap.buildings;

import com.junseo.citymap.terrain.CityTerrain;
import com.junseo.citymap.terrain.Column;
import com.junseo.citymap.terrain.Surface;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;

class ScratchMapTest {
    @Test
    void dump() throws IOException {
        java.io.File cfg = new java.io.File("/tmp/claude-0/-home-user-Junseo/604e9d7a-e65e-5950-b5c8-78ad94e098c5/scratchpad/scratchmap.cfg");
        Assumptions.assumeTrue(cfg.exists() && System.getProperty("previewOnly", "").equals("scratchmap"));
        String[] lines = java.nio.file.Files.readAllLines(cfg.toPath()).toArray(new String[0]);
        CityTerrain t = TestCity.terrain();
        for (int job = 0; job + 1 < lines.length; job += 2) {
            one(t, lines[job], lines[job + 1]);
        }
    }

    private static void one(CityTerrain t, String area, String out) throws IOException {
        String[] spec = area.split(",");
        int x0 = Integer.parseInt(spec[0]), z0 = Integer.parseInt(spec[1]), x1 = Integer.parseInt(spec[2]), z1 = Integer.parseInt(spec[3]);
        int step = Integer.parseInt(spec[4]);
        String dist = spec[5];
        BuildMask m = BuildMask.of(t, Plans.district(t, dist), 2);
        StringBuilder sb = new StringBuilder();
        for (int z = z0; z <= z1; z += step) {
            sb.append(String.format("%5d ", z));
            for (int x = x0; x <= x1; x += step) {
                Column c = t.column(x, z);
                char ch;
                if (c.surface == Surface.PAD || c.hubPillar > 0) ch = 'H';
                else if (c.deck) ch = '=';
                else if (c.isWater()) ch = '~';
                else if (c.tunnel) ch = 'T';
                else if (c.surface == Surface.SIDEWALK) ch = 's';
                else if (c.isRoad()) ch = 'R';
                else if (c.mountainHeight > 0) ch = (char) ('0' + Math.min(9, c.mountainHeight / 10));
                else if (m.free(x, z)) ch = '.';
                else if (dist.equals(c.district)) ch = ',';
                else ch = ' ';
                sb.append(ch);
            }
            sb.append('\n');
        }
        try (FileWriter w = new FileWriter(out)) {
            w.write("x0=" + x0 + " step=" + step + "\n" + sb);
        }
    }
}
