package com.junseo.citymap.buildings;

/**
 * 건물에 쓰는 블록 모음. 색은 미리보기 그림용 대략적인 텍스처 평균색입니다.
 * 26.2 에 있는 블록만 씁니다 (BlockNamesTest 가 검사). 상자·표지판·현수막·침대처럼
 * 블록 엔티티가 있어야 보이는 블록은 생성 단계에서 놓으면 안 보일 수 있어서 쓰지 않습니다.
 */
public final class Blocks {
    public static final Block AIR = Block.of("air", -1);

    // 콘크리트
    public static final Block WHITE_CONCRETE = Block.of("white_concrete", 0xCFD5D6);
    public static final Block LIGHT_GRAY_CONCRETE = Block.of("light_gray_concrete", 0x7D7D73);
    public static final Block GRAY_CONCRETE = Block.of("gray_concrete", 0x36393D);
    public static final Block BLACK_CONCRETE = Block.of("black_concrete", 0x080A0F);
    public static final Block RED_CONCRETE = Block.of("red_concrete", 0x8E2121);
    public static final Block ORANGE_CONCRETE = Block.of("orange_concrete", 0xE06101);
    public static final Block YELLOW_CONCRETE = Block.of("yellow_concrete", 0xF0AF15);
    public static final Block LIME_CONCRETE = Block.of("lime_concrete", 0x5EA918);
    public static final Block GREEN_CONCRETE = Block.of("green_concrete", 0x495B24);
    public static final Block CYAN_CONCRETE = Block.of("cyan_concrete", 0x157788);
    public static final Block LIGHT_BLUE_CONCRETE = Block.of("light_blue_concrete", 0x2389C7);
    public static final Block BLUE_CONCRETE = Block.of("blue_concrete", 0x2C2E8F);
    public static final Block PURPLE_CONCRETE = Block.of("purple_concrete", 0x64209C);
    public static final Block MAGENTA_CONCRETE = Block.of("magenta_concrete", 0xA9309F);
    public static final Block PINK_CONCRETE = Block.of("pink_concrete", 0xD6658F);
    public static final Block BROWN_CONCRETE = Block.of("brown_concrete", 0x603C20);

    // 테라코타
    public static final Block TERRACOTTA = Block.of("terracotta", 0x985E43);
    public static final Block WHITE_TERRACOTTA = Block.of("white_terracotta", 0xD1B2A1);
    public static final Block LIGHT_GRAY_TERRACOTTA = Block.of("light_gray_terracotta", 0x876A61);
    public static final Block GRAY_TERRACOTTA = Block.of("gray_terracotta", 0x392A23);
    public static final Block ORANGE_TERRACOTTA = Block.of("orange_terracotta", 0xA15325);
    public static final Block YELLOW_TERRACOTTA = Block.of("yellow_terracotta", 0xBA8523);
    public static final Block RED_TERRACOTTA = Block.of("red_terracotta", 0x8F3D2E);
    public static final Block BROWN_TERRACOTTA = Block.of("brown_terracotta", 0x4D3323);
    public static final Block LIME_TERRACOTTA = Block.of("lime_terracotta", 0x677534);
    public static final Block CYAN_TERRACOTTA = Block.of("cyan_terracotta", 0x565B5B);
    public static final Block LIGHT_BLUE_TERRACOTTA = Block.of("light_blue_terracotta", 0x716C89);
    public static final Block PINK_TERRACOTTA = Block.of("pink_terracotta", 0xA14E4E);

    // 양털 (시장 차양·천 가게)
    public static final Block WHITE_WOOL = Block.of("white_wool", 0xE9ECEC);
    public static final Block RED_WOOL = Block.of("red_wool", 0xA12722);
    public static final Block ORANGE_WOOL = Block.of("orange_wool", 0xF07613);
    public static final Block YELLOW_WOOL = Block.of("yellow_wool", 0xF8C527);
    public static final Block LIME_WOOL = Block.of("lime_wool", 0x70B919);
    public static final Block GREEN_WOOL = Block.of("green_wool", 0x546D1B);
    public static final Block LIGHT_BLUE_WOOL = Block.of("light_blue_wool", 0x3AAFD9);
    public static final Block CYAN_WOOL = Block.of("cyan_wool", 0x158991);
    public static final Block BLUE_WOOL = Block.of("blue_wool", 0x35399D);
    public static final Block PURPLE_WOOL = Block.of("purple_wool", 0x792AAC);
    public static final Block MAGENTA_WOOL = Block.of("magenta_wool", 0xBD44B3);
    public static final Block PINK_WOOL = Block.of("pink_wool", 0xED8DAC);

    // 유리
    public static final Block GLASS = Block.of("glass", 0xC8DCE4);
    public static final Block WHITE_GLASS = Block.of("white_stained_glass", 0xF0F0F0);
    public static final Block LIGHT_BLUE_GLASS = Block.of("light_blue_stained_glass", 0x6699D8);
    public static final Block CYAN_GLASS = Block.of("cyan_stained_glass", 0x4C7F99);
    public static final Block GRAY_GLASS = Block.of("gray_stained_glass", 0x4C4C4C);
    public static final Block BLACK_GLASS = Block.of("black_stained_glass", 0x191919);
    public static final Block GLASS_PANE = Block.of("glass_pane", 0xC8DCE4);
    public static final Block LIGHT_BLUE_PANE = Block.of("light_blue_stained_glass_pane", 0x6699D8);
    public static final Block GRAY_PANE = Block.of("gray_stained_glass_pane", 0x4C4C4C);
    public static final Block IRON_BARS = Block.of("iron_bars", 0x888888);

    // 돌·벽돌
    public static final Block STONE = Block.of("stone", 0x7E7E7E);
    public static final Block SMOOTH_STONE = Block.of("smooth_stone", 0x9E9E9E);
    public static final Block SMOOTH_STONE_SLAB = Block.of("smooth_stone_slab[type=bottom]", 0x9E9E9E);
    public static final Block STONE_BRICKS = Block.of("stone_bricks", 0x7A7979);
    public static final Block MOSSY_STONE_BRICKS = Block.of("mossy_stone_bricks", 0x737969);
    public static final Block CRACKED_STONE_BRICKS = Block.of("cracked_stone_bricks", 0x767676);
    public static final Block ANDESITE = Block.of("andesite", 0x888888);
    public static final Block POLISHED_ANDESITE = Block.of("polished_andesite", 0x848685);
    public static final Block POLISHED_DIORITE = Block.of("polished_diorite", 0xC0C0C1);
    public static final Block POLISHED_GRANITE = Block.of("polished_granite", 0x9A6A59);
    public static final Block POLISHED_DEEPSLATE = Block.of("polished_deepslate", 0x484849);
    public static final Block DEEPSLATE_TILES = Block.of("deepslate_tiles", 0x363637);
    public static final Block DEEPSLATE_BRICKS = Block.of("deepslate_bricks", 0x464648);
    public static final Block POLISHED_BLACKSTONE = Block.of("polished_blackstone", 0x353038);
    public static final Block COBBLESTONE = Block.of("cobblestone", 0x7F7F7F);
    public static final Block MOSSY_COBBLESTONE = Block.of("mossy_cobblestone", 0x6E7661);
    public static final Block BRICKS = Block.of("bricks", 0x966153);
    public static final Block MUD_BRICKS = Block.of("mud_bricks", 0x89684F);
    public static final Block SANDSTONE = Block.of("smooth_sandstone", 0xDFD6AA);
    public static final Block QUARTZ = Block.of("quartz_block", 0xEBE5DE);
    public static final Block SMOOTH_QUARTZ = Block.of("smooth_quartz", 0xECE6DF);
    public static final Block QUARTZ_PILLAR = Block.of("quartz_pillar[axis=y]", 0xEBE6E0);
    public static final Block QUARTZ_SLAB = Block.of("smooth_quartz_slab[type=bottom]", 0xECE6DF);
    public static final Block QUARTZ_SLAB_TOP = Block.of("smooth_quartz_slab[type=top]", 0xECE6DF);
    public static final Block CALCITE = Block.of("calcite", 0xDFE0DC);
    public static final Block IRON_BLOCK = Block.of("iron_block", 0xDCDCDC);
    public static final Block GOLD_BLOCK = Block.of("gold_block", 0xF6D03D);
    public static final Block RED_NETHER_BRICKS = Block.of("red_nether_bricks", 0x450709);

    // 땅
    public static final Block GRASS = Block.of("grass_block[snowy=false]", 0x7CBD6B);
    public static final Block DIRT = Block.of("dirt", 0x86603F);
    public static final Block COARSE_DIRT = Block.of("coarse_dirt", 0x77563B);
    public static final Block PACKED_MUD = Block.of("packed_mud", 0x8E6B50);
    public static final Block MOSS = Block.of("moss_block", 0x596E2D);
    public static final Block WATER = Block.of("water[level=0]", 0x3F76E4);

    // 나무
    public static final Block OAK_PLANKS = Block.of("oak_planks", 0xA2834F);
    public static final Block SPRUCE_PLANKS = Block.of("spruce_planks", 0x725430);
    public static final Block DARK_OAK_PLANKS = Block.of("dark_oak_planks", 0x432B14);
    public static final Block SPRUCE_SLAB = Block.of("spruce_slab[type=bottom]", 0x725430);
    public static final Block DARK_OAK_SLAB = Block.of("dark_oak_slab[type=bottom]", 0x432B14);
    public static final Block OAK_SLAB = Block.of("oak_slab[type=bottom]", 0xA2834F);
    public static final Block SPRUCE_FENCE = Block.of("spruce_fence", 0x725430);
    public static final Block OAK_LEAVES = Block.of("oak_leaves[persistent=true]", 0x4F7F2A);
    public static final Block AZALEA_LEAVES = Block.of("flowering_azalea_leaves[persistent=true]", 0x63753A);
    public static final Block OAK_LOG = Block.of("oak_log[axis=y]", 0x6D5532);

    // 빛
    public static final Block SEA_LANTERN = Block.of("sea_lantern", 0xACC7BE);
    public static final Block GLOWSTONE = Block.of("glowstone", 0xAB8354);
    public static final Block SHROOMLIGHT = Block.of("shroomlight", 0xF09246);
    public static final Block FROGLIGHT = Block.of("ochre_froglight[axis=y]", 0xF5E9B6);
    public static final Block LANTERN_HANGING = Block.of("lantern[hanging=true]", 0x6A5B49);
    public static final Block LANTERN = Block.of("lantern[hanging=false]", 0x6A5B49);

    // 살림·장식
    public static final Block BARREL = Block.of("barrel[facing=up]", 0x86643B);
    public static final Block SMOKER = Block.of("smoker[facing=south]", 0x555451);
    public static final Block CAMPFIRE = Block.of("campfire[facing=north,lit=true]", 0x584A35);
    public static final Block CAULDRON = Block.of("cauldron", 0x4A4A4A);
    public static final Block HAY = Block.of("hay_block[axis=y]", 0xA68B0C);
    public static final Block MELON = Block.of("melon", 0x6F9119);
    public static final Block PUMPKIN = Block.of("pumpkin", 0xC57618);
    public static final Block DRIED_KELP = Block.of("dried_kelp_block", 0x323B27);
    public static final Block PACKED_ICE = Block.of("packed_ice", 0x8DB4FA);
    public static final Block BLUE_ICE = Block.of("blue_ice", 0x74A8FD);
    public static final Block PRISMARINE = Block.of("prismarine", 0x63A79B);
    public static final Block DARK_PRISMARINE = Block.of("dark_prismarine", 0x335B4B);
    public static final Block SEA_PICKLE = Block.of("sea_pickle[pickles=4,waterlogged=true]", 0x5A6127);
    public static final Block FLOWER_POT_TULIP = Block.of("potted_red_tulip", 0x9E5A3A);
    public static final Block FLOWER_POT_DANDELION = Block.of("potted_dandelion", 0xC09A2A);
    public static final Block FLOWER_POT_FERN = Block.of("potted_fern", 0x5B7F3A);
    public static final Block BLAST_FURNACE = Block.of("blast_furnace[facing=south]", 0x505050);
    public static final Block STONECUTTER = Block.of("stonecutter[facing=south]", 0x7B7774);
    public static final Block ANVIL = Block.of("anvil[facing=east]", 0x444444);
    public static final Block COBWEB = Block.of("cobweb", 0xE4E9EA);
    public static final Block LADDER = Block.of("ladder[facing=south,waterlogged=false]", 0x7C6233);
    public static final Block SCAFFOLDING = Block.of("scaffolding[bottom=false,distance=0,waterlogged=false]", 0xAA8448);

    /** 문 (아래·위 두 칸). facing 은 문을 열고 들어가는 쪽이 아니라 문이 바라보는 쪽 */
    public static Block door(String wood, String facing, boolean upper) {
        return Block.of(wood + "_door[facing=" + facing + ",half=" + (upper ? "upper" : "lower")
                + ",hinge=left,open=false,powered=false]", wood.equals("iron") ? 0xC2C1C1 : 0x8C6E3E);
    }

    private static final java.util.Map<String, Integer> WOOD_RGB = java.util.Map.of(
            "oak", 0xA2834F, "spruce", 0x725430, "birch", 0xC0AF79, "dark_oak", 0x432B14, "acacia", 0xA8592F,
            "cherry", 0xE2B3AD, "mangrove", 0x763631, "bamboo", 0xC2AF52, "crimson", 0x653147, "warped", 0x2B6963);

    /**
     * 벽 표지판 (글씨 있음). 판은 facing 쪽을 보고, 그 반대쪽 블록에 붙습니다.
     * color 는 글자색 (black, white, yellow …), glow 면 밤에도 빛나는 글씨.
     */
    public static Block wallSign(String wood, String facing, String color, boolean glow, String... lines) {
        return Block.of(wood + "_wall_sign[facing=" + facing + ",waterlogged=false]", WOOD_RGB.get(wood))
                .withText(color, glow, lines);
    }

    /** 천장에 매다는 표지판. rotation 0 = 남쪽, 4 = 서쪽, 8 = 북쪽, 12 = 동쪽을 봄 (양면에 글씨) */
    public static Block hangingSign(String wood, int rotation, String color, boolean glow, String... lines) {
        return Block.of(wood + "_hanging_sign[attached=false,rotation=" + rotation + ",waterlogged=false]", WOOD_RGB.get(wood))
                .withText(color, glow, lines);
    }

    /**
     * 벽에서 튀어나온 매다는 표지판 (돌출 간판). 판은 facing 쪽과 그 반대쪽을 보고,
     * facing 을 시계·반시계로 돌린 쪽의 벽에 붙습니다 (남쪽 벽 앞이면 facing=east 또는 west).
     */
    public static Block wallHangingSign(String wood, String facing, String color, boolean glow, String... lines) {
        return Block.of(wood + "_wall_hanging_sign[facing=" + facing + ",waterlogged=false]", WOOD_RGB.get(wood))
                .withText(color, glow, lines);
    }

    public static Block stairs(String material, String facing, int rgb) {
        return Block.of(material + "_stairs[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]", rgb);
    }

    /** 색 이름 → 콘크리트·양털·테라코타·색유리 */
    public static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] CONCRETE_RGB = {0xCFD5D6, 0xE06101, 0xA9309F, 0x2389C7, 0xF0AF15, 0x5EA918, 0xD6658F,
            0x36393D, 0x7D7D73, 0x157788, 0x64209C, 0x2C2E8F, 0x603C20, 0x495B24, 0x8E2121, 0x080A0F};
    private static final int[] WOOL_RGB = {0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C527, 0x70B919, 0xED8DAC,
            0x3E4447, 0x8E8E86, 0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA12722, 0x141519};
    private static final int[] TERRACOTTA_RGB = {0xD1B2A1, 0xA15325, 0x95576C, 0x716C89, 0xBA8523, 0x677534, 0xA14E4E,
            0x392A23, 0x876A61, 0x565B5B, 0x764656, 0x4A3B5B, 0x4D3323, 0x4C532A, 0x8F3D2E, 0x251610};

    public static Block concrete(String color) {
        return Block.of(color + "_concrete", CONCRETE_RGB[colorIndex(color)]);
    }

    public static Block wool(String color) {
        return Block.of(color + "_wool", WOOL_RGB[colorIndex(color)]);
    }

    public static Block terracotta(String color) {
        return Block.of(color + "_terracotta", TERRACOTTA_RGB[colorIndex(color)]);
    }

    public static Block glass(String color) {
        return Block.of(color + "_stained_glass", WOOL_RGB[colorIndex(color)]);
    }

    private static int colorIndex(String color) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equals(color)) {
                return i;
            }
        }
        throw new IllegalArgumentException(color);
    }

    private Blocks() {
    }
}
