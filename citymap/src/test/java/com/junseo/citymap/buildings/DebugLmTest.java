package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Random;

/** 임시 디버그 (지울 것) */
class DebugLmTest {
    @Test
    void dbg() {
        Assumptions.assumeTrue(System.getProperty("previewOnly", "").equals("dbg"));
        Voxels o = SeoulStation.oldStation(SeoulStation.OLD_W, SeoulStation.OLD_D, new Random(5));
        WalkCheck w = new WalkCheck(o).run();
        for (int y = 0; y <= 12; y++) {
            System.out.println("y" + y + " " + w.reachedAt(y));
        }
        for (int y = 0; y <= 1; y++) {
            StringBuilder sb = new StringBuilder("y" + y + ": ");
            for (int j = 0; j <= 23; j++) {
                for (int i = 0; i <= 57; i++) {
                    Block b = o.get(i, y, j);
                    char c = b == null ? ' ' : b.isAir() ? '.' : b.id().contains("stairs") ? 's' : b.id().contains("door") ? 'D' : '#';
                    if ((b == null || b.isAir()) && w.reached(i, y, j)) {
                        c = '*';
                    }
                    sb.append(c);
                }
                sb.append("\n");
            }
            System.out.println(sb);
        }
    }
}
