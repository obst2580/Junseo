package com.junseo.citymap.buildings;

/**
 * 블록 하나: 마인크래프트 블록 데이터 문자열(예: "minecraft:oak_door[facing=south,half=lower]")과
 * 미리보기 그림에 쓸 색. 실제 블록으로 바꾸는 일은 생성기(mapgen)가 합니다.
 */
public record Block(String data, int rgb) {

    public static Block of(String name, int rgb) {
        return new Block(name.contains(":") ? name : "minecraft:" + name, rgb);
    }

    /** 속성을 뺀 블록 id (예: "minecraft:oak_door") */
    public String id() {
        int b = data.indexOf('[');
        return b < 0 ? data : data.substring(0, b);
    }

    public boolean isAir() {
        return data.equals("minecraft:air");
    }

    /** 같은 블록에 속성을 붙인 것. 예: Blocks.OAK_DOOR.with("facing=south,half=lower") */
    public Block with(String properties) {
        return new Block(id() + "[" + properties + "]", rgb);
    }

    /** 속성 값 (없으면 null) */
    public String property(String key) {
        int b = data.indexOf('[');
        if (b < 0) {
            return null;
        }
        for (String kv : data.substring(b + 1, data.length() - 1).split(",")) {
            int eq = kv.indexOf('=');
            if (kv.substring(0, eq).equals(key)) {
                return kv.substring(eq + 1);
            }
        }
        return null;
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};

    /**
     * 위에서 볼 때 시계 방향으로 quarter 번 90도 돌린 블록.
     * facing, axis, rotation 과 north/east/south/west 연결 속성을 같이 돌립니다.
     */
    public Block rotate(int quarter) {
        int q = Math.floorMod(quarter, 4);
        int b = data.indexOf('[');
        if (q == 0 || b < 0) {
            return this;
        }
        StringBuilder out = new StringBuilder(data.length()).append(data, 0, b + 1);
        String[] parts = data.substring(b + 1, data.length() - 1).split(",");
        for (int k = 0; k < parts.length; k++) {
            String kv = parts[k];
            int eq = kv.indexOf('=');
            String key = kv.substring(0, eq), value = kv.substring(eq + 1);
            int dk = dirIndex(key);
            if (dk >= 0) {
                key = DIRS[(dk + q) % 4];
            } else if (key.equals("facing") && dirIndex(value) >= 0) {
                value = DIRS[(dirIndex(value) + q) % 4];
            } else if (key.equals("axis") && q % 2 == 1 && !value.equals("y")) {
                value = value.equals("x") ? "z" : "x";
            } else if (key.equals("rotation")) {
                value = Integer.toString((Integer.parseInt(value) + 4 * q) % 16);
            }
            if (k > 0) {
                out.append(',');
            }
            out.append(key).append('=').append(value);
        }
        return new Block(out.append(']').toString(), rgb);
    }

    private static int dirIndex(String s) {
        for (int i = 0; i < 4; i++) {
            if (DIRS[i].equals(s)) {
                return i;
            }
        }
        return -1;
    }
}
