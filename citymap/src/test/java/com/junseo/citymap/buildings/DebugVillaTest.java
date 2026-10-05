package com.junseo.citymap.buildings;

import org.junit.jupiter.api.Test;
import java.util.Random;

class DebugVillaTest {
    @Test
    void dump() {
        Voxels v = ShopHouse.build(10, 10, new Random(0), ShopHouse.Style.VILLA);
        StringBuilder sb = new StringBuilder();
        WalkCheck w = new WalkCheck(v).run();
        for (int y = -1; y <= 6; y++) {
            sb.append("y=").append(y).append('\n');
            for (int j = 0; j < v.d; j++) {
                for (int i = 0; i < v.w; i++) {
                    Block b = v.get(i, y, j);
                    char c = b == null ? '.' : b.isAir() ? ' ' : b.id().contains("stairs") ? (b.property("facing").charAt(0)) : b.id().contains("door") ? 'D' : b.id().contains("andesite") ? '=' : b.id().contains("concrete") ? '#' : b.id().contains("brick") ? 'B' : b.id().contains("smooth_stone") ? '-' : '?';
                    sb.append(c);
                }
                sb.append("   ");
                for (int i = 0; i < v.w; i++) {
                    sb.append(w.reached(i, y, j) ? 'o' : '.');
                }
                sb.append('\n');
            }
        }
        System.out.println(sb);
    }
}
