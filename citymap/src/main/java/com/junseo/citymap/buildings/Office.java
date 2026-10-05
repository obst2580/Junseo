package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 구역마다 다른 업무·주상복합 빌딩. 뼈대(층판, 가운데 코어: 꺾인 계단·엘리베이터·화장실, 사무실·집 층, 옥상)는
 * {@link Tower} 로 짓고, 겉모양(외벽)·1층·포디움·꼭대기는 구역 성격에 맞게 다시 입힙니다.
 * <ul>
 *   <li>여의도: 증권가 1980~90년대 20~30층 짙은 화강석 + 갈색(브론즈) 유리, 세로 돌기둥 (GRANITE) /
 *       30층 넘는 새 유리 타워: 포디움, 왕관(유리 가림벽·계단식 꼭대기) (CURTAIN) / 15~20층 오피스텔 (OFFICETEL)</li>
 *   <li>강남(테헤란로): 20~40층 커튼월 유리 타워 (파랑·청록·검정 유리), 1층 카페·은행·편의점, 앞 공개공지가 넓음</li>
 *   <li>중구: 1970년대 8~12층 콘크리트 가로띠 창 (BANDS) / 1980년대 15~25층 돌·갈색 유리, 창이 쑥 들어감 (GRANITE) /
 *       세종대로 새 유리 빌딩 (CURTAIN)</li>
 *   <li>용산: 용산역 주상복합 30층 이상 — 상가 포디움 위 가는 주거 타워, 엇갈린 발코니 (RESIDENTIAL, kind "apartment")</li>
 *   <li>마포(마포대로): 15~25층 화강석 기둥 + 유리 (STONE_GLASS) / 낮으면 알루미늄 패널 (PANEL)</li>
 *   <li>송파: 10~20층 패널·유리 / 그 밖: 층수에 따라 섞음</li>
 * </ul>
 * podium = true 면 아래 3~5층이 땅을 꽉 채운 상가(1층 편의점·카페·은행, 2층부터 식당·병원·학원·운동),
 * 위는 들여 지은 타워. 아니면 앞에 공개공지(포장, 화단, 의자, 미술작품, 머릿돌)를 두고 들여 짓습니다.
 * <p>
 * 모든 층은 1층 정문 → 코어 앞 복도 → 꺾인 계단으로 걸어서 갑니다 (엘리베이터도 있음). 정면은 남쪽(j = d-1).
 * 1층 높이 5 (20층 넘는 업무 빌딩 로비는 8), 그 위는 4.
 * <p>
 * 건물 이름 간판은 {@link #name} 을 build 의 Random 으로 먼저 불러서 정합니다: 같은 seed 의 새 Random 으로
 * name 을 부르면 배치 이름과 간판 이름이 같아집니다.
 */
final class Office {
    /**
     * 외벽 양식 다섯: STONE(화강석 — 여의도·중구는 쑥 들어간 창, 그 밖은 돌기둥 사이 유리), BANDS(1970년대 콘크리트 가로띠 창),
     * PANEL(알루미늄·타일 패널과 네모 창, 오피스텔은 실외기 루버), CURTAIN(유리 커튼월 타워), RESIDENTIAL(주상복합 주거 타워)
     */
    enum Style { STONE, BANDS, PANEL, CURTAIN, RESIDENTIAL }

    static final int MIN_W = 24, MAX_W = 70, MIN_D = 24, MAX_D = 60, MIN_FLOORS = 6, MAX_FLOORS = 60;

    /** 구역과 층수로 정하는 건축 양식 (같은 구역·층수면 같은 양식, 색·모양은 Random) */
    static Style style(String district, int floors) {
        return switch (district == null ? "" : district) {
            case "yeouido" -> floors >= 31 ? Style.CURTAIN : floors >= 21 ? (floors % 3 == 0 ? Style.CURTAIN : Style.STONE)
                    : floors >= 15 ? Style.PANEL : Style.STONE;
            case "gangnam" -> floors >= 16 ? Style.CURTAIN : floors % 2 == 0 ? Style.STONE : Style.CURTAIN;
            case "junggu" -> floors >= 26 ? Style.CURTAIN : floors >= 13 ? Style.STONE : Style.BANDS;
            case "yongsan" -> floors >= 30 ? Style.RESIDENTIAL : floors >= 15 ? Style.CURTAIN : Style.STONE;
            case "mapo" -> floors >= 15 ? Style.STONE : Style.PANEL;
            case "songpa" -> floors >= 16 ? Style.CURTAIN : floors % 2 == 0 ? Style.PANEL : Style.STONE;
            default -> floors >= 20 ? Style.CURTAIN : floors >= 12 ? Style.STONE : Style.PANEL;
        };
    }

    /** "office" (업무·오피스텔), 용산 주상복합 타워만 "apartment" */
    static String kind(String district, int floors) {
        return style(district, floors) == Style.RESIDENTIAL ? "apartment" : "office";
    }

    /** 여의도 15~20층 패널 빌딩은 오피스텔 (이름·안내판·실외기 루버만 다름, 위층은 빈 껍데기) */
    static boolean officetel(String district, int floors) {
        return "yeouido".equals(district) && style(district, floors) == Style.PANEL && floors >= 15;
    }

    /** 건물 이름 (간판 한 줄에 들어가는 길이) */
    static String name(String district, int floors, Random r) {
        String pre = KoreanNames.prefix(r);
        String[] tails = officetel(district, floors) ? new String[]{"오피스텔", "레지던스", "오피스텔", "스테이"} : switch (style(district, floors)) {
            case RESIDENTIAL -> new String[]{"파크타워", "센트럴", "시티타워", "리버뷰", "센트럴파크"};
            default -> switch (district == null ? "" : district) {
                case "yeouido" -> new String[]{"증권빌딩", "투자증권", "금융센터", "파이낸스", "생명빌딩", "증권"};
                case "gangnam" -> new String[]{"타워", "센터", "스퀘어", "빌딩", "타워"};
                case "junggu" -> new String[]{"빌딩", "회관", "센터", "화재빌딩", "빌딩"};
                case "mapo" -> new String[]{"빌딩", "타워", "센터", "오피스"};
                default -> new String[]{"빌딩", "타워", "센터", "오피스"};
            };
        };
        return pre + tails[r.nextInt(tails.length)];
    }

    /**
     * @param district 구역 id (yeouido, gangnam, junggu, yongsan, mapo, songpa, 그 밖은 기본)
     * @param w        너비 24..70
     * @param d        깊이 24..60 (정면 = 남쪽)
     * @param floors   층수 6..60 (그대로 지킴)
     * @param podium   아래 3~5층을 땅 가득 상가로
     */
    static Voxels build(String district, int w, int d, int floors, boolean podium, Random r) {
        return new Office(district, Math.max(MIN_W, w), Math.max(MIN_D, d), Math.max(1, floors), podium, r).make();
    }

    /**
     * 집 한 채나 방 하나의 안쪽 바닥 (벽 빼고, 건물 좌표). type 은 "원룸", "84형"(세대 전체), "거실", "주방", "안방",
     * "침실", "욕실", "현관", "발코니".
     */
    record Room(String type, int i0, int j0, int i1, int j1, int level) {
        int sizeI() {
            return i1 - i0 + 1;
        }

        int sizeJ() {
            return j1 - j0 + 1;
        }

        /** 짧은 변 */
        int small() {
            return Math.min(sizeI(), sizeJ());
        }

        /** 긴 변 */
        int large() {
            return Math.max(sizeI(), sizeJ());
        }
    }

    /** 검사용: 지은 건물과 그 안의 집·방 목록, 층 높이들 */
    record Built(Voxels voxels, List<Room> rooms, int[] levels, boolean[] used) {
    }

    static Built inspect(String district, int w, int d, int floors, boolean podium, Random r) {
        Office o = new Office(district, Math.max(MIN_W, w), Math.max(MIN_D, d), Math.max(1, floors), podium, r);
        Voxels v = o.make();
        boolean[] used = new boolean[o.floors];
        for (int k = 0; k < o.floors; k++) {
            used[k] = o.used(k);
        }
        return new Built(v, o.rooms, o.t.levels, used);
    }

    // ------------------------------------------------------------------ 상태

    private final String district, name;
    private final Style style;
    private final int w, d, floors;
    private final boolean podium;
    private final Random r;
    /** 포디움 층 수 (없으면 0) */
    private final int p;
    private int px0, pz0, px1, pz1;
    private int tx0, tz0, tx1, tz1;
    /** 모서리 깎기, 꼭대기 (0 평지붕, 1 유리 가림벽, 2 계단식 + 가림벽, 3 세로 지느러미), 계단식 시작 층, 피난안전층 */
    private int chamfer, crown, stepFrom = Integer.MAX_VALUE, mechK = -1;
    /** 1층 앞을 들여 지어 기둥만 남김 (회랑) */
    private boolean colonnade;
    private int elevators, restrooms, lobbyH;
    // 재료
    private Block wall, glass, frame, band, spandrel, podStone, podFrame, podGlass;
    private boolean fins, recessed, balconies, piers, louvers;
    private int bay, mEvery;
    private String[] shopUses, tenantUses;
    /** 집 층: 코어를 북쪽 바깥 벽에 붙이고(맨 위층을 북쪽 띠 펜트하우스로 줄여서) 남쪽 한 줄에만 세대 */
    private boolean penthouse;
    private Tower t;
    private Voxels v;
    /** 지은 집·방 (검사용): 건물 좌표 바닥 상자 */
    private final List<Room> rooms = new ArrayList<>();
    /** 1층 가게: {i0, i1, j0, j1} 와 쓰임 */
    private final List<int[]> units = new ArrayList<>();
    private final List<String> unitUse = new ArrayList<>();
    private final List<String> unitName = new ArrayList<>();

    private Office(String district, int w, int d, int floors, boolean podium, Random r) {
        this.district = district == null ? "" : district;
        this.w = w;
        this.d = d;
        this.floors = floors;
        this.r = r;
        this.name = name(this.district, floors, r);
        this.style = style(this.district, floors);
        this.podium = podium && floors >= 6;
        // 포디움: 1층 + 위 2~3개 층 (꾸미는 포디움 층은 최대 3)
        this.p = this.podium ? Math.min(3 + r.nextInt(2), floors - 3) : 0;
        this.piers = !(this.district.equals("yeouido") || this.district.equals("junggu"));
        layout();
        materials();
        uses();
    }

    // ------------------------------------------------------------------ 배치

    private void layout() {
        boolean slender = style == Style.RESIDENTIAL;
        if (podium) {
            // 포디움: 앞 보도 한 줄만 남기고 땅 가득
            px0 = 0;
            px1 = w - 1;
            pz0 = 0;
            pz1 = d - 2;
            int side = Math.max(0, Math.min(slender ? 12 : 6, (w - (slender ? 30 : 26)) / 2));
            int frontIn = Math.max(2, Math.min(slender ? 5 : 8, (d - 26) / 2 + 2));
            int backIn = Math.max(0, Math.min(slender ? 2 : 3, (d - 30) / 3));
            tx0 = px0 + side;
            tx1 = px1 - side;
            tz0 = pz0 + backIn;
            tz1 = pz1 - frontIn;
        } else {
            int fs = district.equals("gangnam") ? 6 + r.nextInt(3) : 3 + r.nextInt(3);
            fs = Math.max(2, Math.min(fs, d - 22));
            int ss = Math.max(1, Math.min(4, (w - 24) / 2 + 1));
            int bs = d - fs >= 26 ? 2 : 1;
            tx0 = ss;
            tx1 = w - 1 - ss;
            tz0 = bs;
            tz1 = d - 1 - fs;
            px0 = tx0;
            px1 = tx1;
            pz0 = tz0;
            pz1 = tz1;
        }
        if (slender) {
            // 집 타워 깊이는 세대 깊이에 맞춤: 84형 앞뒤 두 줄 48, 한 줄(코어 북쪽) 30, 오피스텔 앞뒤 원룸 32~36.
            // 남는 깊이는 포디움 옥상정원·마당으로 (타워를 가운데로)
            int avail = tz1 - tz0 + 1;
            int fit = style == Style.RESIDENTIAL
                    ? (avail >= 2 * APT_D + 16 ? 2 * APT_D + 16 : avail >= APT_D + 14 ? APT_D + 14 : avail)
                    : (avail >= 2 * STUDIO_D + 16 ? Math.min(avail, 2 * STUDIO_D + 20) : avail);
            tz0 += (avail - fit) / 2;
            tz1 = tz0 + fit - 1;
            if (!podium) {
                pz0 = tz0;
                pz1 = tz1;
            }
        }
        int tw = tx1 - tx0 + 1, td = tz1 - tz0 + 1;
        // 꼭대기·모서리
        if (style == Style.CURTAIN) {
            crown = floors >= 18 ? 1 + r.nextInt(3) : r.nextInt(2);
            chamfer = tw >= 30 && td >= 24 && r.nextInt(3) == 0 ? 2 + r.nextInt(2) : 0;
        } else if (style == Style.RESIDENTIAL) {
            crown = 1;
            chamfer = 0;
        }
        colonnade = !podium && style == Style.STONE && td >= 22 && r.nextInt(3) == 0;
        lobbyH = !podium && floors >= 20 && style != Style.RESIDENTIAL ? Floors.HALL : Floors.GROUND;
        // 코어 크기: 엘리베이터·화장실 수를 바닥 너비에 맞춤 (집 타워는 세대마다 욕실, 포디움 상가용 화장실 한 칸만)
        boolean home = style == Style.RESIDENTIAL;
        int want = floors >= 35 ? 4 : floors >= 20 ? 3 : floors >= 10 ? 2 : 1;
        elevators = home ? Math.min(3, Math.max(2, want)) : want;
        restrooms = home ? (podium ? 1 : 0) : 2;
        if (home) {
            // 앞뒤 두 줄 세대가 들어가면 코어 가운데, 아니면 코어를 북쪽에 붙이고 남쪽 한 줄 (복도식)
            int unitD = style == Style.RESIDENTIAL && td >= 29 ? APT_D : STUDIO_D;
            penthouse = td < 2 * unitD + 16 && floors - 1 > p + 1;
        }
        if (crown == 2 && floors - 3 > p + 2) {
            stepFrom = floors - 3;
        } else if (crown == 2) {
            crown = 1;
        }
        while (coreW() > topWidth() - 2) {
            if (elevators > 2) {
                elevators--;
            } else if (restrooms > 1) {
                restrooms--;
            } else if (elevators > 1) {
                elevators--;
            } else if (stepFrom != Integer.MAX_VALUE) {
                stepFrom = Integer.MAX_VALUE;
                crown = 1;
            } else {
                break;
            }
        }
        if (stepFrom != Integer.MAX_VALUE && (restrooms < 2 && !home)) {
            stepFrom = Integer.MAX_VALUE;
            crown = 1;
        }
        if (floors >= 30 && floors / 2 > p) {
            mechK = floors / 2;
        }
    }

    private int coreW() {
        return restrooms > 0 ? 9 + 4 * elevators + 8 * restrooms : 4 * elevators + 7;
    }

    private int topWidth() {
        int s = stepFrom != Integer.MAX_VALUE ? 4 : 0;
        return tx1 - tx0 + 1 - 2 * s;
    }

    /** 층 k 의 바닥판 */
    private boolean shapeIn(int i, int j, int k) {
        if (k < p) {
            return i >= px0 && i <= px1 && j >= pz0 && j <= pz1;
        }
        int a0 = tx0, a1 = tx1, b0 = tz0, b1 = tz1;
        if (penthouse && k == floors - 1) {
            b1 = Math.min(b1, tz0 + 11); // 코어(8줄)가 북쪽 벽에 붙게: Tower 는 맨 위층 가운데에 코어를 둠
        }
        if (k >= stepFrom) {
            int s = k >= floors - 1 ? 4 : 2;
            a0 += s;
            a1 -= s;
            b0 += s;
            b1 -= s;
        }
        if (k == 0 && colonnade) {
            b1 -= 2;
        }
        if (i < a0 || i > a1 || j < b0 || j > b1) {
            return false;
        }
        if (chamfer > 0) {
            int dx = Math.min(i - a0, a1 - i), dz = Math.min(j - b0, b1 - j);
            return dx + dz >= chamfer;
        }
        return true;
    }

    // ------------------------------------------------------------------ 재료

    private static Block b(String id, int rgb) {
        return Block.of(id, rgb);
    }

    private static final Block BRONZE = b("brown_stained_glass", 0x724728);
    private static final Block DARK_GLASS = b("black_stained_glass", 0x191919);
    private static final Block GRAY_GLASS = b("gray_stained_glass", 0x4C4C4C);
    private static final Block BLUE_GLASS = b("light_blue_stained_glass", 0x6699D8);
    private static final Block TEAL_GLASS = b("cyan_stained_glass", 0x4C7F99);
    private static final Block DEEP_BLUE = b("blue_stained_glass", 0x334CB2);
    private static final Block SILVER_GLASS = b("light_gray_stained_glass", 0x999999);
    private static final Block CLEAR = b("glass", 0xC8DCE4);
    private static final Block LOUVER = IRON_BARS;
    private static final Block TOP_SLAB = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);
    private static final Block LEDGE = Block.of("smooth_stone_slab[type=bottom,waterlogged=false]", 0x9E9E9E);

    private <T> T pick(T[] a) {
        return a[r.nextInt(a.length)];
    }

    private void materials() {
        bay = 3;
        mEvery = 3;
        switch (style) {
            case STONE -> {
                if (!piers && district.equals("yeouido")) {
                    // 여의도 증권가: 짙은 화강석 + 브론즈 유리, 세로 돌기둥이 튀어나옴
                    wall = pick(new Block[]{POLISHED_GRANITE, DEEPSLATE_TILES, b("brown_terracotta", 0x4D3323), POLISHED_DEEPSLATE});
                    glass = pick(new Block[]{BRONZE, DARK_GLASS, BRONZE, GRAY_GLASS});
                    fins = true;
                    bay = r.nextBoolean() ? 3 : 4;
                } else if (!piers) {
                    // 중구 1980년대: 돌·타일 벽에 쑥 들어간 창
                    wall = pick(new Block[]{POLISHED_GRANITE, b("white_terracotta", 0xD1B2A1), POLISHED_ANDESITE,
                            b("terracotta", 0x985E43), b("brown_terracotta", 0x4D3323)});
                    glass = pick(new Block[]{BRONZE, GRAY_GLASS, BRONZE, DARK_GLASS});
                    recessed = true;
                    bay = r.nextBoolean() ? 3 : 4;
                } else {
                    // 1990~2000년대: 밝은 화강석 기둥 사이 유리
                    wall = pick(new Block[]{POLISHED_ANDESITE, SMOOTH_STONE, POLISHED_DIORITE, POLISHED_GRANITE, POLISHED_ANDESITE});
                    glass = pick(new Block[]{BLUE_GLASS, TEAL_GLASS, GRAY_GLASS, DEEP_BLUE, SILVER_GLASS});
                    bay = r.nextBoolean() ? 4 : 5;
                }
                frame = wall;
                band = wall;
                spandrel = wall;
            }
            case BANDS -> {
                band = pick(new Block[]{WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, SMOOTH_STONE, b("white_terracotta", 0xD1B2A1)});
                glass = pick(new Block[]{GRAY_GLASS, CLEAR, SILVER_GLASS, BLUE_GLASS});
                frame = pick(new Block[]{GRAY_CONCRETE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE});
                wall = band;
                spandrel = band;
            }
            case PANEL -> {
                wall = pick(new Block[]{WHITE_CONCRETE, SMOOTH_QUARTZ, LIGHT_GRAY_CONCRETE, b("calcite", 0xDFE0DC), b("white_terracotta", 0xD1B2A1)});
                glass = pick(new Block[]{BLUE_GLASS, TEAL_GLASS, GRAY_GLASS, SILVER_GLASS});
                band = pick(new Block[]{LIGHT_GRAY_CONCRETE, GRAY_CONCRETE, POLISHED_ANDESITE, b("brown_terracotta", 0x4D3323)});
                frame = band;
                spandrel = band;
                louvers = officetel(district, floors) || r.nextInt(3) == 0;
            }
            case CURTAIN -> {
                Block[] glasses = switch (district) {
                    case "gangnam" -> new Block[]{TEAL_GLASS, DEEP_BLUE, DARK_GLASS, GRAY_GLASS, TEAL_GLASS, BLUE_GLASS};
                    case "yeouido" -> new Block[]{BLUE_GLASS, TEAL_GLASS, SILVER_GLASS, DEEP_BLUE, CLEAR};
                    case "junggu" -> new Block[]{GRAY_GLASS, CLEAR, BLUE_GLASS, SILVER_GLASS};
                    default -> new Block[]{BLUE_GLASS, TEAL_GLASS, GRAY_GLASS, SILVER_GLASS};
                };
                glass = pick(glasses);
                frame = pick(new Block[]{LIGHT_GRAY_CONCRETE, GRAY_CONCRETE, POLISHED_ANDESITE, BLACK_CONCRETE, IRON_BLOCK});
                // 띠(층 사이)는 유리와 비슷한 어두운 판이면 통유리처럼 보임
                spandrel = pick(new Block[]{frame, glass == DARK_GLASS ? DARK_GLASS : GRAY_GLASS, frame});
                band = frame;
                wall = frame;
                mEvery = r.nextBoolean() ? 2 : 3;
                fins = r.nextInt(3) == 0;
            }
            case RESIDENTIAL -> {
                frame = pick(new Block[]{WHITE_CONCRETE, SMOOTH_QUARTZ, b("calcite", 0xDFE0DC)});
                glass = pick(new Block[]{BLUE_GLASS, SILVER_GLASS, CLEAR, TEAL_GLASS});
                band = frame;
                wall = frame;
                spandrel = frame;
                balconies = true;
                bay = 5;
            }
        }
        // 포디움·1층
        podStone = switch (style) {
            case STONE -> piers ? pick(new Block[]{POLISHED_ANDESITE, POLISHED_GRANITE, POLISHED_DEEPSLATE}) : wall;
            case RESIDENTIAL -> pick(new Block[]{b("smooth_sandstone", 0xDFD6AA), POLISHED_ANDESITE, b("white_terracotta", 0xD1B2A1)});
            default -> pick(new Block[]{POLISHED_ANDESITE, POLISHED_GRANITE, SMOOTH_STONE, POLISHED_DEEPSLATE});
        };
        podFrame = pick(new Block[]{POLISHED_DEEPSLATE, POLISHED_BLACKSTONE, GRAY_CONCRETE});
        podGlass = style == Style.CURTAIN || (style == Style.STONE && piers) ? glass : pick(new Block[]{BLUE_GLASS, GRAY_GLASS, TEAL_GLASS});
    }

    private void uses() {
        shopUses = switch (district) {
            case "yeouido" -> new String[]{"은행", "카페", "편의점", "증권", "식당"};
            case "gangnam" -> new String[]{"카페", "은행", "편의점", "식당", "약국"};
            case "junggu" -> new String[]{"은행", "카페", "식당", "편의점", "약국"};
            case "yongsan" -> new String[]{"편의점", "카페", "부동산", "식당", "약국", "은행"};
            default -> new String[]{"편의점", "카페", "식당", "약국", "은행", "부동산"};
        };
        tenantUses = switch (district) {
            case "gangnam" -> new String[]{"병원", "학원", "운동", "식당", "치과"};
            case "yongsan" -> new String[]{"식당", "병원", "학원", "운동", "식당"};
            default -> new String[]{"식당", "병원", "학원", "운동", "치과"};
        };
    }

    // ------------------------------------------------------------------ 짓기

    private Voxels make() {
        Tower.Spec s = new Tower.Spec(w, d, floors);
        s.lobbyH = lobbyH;
        s.typicalH = Floors.OFFICE;
        s.shape = this::shapeIn;
        s.glass = glass;
        s.mullion = frame;
        s.spandrel = spandrel;
        s.podium = podStone;
        s.mullionEvery = mEvery;
        s.elevators = elevators;
        s.restrooms = restrooms;
        s.extraTop = 12;
        s.helipad = crown == 0 && floors >= 16 && !oldStyle();
        s.name = name;
        s.use = this::use;
        s.custom = this::custom;
        t = Tower.build(s, r);
        v = t.v;
        site();
        facade();
        groundFront();
        podiumSigns();
        shellCores();
        shellDress();
        roofs();
        openLoungeDoor();
        crown();
        restroomSigns();
        v.connect();
        return v;
    }

    /** 1970~80년대 돌·콘크리트 빌딩 (헬기장 없음, 옥상 물탱크·국기 게양대) */
    private boolean oldStyle() {
        return style == Style.BANDS || (style == Style.STONE && !piers);
    }

    /**
     * 안을 꾸미는 층인지. 7층 이하는 모든 층. 8층 이상은 1층 로비·가게, 포디움 층, 주상복합 맨 아래 주거 3개 층,
     * 펜트하우스 라운지(옥상정원)만 꾸미고, 나머지는 빈 껍데기(바닥판 + 외벽 + 계단·엘리베이터 코어).
     */
    boolean used(int k) {
        if (floors < 8 || k == 0 || k < p) {
            return true;
        }
        if (style == Style.RESIDENTIAL) {
            int home0 = Math.max(1, p);
            return (k >= home0 && k < home0 + 3 && k != mechK) || (penthouse && k == floors - 1);
        }
        return false;
    }

    private Tower.Use use(int k) {
        if (k < Math.max(1, p) || !used(k)) {
            return Tower.Use.CUSTOM; // 껍데기 층은 custom 이 아무것도 안 함
        }
        return style == Style.RESIDENTIAL ? Tower.Use.CUSTOM : Tower.Use.OFFICE;
    }

    private void custom(Tower tw, int k, int level, int h) {
        t = tw;
        v = tw.v;
        if (!used(k)) {
            return;
        }
        if (k == 0) {
            lobbyFloor(level, h);
        } else if (k < p) {
            tenantFloor(k, level, h);
        } else if (penthouse && k == floors - 1) {
            lounge(k, level, h);
        } else {
            homeFloor(k, level, h);
        }
    }

    // ------------------------------------------------------------------ 1층: 로비 + 가게

    private int cx() {
        return (t.ci0 + t.ci1) / 2;
    }

    private void lobbyFloor(int level, int h) {
        int[] bb = t.bounds(0);
        int x0 = bb[0] + 1, z0 = bb[1] + 1, x1 = bb[2] - 1, z1 = bb[3] - 1;
        int top = level + h - 2;
        int cx = cx();
        Block inner = Interior.INNER_WALL;
        // 구역: 코어 둘레(RING) + 정문에서 코어까지 로비 통로(cx±5) = 로비, 앞 줄(길 쪽 깊이 ≤ 14) = 가게들,
        // 나머지 뒤쪽 = 큰 세입자 하나 (포디움은 마트·식당가, 아니면 관리실), 로비와는 유리 칸막이와 문
        int rx0 = t.ci0 - 2, rx1 = t.ci1 + 2, rz0 = t.cj0 - 2, rz1 = t.cj1 + 2;
        int fz0 = Math.max(rz1 + 2, z1 - 13);
        boolean front = z1 - fz0 + 1 >= 5;
        int lw0 = Math.max(x0, cx - 5), lw1 = Math.min(x1, cx + 5);
        int[][] zone = new int[w][d]; // 0 밖, 1 로비, 2 앞 가게, 3 벽, 4 뒤
        for (int j = z0; j <= z1; j++) {
            for (int i = x0; i <= x1; i++) {
                if (!t.inside(i, j, 0) || t.edge(i, j, 0)) {
                    continue;
                }
                boolean ring = i >= rx0 && i <= rx1 && j >= rz0 && j <= rz1;
                boolean hall = i >= lw0 && i <= lw1 && j > rz1;
                if (ring || hall) {
                    zone[i][j] = 1;
                } else if (front && j >= fz0) {
                    zone[i][j] = (i == lw0 - 1 || i == lw1 + 1) ? 3 : 2;
                } else if (front && j == fz0 - 1) {
                    zone[i][j] = 3;
                } else {
                    zone[i][j] = 4;
                }
            }
        }
        // 벽: 앞 가게 줄 경계는 벽, 뒤 세입자와 로비 사이는 유리 칸막이
        Block pane = Kit.WHITE_PANE;
        int backCells = 0;
        for (int j = z0; j <= z1; j++) {
            for (int i = x0; i <= x1; i++) {
                if (zone[i][j] == 3) {
                    v.fill(i, level, j, i, top, j, inner);
                } else if (zone[i][j] == 4) {
                    backCells++;
                    if (zone[i - 1][j] == 1 || zone[i + 1][j] == 1 || zone[i][j - 1] == 1 || zone[i][j + 1] == 1) {
                        v.fill(i, level, j, i, top, j, pane);
                    }
                }
            }
        }
        // 뒤 세입자 문 (로비 쪽): 코어 북쪽 복도 가운데, 서·동쪽 복도, 로비 통로 옆
        int[][] doors = {{cx - 2, rz0 - 1}, {cx + 2, rz0 - 1}, {rx0 - 1, rz1 - 1}, {rx1 + 1, rz1 - 1},
                {lw0 - 1, rz1 + 2}, {lw1 + 1, rz1 + 2}};
        for (int[] dp : doors) {
            int i = dp[0], j = dp[1];
            if (i > x0 && i < x1 && j > z0 && j < z1 && zone[i][j] == 4) {
                boolean alongI = zone[i][j - 1] == 1 || zone[i][j + 1] == 1;
                v.fill(i, level, j, i + (alongI ? 1 : 0), level + 1, j + (alongI ? 0 : 1), AIR);
            }
        }
        if (backCells > 0) {
            String use = podium ? (backCells >= 300 ? (r.nextBoolean() ? "마트" : "식당가") : "식당") : backCells >= 120 ? "식당" : "관리실";
            int[][] zz = zone;
            back(use, (i, j) -> i >= 0 && j >= 0 && i < w && j < d && zz[i][j] == 4, x0, z0, x1, z1, level, h);
        }
        // 앞 가게들: 로비 통로 양옆을 9칸 안팎으로 나눔
        List<int[]> blocks = new ArrayList<>();
        if (front) {
            if (lw0 - 2 - x0 + 1 >= 4) {
                blocks.add(new int[]{x0, lw0 - 2, fz0, z1});
            }
            if (x1 - (lw1 + 2) + 1 >= 4) {
                blocks.add(new int[]{lw1 + 2, x1, fz0, z1});
            }
        }
        int u = r.nextInt(shopUses.length);
        for (int[] blk : blocks) {
            int width = blk[1] - blk[0] + 1;
            int n = Math.max(1, Math.round(width / 9f));
            int each = (width + 1) / n;
            for (int q = 0; q < n; q++) {
                int a0 = blk[0] + q * each, a1 = q == n - 1 ? blk[1] : a0 + each - 2;
                if (q < n - 1) {
                    v.fill(a1 + 1, level, blk[2], a1 + 1, top, blk[3], inner);
                }
                if (a1 - a0 < 3) {
                    continue;
                }
                String use = shopUses[u++ % shopUses.length];
                units.add(new int[]{a0, a1, blk[2], blk[3]});
                unitUse.add(use);
                unitName.add(shopName(use));
                shop(use, a0, a1, blk[2], blk[3], level, h);
            }
        }
        // 로비: 돌 바닥, 안내 데스크, 출입 게이트, 소파, 화분, 층 안내판
        Frame f = Frame.of(v);
        Block floor = pick(new Block[]{POLISHED_DIORITE, POLISHED_ANDESITE, SMOOTH_QUARTZ, POLISHED_GRANITE});
        for (int j = z0; j <= z1; j++) {
            for (int i = x0; i <= x1; i++) {
                if (zone[i][j] == 1 && v.get(i, level, j) == null && !(i >= t.ci0 && i <= t.ci1 && j >= t.cj0 && j <= t.cj1)) {
                    v.set(i, level - 1, j, floor);
                }
            }
        }
        // 게이트: 코어 앞 복도 남쪽 줄, 한 칸 걸러 낮은 기둥
        int gz = t.corridorJ + 3;
        if (gz < z1 - 2) {
            for (int i = cx - 4; i <= cx + 4; i += 2) {
                if (t.inside(i, gz, 0) && v.get(i, level, gz) == null) {
                    v.set(i, level, gz, Block.of("smooth_quartz", 0xECE6DF));
                    v.set(i, level + 1, gz, Block.of("light_gray_stained_glass_pane", 0x999999));
                }
            }
            // 안내 데스크 (게이트 앞 동쪽)
            int dz = Math.min(z1 - 1, gz + 3);
            for (int i = cx + 3; i <= cx + 5; i++) {
                if (t.free(i, dz, 0) && v.get(i, level, dz) == null) {
                    v.set(i, level, dz, Furniture.COUNTER);
                }
            }
            if (t.free(cx + 4, dz - 1, 0) && v.get(cx + 4, level, dz - 1) == null) {
                Furniture.chair(f, cx + 4, level, dz - 1, "north", "dark_oak");
            }
            // 기다리는 소파 (서쪽)
            if (z1 - 2 > dz && t.free(cx - 5, z1 - 2, 0)) {
                Furniture.sofa(f, r, cx - 5, level, z1 - 2, 3, "north");
                Furniture.plant(f, r, cx - 6, level, z1 - 2);
            }
        }
        Furniture.plant(f, r, cx - 2, level, z1);
        Furniture.plant(f, r, cx + 2, level, z1);
        // 층 안내판 (엘리베이터 옆)
        String[] dir = directory();
        if (v.get(t.ci0 + 6, level + 1, t.corridorJ) == null) {
            v.set(t.ci0 + 6, level + 1, t.corridorJ, Blocks.wallSign("dark_oak", "south", "white", true, dir));
        }
        // 등
        for (int j = z0 + 2; j <= z1; j += 4) {
            for (int i = x0 + 2; i <= x1 - 1; i += 4) {
                if (zone[i][j] == 1 && t.free(i, j, 0) && v.get(i, top, j) == null && v.get(i, level, j) == null) {
                    v.set(i, top, j, Interior.LIGHT);
                }
            }
        }
    }

    private String[] directory() {
        if (p > 0) {
            String up = style == Style.RESIDENTIAL ? "주거" : officetel(district, floors) ? "오피스텔" : "사무실";
            return new String[]{name, "1층 상가", "2-" + p + "층 상가", (p + 1) + "-" + floors + "층 " + up};
        }
        return new String[]{name, "", "2-" + floors + "층", officetel(district, floors) ? "오피스텔" : "사무실"};
    }

    private String shopName(String use) {
        String pre = KoreanNames.prefix(r);
        return switch (use) {
            case "은행" -> pick(new String[]{"준서은행", pre + "은행", "한빛은행"});
            case "카페" -> "카페 " + pre;
            case "편의점" -> pick(new String[]{"준서마트24", "24시 편의점", pre + "24"});
            case "증권" -> pre + "증권";
            case "약국" -> pre + "약국";
            case "부동산" -> pre + "부동산";
            default -> pick(new String[]{pre + "식당", "한식 " + pre, pre + "국수", pre + "김밥", pre + "돈까스"});
        };
    }

    /** 1층 가게 하나 (a0..a1 × b0..b1, 정면은 b1 쪽 바깥 벽) */
    private void shop(String use, int a0, int a1, int b0, int b1, int level, int h) {
        Frame f = Frame.of(v);
        int top = level + h - 2;
        java.util.function.BiPredicate<Integer, Integer> ok = (i, j) -> i >= a0 && i <= a1 && j >= b0 && j <= b1
                && t.free(i, j, 0) && v.get(i, level, j) == null;
        Block floorB = switch (use) {
            case "카페" -> Block.of("spruce_planks", 0x725430);
            case "식당" -> Block.of("birch_planks", 0xC0AF79);
            case "은행", "증권" -> POLISHED_ANDESITE;
            default -> Block.of("white_concrete", 0xCFD5D6);
        };
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                if (v.get(i, level, j) == null && !(i >= t.ci0 - 1 && i <= t.ci1 + 1 && j >= t.cj0 - 1 && j <= t.cj1 + 1)) {
                    v.set(i, level - 1, j, floorB);
                }
            }
        }
        int mid = (a0 + a1) / 2;
        switch (use) {
            case "편의점" -> {
                // 계산대 (문 옆), 진열대 줄, 뒤 벽 음료 냉장고
                for (int j = b1 - 3; j <= b1 - 2; j++) {
                    if (ok.test(a0 + 1, j)) {
                        v.set(a0 + 1, level, j, Furniture.COUNTER);
                    }
                }
                for (int i = a0 + 3; i <= a1 - 1; i += 3) {
                    for (int j = b0 + 3; j <= b1 - 4; j++) {
                        if (ok.test(i, j)) {
                            v.set(i, level, j, Furniture.WHITE_TOP);
                            v.set(i, level + 1, j, (j & 1) == 0 ? BARREL : Furniture.BOOKSHELF);
                        }
                    }
                }
                for (int i = a0; i <= a1; i++) {
                    if (ok.test(i, b0)) {
                        v.set(i, level, b0, Block.of("white_concrete", 0xCFD5D6));
                        v.set(i, level + 1, b0, CLEAR);
                    }
                }
            }
            case "카페" -> {
                for (int i = a0 + 1; i <= Math.min(a1 - 1, a0 + 4); i++) {
                    if (ok.test(i, b0 + 2)) {
                        v.set(i, level, b0 + 2, Furniture.COUNTER);
                    }
                }
                if (ok.test(a0 + 1, b0)) {
                    v.set(a0 + 1, level, b0, Block.of("smoker[facing=south,lit=false]", 0x555451)); // 커피 기계
                }
                for (int j = b0 + 5; j <= b1 - 2; j += 3) {
                    for (int i = a0 + 1; i + 1 <= a1 - 1; i += 4) {
                        if (ok.test(i, j) && ok.test(i + 1, j) && ok.test(i, j - 1) && ok.test(i, j + 1)) {
                            Furniture.table(f, i, level, j, 2, 1, "oak");
                        }
                    }
                }
                for (int j = b0 + 4; j <= b1 - 1; j += 4) {
                    if (ok.test(mid, j)) {
                        v.set(mid, top, j, LANTERN_HANGING);
                    }
                }
                Furniture.plant(f, r, a1, level, b1);
            }
            case "은행", "증권" -> {
                // 창구: 뒤쪽 가로 줄 계산대 + 유리 칸막이, 직원 의자, 손님 의자 줄, 앞 ATM
                int cz = b0 + 3;
                for (int i = a0 + 1; i <= a1 - 1; i++) {
                    if (ok.test(i, cz)) {
                        v.set(i, level, cz, Furniture.COUNTER);
                        if ((i - a0) % 3 == 0) {
                            v.set(i, level + 1, cz, Kit.WHITE_PANE);
                        }
                    }
                    if ((i - a0) % 3 == 2 && ok.test(i, cz - 1)) {
                        Furniture.chair(f, i, level, cz - 1, "north", "dark_oak");
                    }
                }
                for (int j = cz + 3; j <= b1 - 3; j += 2) {
                    for (int i = a0 + 2; i <= a1 - 2; i++) {
                        if (ok.test(i, j) && (i - a0) % 5 != 0) {
                            Furniture.chair(f, i, level, j, "south", "birch");
                        }
                    }
                }
                if (use.equals("증권")) {
                    v.fill(a0 + 1, level + 1, b0, a1 - 1, level + 2, b0, BLACK_CONCRETE); // 시세판
                } else {
                    for (int j = b1 - 2; j <= b1 - 1; j++) {
                        if (ok.test(a1, j)) {
                            v.set(a1, level, j, IRON_BLOCK);
                            v.set(a1, level + 1, j, Block.of("light_gray_concrete", 0x7D7D73));
                        }
                    }
                }
            }
            case "약국" -> {
                for (int i = a0 + 1; i <= a1 - 1; i++) {
                    if (ok.test(i, b0 + 2)) {
                        v.set(i, level, b0 + 2, Furniture.COUNTER);
                    }
                    if (ok.test(i, b0)) {
                        v.set(i, level, b0, Furniture.BOOKSHELF);
                        v.set(i, level + 1, b0, Furniture.BOOKSHELF);
                    }
                }
                for (int j = b0 + 4; j <= b1 - 3; j++) {
                    if (ok.test(a1, j)) {
                        v.set(a1, level, j, Furniture.WHITE_TOP);
                        v.set(a1, level + 1, j, BARREL);
                    }
                }
            }
            case "부동산" -> {
                for (int i = a0 + 1; i <= a1 - 1; i += 2) {
                    if (ok.test(i, b0 + 2) && ok.test(i, b0 + 3)) {
                        Furniture.desk(f, i, level, b0 + 2, "south");
                    }
                }
                if (ok.test(a0 + 1, b1 - 2)) {
                    Furniture.sofa(f, r, a0 + 1, level, b1 - 2, Math.min(3, a1 - a0 - 1), "north");
                }
            }
            default -> { // 식당
                for (int i = a0; i <= a1; i++) {
                    if (ok.test(i, b0)) {
                        v.set(i, level, b0, i == a0 + 1 ? SMOKER : i == a0 + 3 ? CAULDRON : Furniture.COUNTER);
                    }
                }
                for (int j = b0 + 3; j <= b1 - 2; j += 3) {
                    for (int i = a0 + 1; i + 1 <= a1 - 1; i += 4) {
                        if (ok.test(i, j) && ok.test(i + 1, j) && ok.test(i, j - 1) && ok.test(i, j + 1)) {
                            Furniture.table(f, i, level, j, 2, 1, "spruce");
                        }
                    }
                }
            }
        }
        for (int j = b0 + 2; j <= b1; j += 4) {
            for (int i = a0 + 1; i <= a1; i += 4) {
                if (v.get(i, top, j) == null && v.get(i, level, j) == null && ok.test(i, j)) {
                    v.set(i, top, j, Interior.LIGHT);
                }
            }
        }
    }

    /**
     * 1층 뒤쪽 큰 세입자 (in 이 참인 칸들): 마트(진열대 줄, 뒷벽 냉장고, 계산대), 식당가·식당(뒷벽 주방, 식탁),
     * 관리실(책상, 우편함). 칸막이·벽 바로 안쪽 한 줄은 비워 문을 막지 않음.
     */
    private void back(String use, java.util.function.BiPredicate<Integer, Integer> in, int x0, int z0, int x1, int z1, int level, int h) {
        Frame f = Frame.of(v);
        int top = level + h - 2;
        java.util.function.BiPredicate<Integer, Integer> deep = (i, j) -> in.test(i, j) && in.test(i - 1, j) && in.test(i + 1, j)
                && in.test(i, j - 1) && in.test(i, j + 1) && t.free(i, j, 0) && v.get(i, level, j) == null;
        Block fl = switch (use) {
            case "마트" -> Block.of("white_concrete", 0xCFD5D6);
            case "관리실" -> Block.of("light_gray_concrete", 0x7D7D73);
            default -> Block.of("birch_planks", 0xC0AF79);
        };
        for (int j = z0; j <= z1; j++) {
            for (int i = x0; i <= x1; i++) {
                if (in.test(i, j) && v.get(i, level, j) == null) {
                    v.set(i, level - 1, j, fl);
                }
            }
        }
        switch (use) {
            case "마트" -> {
                for (int j = z0; j <= z1; j++) {
                    for (int i = x0; i <= x1; i++) {
                        if (!deep.test(i, j)) {
                            // 바깥 벽(북쪽)에 붙은 칸: 음료·냉장 진열장
                            if (in.test(i, j) && !in.test(i, j - 1) && v.get(i, level, j) == null && solidWall(i, level + 1, j - 1)) {
                                v.set(i, level, j, Block.of("white_concrete", 0xCFD5D6));
                                v.set(i, level + 1, j, CLEAR);
                            }
                            continue;
                        }
                        if ((i - x0) % 4 == 2 && (j - z0) % 9 != 0 && (j - z0) % 9 != 8) {
                            v.set(i, level, j, Furniture.WHITE_TOP);
                            v.set(i, level + 1, j, (j & 1) == 0 ? BARREL : MELON);
                        }
                    }
                }
            }
            case "관리실" -> {
                for (int j = z0 + 1; j <= z1; j += 4) {
                    for (int i = x0 + 1; i <= x1; i += 3) {
                        if (deep.test(i, j) && deep.test(i, j + 1)) {
                            Furniture.desk(f, i, level, j, "south");
                        }
                    }
                }
                for (int i = x0; i <= x1; i++) {
                    if (in.test(i, z0) && !in.test(i, z0 - 1) && v.get(i, level, z0) == null) {
                        v.set(i, level + 1, z0, Block.of("spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]", 0x725430)); // 우편함
                    }
                }
            }
            default -> { // 식당가·식당
                for (int i = x0; i <= x1; i++) {
                    for (int j = z0; j <= z1; j++) {
                        if (in.test(i, j) && !in.test(i, j - 1) && v.get(i, level, j) == null && solidWall(i, level + 1, j - 1)) {
                            v.set(i, level, j, (i - x0) % 5 == 1 ? SMOKER : (i - x0) % 5 == 3 ? CAULDRON : Furniture.COUNTER);
                            v.set(i, level + 2, j, Furniture.COUNTER);
                            break;
                        }
                    }
                }
                for (int j = z0 + 3; j <= z1; j += 3) {
                    for (int i = x0 + 1; i <= x1; i += 4) {
                        if (deep.test(i, j) && deep.test(i + 1, j) && deep.test(i, j - 1) && deep.test(i + 1, j - 1)
                                && deep.test(i, j + 1) && deep.test(i + 1, j + 1)) {
                            Furniture.table(f, i, level, j, 2, 1, "dark_oak");
                        }
                    }
                }
            }
        }
        for (int j = z0 + 1; j <= z1; j += 4) {
            for (int i = x0 + 1; i <= x1; i += 4) {
                if (in.test(i, j) && t.free(i, j, 0) && v.get(i, top, j) == null && v.get(i, level, j) == null) {
                    v.set(i, top, j, Interior.LIGHT);
                }
            }
        }
    }

    private boolean solidWall(int i, int y, int j) {
        Block b = v.get(i, y, j);
        return b != null && !b.isAir() && !b.id().endsWith("_pane");
    }

    // ------------------------------------------------------------------ 포디움 위층: 식당·병원·학원·운동

    private void tenantFloor(int k, int level, int h) {
        int[] bb = t.bounds(k);
        int x0 = bb[0] + 1, z0 = bb[1] + 1, x1 = bb[2] - 1, z1 = bb[3] - 1;
        int top = level + h - 2;
        int cx = cx();
        int rx0 = t.ci0 - 3, rx1 = t.ci1 + 3, rz0 = t.cj0 - 3, rz1 = t.cj1 + 3;
        Block pane = Kit.WHITE_PANE;
        // 공용 복도(코어 둘레) 유리 칸막이
        for (int i = rx0; i <= rx1; i++) {
            for (int j : new int[]{rz0, rz1}) {
                if (i >= x0 && i <= x1 && j >= z0 && j <= z1) {
                    v.fill(i, level, j, i, top, j, pane);
                }
            }
        }
        for (int j = rz0; j <= rz1; j++) {
            for (int i : new int[]{rx0, rx1}) {
                if (i >= x0 && i <= x1 && j >= z0 && j <= z1) {
                    v.fill(i, level, j, i, top, j, pane);
                }
            }
        }
        // 가운데 벽 (서쪽·동쪽 가게)
        v.fill(cx, level, z0, cx, top, rz0 - 1, Interior.INNER_WALL);
        v.fill(cx, level, rz1 + 1, cx, top, z1, Interior.INNER_WALL);
        // 문 (복도에서 가게로)
        for (int[] dpos : new int[][]{{cx - 3, rz1}, {cx + 3, rz1}, {rx0, t.corridorJ}, {rx1, t.corridorJ}, {cx - 3, rz0}, {cx + 3, rz0}}) {
            int i = dpos[0], j = dpos[1];
            if (i >= x0 && i <= x1 && j >= z0 && j <= z1) {
                v.fill(i, level, j, i + (j == rz0 || j == rz1 ? 1 : 0), level + 1, j + (j == rz0 || j == rz1 ? 0 : 1), AIR);
            }
        }
        // 화장실이 코어에 없으면 (주거 타워) 복도 북쪽에 남녀 화장실
        if (restrooms == 0 && rz0 - z0 >= 6) {
            int b1 = rz0 - 1, b0 = Math.max(z0 + 1, b1 - 5);
            Frame f = Frame.of(v);
            if (cx - 9 >= x0 + 1) {
                v.fill(cx - 10, level, b0 - 1, cx - 2, top, rz0, AIR);
                Interior.restroom(f, cx - 9, b0, cx - 3, b1 - 1, level, h, "남자 화장실", cx - 6);
                v.fill(cx - 7, level, b1 + 1, cx - 5, level + 1, rz0, AIR);
            }
            if (cx + 9 <= x1 - 1) {
                v.fill(cx + 2, level, b0 - 1, cx + 10, top, rz0, AIR);
                Interior.restroom(f, cx + 3, b0, cx + 9, b1 - 1, level, h, "여자 화장실", cx + 6);
                v.fill(cx + 5, level, b1 + 1, cx + 7, level + 1, rz0, AIR);
            }
        }
        String west = tenantUses[(k * 2) % tenantUses.length], east = tenantUses[(k * 2 + 1) % tenantUses.length];
        tenant(west, k, level, h, x0, cx - 1, z0, z1);
        tenant(east, k, level, h, cx + 1, x1, z0, z1);
        unitUseByFloor.add(new String[]{west, east});
    }

    private final List<String[]> unitUseByFloor = new ArrayList<>();
    /** 펜트하우스 라운지 → 옥상정원 문 {i, y, j} */
    private int[] loungeDoor;

    private void tenant(String use, int k, int level, int h, int a0, int a1, int b0, int b1) {
        Frame f = Frame.of(v);
        int top = level + h - 2;
        java.util.function.BiPredicate<Integer, Integer> ok = (i, j) -> i >= a0 && i <= a1 && j >= b0 && j <= b1
                && t.free(i, j, k) && v.get(i, level, j) == null && !nearRing(i, j);
        Block fl = switch (use) {
            case "식당" -> Block.of("birch_planks", 0xC0AF79);
            case "운동" -> Block.of("gray_concrete", 0x36393D);
            default -> Block.of("white_concrete", 0xCFD5D6);
        };
        for (int j = b0; j <= b1; j++) {
            for (int i = a0; i <= a1; i++) {
                if (t.free(i, j, k) && v.get(i, level, j) == null) {
                    v.set(i, level - 1, j, fl);
                }
            }
        }
        switch (use) {
            case "식당" -> {
                for (int i = a0; i <= a1; i++) {
                    if (ok.test(i, b0)) {
                        v.set(i, level, b0, (i - a0) % 6 == 1 ? SMOKER : (i - a0) % 6 == 3 ? CAULDRON : Furniture.COUNTER);
                    }
                }
                for (int j = b0 + 3; j <= b1 - 1; j += 3) {
                    for (int i = a0 + 1; i + 1 <= a1 - 1; i += 4) {
                        if (ok.test(i, j) && ok.test(i + 1, j) && ok.test(i, j - 1) && ok.test(i, j + 1) && ok.test(i + 1, j + 1)) {
                            Furniture.table(f, i, level, j, 2, 1, "spruce");
                        }
                    }
                }
            }
            case "병원", "치과" -> {
                // 바깥 벽 쪽 진료 침대 줄(커튼), 복도 쪽 대기 의자, 접수대
                for (int i = a0 + 1; i <= a1 - 1; i += 3) {
                    int j = b1 - 1;
                    if (ok.test(i, j) && ok.test(i + 1, j)) {
                        if (use.equals("치과")) {
                            v.set(i, level, j, Block.of("quartz_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
                            v.set(i + 1, level, j, IRON_BLOCK);
                        } else {
                            v.set(i, level, j, Block.of("white_wool", 0xE9ECEC));
                            v.set(i + 1, level, j, Block.of("white_wool", 0xE9ECEC));
                        }
                        if (ok.test(i + 2, j)) {
                            v.set(i + 2, level, j, Kit.CURTAIN);
                            v.set(i + 2, level + 1, j, Kit.CURTAIN);
                        }
                    }
                    j = b0 + 1;
                    if (ok.test(i, j) && ok.test(i + 1, j)) {
                        v.set(i, level, j, Block.of("white_wool", 0xE9ECEC));
                        v.set(i + 1, level, j, Block.of("white_wool", 0xE9ECEC));
                    }
                }
                for (int j = b0 + 4; j <= b1 - 4; j += 3) {
                    for (int i = a0 + 2; i <= a1 - 2; i++) {
                        if (ok.test(i, j) && (i - a0) % 6 != 0) {
                            Furniture.chair(f, i, level, j, "north", "birch");
                        }
                    }
                }
            }
            case "학원" -> {
                // 칸막이 교실 (바깥 벽 쪽 줄), 칠판, 책상
                for (int i = a0; i <= a1; i++) {
                    for (int j = b0; j <= b1; j++) {
                        if (ok.test(i, j) && (i - a0) % 8 == 7) {
                            v.fill(i, level, j, i, top, j, Interior.INNER_WALL);
                        }
                    }
                }
                for (int i = a0 + 1; i <= a1 - 1; i++) {
                    if ((i - a0) % 8 == 7) {
                        continue;
                    }
                    for (int j = b0 + 1; j <= b1 - 1; j += 2) {
                        if (ok.test(i, j) && (i - a0) % 8 >= 2) {
                            v.set(i, level, j, Furniture.DESK_TOP);
                        }
                    }
                    if ((i - a0) % 8 == 0 && ok.test(i, b0)) {
                        v.set(i, level + 1, b0, Block.of("green_concrete", 0x495B24));
                    }
                }
            }
            default -> { // 운동 (필라테스·헬스)
                for (int i = a0 + 1; i <= a1 - 1; i += 2) {
                    int j = b1 - 1;
                    if (ok.test(i, j)) {
                        v.set(i, level, j, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
                        v.set(i, level + 1, j, IRON_BARS);
                    }
                }
                for (int j = b0 + 2; j <= b1 - 4; j++) {
                    for (int i = a0 + 2; i <= a1 - 2; i++) {
                        if (ok.test(i, j) && (j - b0) % 3 != 1) {
                            v.set(i, level, j, Block.of("blue_carpet", 0x35399D));
                        }
                    }
                }
                for (int i = a0; i <= a1; i++) {
                    if (ok.test(i, b0)) {
                        v.fill(i, level, b0, i, level + 1, b0, CLEAR); // 거울 벽
                    }
                }
            }
        }
        for (int j = b0 + 1; j <= b1; j += 4) {
            for (int i = a0 + 1; i <= a1; i += 4) {
                if (t.free(i, j, k) && v.get(i, top, j) == null && v.get(i, level, j) == null) {
                    v.set(i, top, j, Interior.LIGHT);
                }
            }
        }
    }

    /** 공용 복도 유리 칸막이 바로 바깥 한 칸 (문 앞을 비움) */
    private boolean nearRing(int i, int j) {
        return i >= t.ci0 - 4 && i <= t.ci1 + 4 && j >= t.cj0 - 4 && j <= t.cj1 + 4;
    }

    // ------------------------------------------------------------------ 집 층: 복도 앞뒤 세대 (원룸·84형)

    /** 원룸 최소 안쪽 6×8 (욕실 포함), 84형 최소 안쪽 13×16 (방 줄 + 거실 6 + 발코니) */
    static final int STUDIO_W = 6, STUDIO_D = 8, APT_W = 13, APT_D = 16;

    private void homeFloor(int k, int level, int h) {
        int[] bb = t.bounds(k);
        int x0 = bb[0] + 1, x1 = bb[2] - 1;
        band(k, level, h, x0, x1, t.corridorJ + 3, bb[3] - 1, true);
        band(k, level, h, x0, x1, bb[1] + 1, t.cj0 - 4, false);
        if (restrooms == 1) {
            recycling(level, h);
        }
    }

    /** 복도 한쪽 세대 줄: j 범위 b0..b1 (south 면 복도 벽은 b0-1, 아니면 b1+1) */
    private void band(int k, int level, int h, int x0, int x1, int b0, int b1, boolean south) {
        int depth = b1 - b0 + 1, width = x1 - x0 + 1;
        boolean apt = style == Style.RESIDENTIAL && depth >= APT_D && width >= APT_W;
        if (!apt && (depth < STUDIO_D || width < STUDIO_W)) {
            return; // 세대가 안 들어가는 얕은 띠는 공용 공간으로 둠
        }
        int minW = apt ? APT_W : depth >= 12 ? 8 : STUDIO_W;
        int top = level + h - 2;
        Block wallB = Interior.INNER_WALL;
        int wallJ = south ? b0 - 1 : b1 + 1;
        for (int i = x0; i <= x1; i++) {
            if (t.inside(i, wallJ, k) && !t.edge(i, wallJ, k)) {
                v.fill(i, level, wallJ, i, top, wallJ, wallB);
            }
        }
        int n = Math.max(1, (width + 1) / (minW + 1));
        int each = (width - (n - 1)) / n;
        for (int u = 0; u < n; u++) {
            int a0 = x0 + u * (each + 1), a1 = u == n - 1 ? x1 : a0 + each - 1;
            if (u < n - 1) {
                v.fill(a1 + 1, level, b0, a1 + 1, top, b1, wallB);
            }
            int uw = a1 - a0 + 1;
            Frame f = south ? Frame.facing(v, a0, b0, "south") : Frame.facing(v, a1, b1, "north");
            if (apt) {
                apt84(f, uw, depth, level, h);
            } else {
                studio(f, uw, depth, level, h);
            }
        }
    }

    /** 방 기록 (Frame 좌표 상자 → 건물 좌표) */
    private void room(Frame f, String type, int a0, int b0, int a1, int b1, int level) {
        int i0 = f.i(a0, b0), j0 = f.j(a0, b0), i1 = f.i(a1, b1), j1 = f.j(a1, b1);
        rooms.add(new Room(type, Math.min(i0, i1), Math.min(j0, j1), Math.max(i0, i1), Math.max(j0, j1), level));
    }

    /**
     * 원룸 (안쪽 W×D, W ≥ 6, D ≥ 8): 현관문 b = -1 (a = W-4), 현관 옆 욕실 2×3 (변기·세면대·샤워),
     * 서쪽 벽 부엌(조리대·개수대·가스레인지·냉장고), 옷장, 창가 침대와 책상.
     */
    private void studio(Frame f, int W, int D, int level, int h) {
        int top = level + h - 2;
        Block wallB = Interior.INNER_WALL;
        Interior.floor(f, 0, 0, W - 1, D - 1, level, r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        Interior.door(f, W - 4, level, -1, "spruce", "south");
        // 욕실 [W-2..W-1]×[0..2]
        f.fill(W - 3, level, 0, W - 3, top, 3, wallB);
        f.fill(W - 3, level, 3, W - 1, top, 3, wallB);
        Interior.door(f, W - 3, level, 1, "pale_oak", "east");
        Interior.floor(f, W - 2, 0, W - 1, 2, level, Interior.TILE);
        f.set(W - 2, level, 0, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
        f.set(W - 1, level + 2, 0, Block.of("tripwire_hook[attached=false,facing=south,powered=false]", 0x8F8F8F)); // 샤워기
        f.set(W - 1, level, 2, CAULDRON);
        f.set(W - 1, level + 1, 3, Block.of("light_blue_stained_glass", 0x6699D8)); // 거울
        f.set(W - 2, top, 1, Interior.LIGHT);
        // 부엌 (서쪽 벽)
        f.set(0, level, 0, Furniture.COUNTER);
        f.set(0, level, 1, CAULDRON);
        f.set(0, level, 2, Block.of("smoker[facing=east,lit=false]", 0x555451));
        f.set(0, level, 3, Furniture.FRIDGE);
        f.set(0, level + 1, 3, Furniture.FRIDGE);
        f.fill(0, level + 2, 0, 0, level + 2, 2, Furniture.COUNTER);
        // 옷장, 침대, 책상
        f.set(0, level, 5, Rooms.WARDROBE);
        f.set(0, level + 1, 5, Rooms.WARDROBE);
        Furniture.bed(f, r, 0, level, D - 1, "east");
        Furniture.desk(f, W - 1, level, D - 1, "west");
        Furniture.plant(f, r, W - 1, level, D - 3);
        f.set(W / 2 - 1, top, D / 2 + 1, Interior.LIGHT);
        room(f, "원룸", 0, 0, W - 1, D - 1, level);
        room(f, "욕실", W - 2, 0, W - 1, 2, level);
    }

    /**
     * 84㎡형 한 세대 (안쪽 W×D, W ≥ 13, D ≥ 16). 현관문은 b = -1 (a = W-2), 창은 b = D.
     * <pre>
     *  b 0..3  : 침실2 [0..2] | 침실3 [4..6] | 욕실 [8..9]×[0..2] | (다용도) | 현관 [W-2..W-1]×[0..1]
     *  b 4..7  : 복도·주방·식당 (주방은 동쪽 벽, 식탁)
     *  b 8..D-3: 안방 [0..3] | 거실 [5..W-1]
     *  b D-2   : 유리 칸막이 (미닫이 자리 트임), b D-1: 발코니 (세탁기, 화분)
     * </pre>
     */
    private void apt84(Frame f, int W, int D, int level, int h) {
        int top = level + h - 2;
        Block wallB = Interior.INNER_WALL;
        Block pane = Block.of("glass_pane", 0xC8DCE4);
        int fr = D - 3; // 거실·안방 마지막 줄
        Interior.floor(f, 0, 0, W - 1, D - 1, level, r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT);
        // 벽
        f.fill(3, level, 0, 3, top, 4, wallB);
        f.fill(7, level, 0, 7, top, 4, wallB);
        f.fill(0, level, 4, 7, top, 4, wallB);
        f.fill(10, level, 0, 10, top, 3, wallB);
        f.fill(8, level, 3, 10, top, 3, wallB);
        f.fill(0, level, 7, 4, top, 7, wallB);
        f.fill(4, level, 8, 4, top, D - 2, wallB);
        f.fill(0, level, D - 2, W - 1, top, D - 2, pane);
        // 문
        Interior.door(f, W - 2, level, -1, "spruce", "south");
        Interior.door(f, 1, level, 4, "pale_oak", "south");
        Interior.door(f, 5, level, 4, "pale_oak", "south");
        Interior.door(f, 9, level, 3, "pale_oak", "south");
        Interior.door(f, 3, level, 7, "pale_oak", "south");
        f.fill(2, level, D - 2, 2, level + 1, D - 2, AIR);              // 안방 → 발코니
        f.fill(W - 3, level, D - 2, W - 3, level + 1, D - 2, AIR);      // 거실 → 발코니
        // 바닥 마감: 현관·욕실·발코니 타일
        Interior.floor(f, W - 2, 0, W - 1, 1, level, Interior.TILE);
        Interior.floor(f, 8, 0, 9, 2, level, Interior.TILE);
        Interior.floor(f, 0, D - 1, W - 1, D - 1, level, Block.of("light_gray_terracotta", 0x876A61));
        // 침실2·3: 침대, 옷장, 책상
        for (int a : new int[]{0, 4}) {
            Furniture.bed(f, r, a, level, 0, "south");
            f.set(a + 2, level, 0, Rooms.WARDROBE);
            f.set(a + 2, level + 1, 0, Rooms.WARDROBE);
            Furniture.desk(f, a + 2, level, 2, "west");
            f.set(a + 1, top, 2, Interior.LIGHT);
        }
        // 욕실: 샤워 (북서 구석), 변기, 세면대
        f.set(8, level + 2, 0, Block.of("tripwire_hook[attached=false,facing=south,powered=false]", 0x8F8F8F));
        f.set(9, level, 0, Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
        f.set(8, level, 2, CAULDRON);
        f.set(7, level + 1, 2, Block.of("light_blue_stained_glass", 0x6699D8)); // 거울
        f.set(9, top, 1, Interior.LIGHT);
        // 현관: 신발장
        f.set(W - 1, level, 2, Furniture.COUNTER);
        f.set(W - 1, top, 1, Interior.LIGHT);
        // 주방 (동쪽 벽): 냉장고, 개수대, 가스레인지, 조리대 + 위 수납장, 식탁
        f.set(W - 1, level, 4, Furniture.FRIDGE);
        f.set(W - 1, level + 1, 4, Furniture.FRIDGE);
        f.set(W - 1, level, 5, CAULDRON);
        f.set(W - 1, level, 6, Block.of("smoker[facing=west,lit=false]", 0x555451));
        f.set(W - 1, level, 7, Furniture.COUNTER);
        f.fill(W - 1, level + 2, 5, W - 1, level + 2, 7, Furniture.COUNTER);
        Furniture.table(f, W - 5, level, 6, 2, 1, "oak");
        f.set(W - 4, top, 6, Interior.LIGHT);
        f.set(5, top, 5, Interior.LIGHT);
        // 다용도 공간 (욕실과 현관 사이, 넓은 세대)
        for (int a = 11; a <= W - 3; a++) {
            f.set(a, level, 0, Furniture.COUNTER);
        }
        // 안방: 침대 둘(더블), 옷장, 화장대
        Furniture.bed(f, r, 0, level, fr - 2, "east");
        Furniture.bed(f, r, 0, level, fr - 1, "east");
        f.set(0, level, 8, Rooms.WARDROBE);
        f.set(0, level + 1, 8, Rooms.WARDROBE);
        f.set(1, level, 8, Rooms.WARDROBE);
        f.set(1, level + 1, 8, Rooms.WARDROBE);
        f.set(3, level, fr, Furniture.DESK_TOP);
        f.set(2, top, (8 + fr) / 2, Interior.LIGHT);
        // 거실: TV (안방 벽), 소파, 탁자, 화분
        int mid = (8 + fr) / 2;
        f.set(5, level + 1, mid, Furniture.TV);
        f.set(5, level + 1, mid + 1, Furniture.TV);
        f.set(5, level, mid, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        f.set(5, level, mid + 1, Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14));
        Furniture.sofa(f.sub(W - 2, mid - 1, "west"), r, 0, level, 0, 3, "south");
        f.set(W - 4, level, mid, Block.of("gray_carpet", 0x3E4447));
        Furniture.plant(f, r, W - 1, level, fr);
        f.set((5 + W - 1) / 2, top, mid, Interior.LIGHT);
        // 발코니: 세탁기, 화분
        f.set(W - 1, level, D - 1, IRON_BLOCK);
        Furniture.plant(f, r, 0, level, D - 1);
        Furniture.plant(f, r, 6, level, D - 1);
        // 기록
        room(f, "84형", 0, 0, W - 1, D - 1, level);
        room(f, "침실", 0, 0, 2, 3, level);
        room(f, "침실", 4, 0, 6, 3, level);
        room(f, "욕실", 8, 0, 9, 2, level);
        room(f, "현관", W - 2, 0, W - 1, 1, level);
        room(f, "주방", 5, 5, W - 1, 7, level);
        room(f, "안방", 0, 8, 3, fr, level);
        room(f, "거실", 5, 8, W - 1, fr, level);
        room(f, "발코니", 0, D - 1, W - 1, D - 1, level);
    }

    /** 집 층의 코어 화장실 → 분리수거실 (포디움 상가 층만 화장실로 씀) */
    private void recycling(int level, int h) {
        int i0 = t.ci0 + 9 + 4 * elevators, i1 = i0 + 6, j0 = t.cj0 + 1, j1 = t.cj1 - 1;
        v.fill(i0, level, j0, i1, level + 1, j1, AIR);
        v.fill(i0, level - 1, j0, i1, level - 1, j1, Block.of("light_gray_concrete", 0x7D7D73));
        String[] bins = {"blue", "yellow", "green", "white", "light_gray", "brown"};
        for (int q = 0; q < bins.length && i0 + q <= i1; q++) {
            v.set(i0 + q, level, j0, Blocks.concrete(bins[q]));
        }
        for (int i = i0; i <= i1 + 1; i++) {
            Block s = v.get(i, level + 1, t.cj1 + 1);
            if (s != null && s.text() != null && s.text().contains("화장실")) {
                v.set(i, level + 1, t.cj1 + 1, Blocks.wallSign("birch", "south", "black", false, "", "분리수거실"));
            }
        }
    }

    /** 맨 위 펜트하우스 층: 입주민 라운지 (소파), 등, 남쪽 옥상정원으로 나가는 문 */
    private void lounge(int k, int level, int h) {
        int[] bb = t.bounds(k);
        Frame f = Frame.of(v);
        loungeDoor = new int[]{cx() + 4, level, bb[3]};
        for (int i = bb[0] + 2; i + 2 <= bb[2] - 2; i += 6) {
            if (Math.abs(i - cx()) < 4) {
                continue;
            }
            int j = bb[3] - 1;
            if (t.inside(i, j, k) && v.get(i, level, j) == null && v.get(i + 2, level, j) == null) {
                Furniture.sofa(f, r, i, level, j, 3, "north");
            }
        }
        for (int i = bb[0] + 2; i <= bb[2] - 2; i += 5) {
            if (v.get(i, level + h - 2, t.corridorJ) == null) {
                v.set(i, level + h - 2, t.corridorJ, Interior.LIGHT);
            }
        }
    }

    // ------------------------------------------------------------------ 바깥 땅

    private void site() {
        Block pave = pick(new Block[]{LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, SMOOTH_STONE});
        Block stripe = pave == POLISHED_ANDESITE ? LIGHT_GRAY_CONCRETE : POLISHED_ANDESITE;
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                if (v.get(i, -1, j) == null) {
                    v.set(i, -1, j, (i + j) % 6 == 0 && j > tz1 ? stripe : pave);
                }
            }
        }
        if (podium) {
            // 앞 보도: 가로수 구덩이 대신 볼라드
            for (int i = 2; i < w - 2; i += 5) {
                v.set(i, 0, d - 1, StreetPlan.POST);
            }
            return;
        }
        // 공개공지: 앞마당 화단·나무·의자·미술작품·머릿돌
        int fz = tz1 + 1, fz1 = d - 1;
        int depth = fz1 - fz + 1;
        if (depth >= 3) {
            int mid = (tx0 + tx1) / 2;
            // 정문 앞은 비우고 양쪽에 화단 + 나무
            for (int i : new int[]{tx0 + 2, tx1 - 2}) {
                if (depth >= 5) {
                    Site.planter(v, i, fz + depth / 2);
                }
            }
            if (depth >= 5 && tx1 - tx0 >= 28) {
                Site.bench(v, tx0 + 5, fz + depth / 2, 3, true);
                Site.bench(v, tx1 - 7, fz + depth / 2, 3, true);
            }
            // 미술작품 (건축물 미술작품): 돌 받침 위 금속 덩어리
            int si = tx0 + Math.max(2, (mid - tx0) / 2), sj = fz + Math.max(1, depth / 2 - 1);
            if (depth >= 6 && v.get(si, 0, sj) == null) {
                v.set(si, 0, sj, POLISHED_ANDESITE);
                v.set(si, 1, sj, Block.of("cut_copper", 0xBF6A50));
                v.set(si, 2, sj, Block.of("weathered_copper", 0x6C9E79));
                v.set(si, 3, sj, Block.of("cut_copper", 0xBF6A50));
                v.set(si + 1, 2, sj, Block.of("weathered_copper", 0x6C9E79));
            }
            // 머릿돌 (건물 이름)
            int ni = tx1 - 1, nj = Math.min(fz1 - 1, fz + 1);
            if (v.get(ni, 0, nj) == null) {
                v.fill(ni, 0, nj, ni + 1, 1, nj, POLISHED_GRANITE);
                v.set(ni, 1, nj + 1, Blocks.wallSign("dark_oak", "south", "white", false, "", name));
            }
            // 국기 게양대 (오래된 업무 빌딩)
            if (oldStyle()) {
                for (int q = 0; q < 3; q++) {
                    Site.flagpole(v, tx0 + 1 + 2 * q, fz1 - 1, 8);
                }
            }
        }
        // 옆·뒤 띠: 작은 화단
        for (int j = tz0 + 2; j < tz1 - 1; j += 7) {
            if (tx0 >= 2) {
                v.set(tx0 - 2, 0, j, Kit.BOX_HEDGE);
            }
            if (w - 1 - tx1 >= 2) {
                v.set(tx1 + 2, 0, j, Kit.BOX_HEDGE);
            }
        }
    }

    // ------------------------------------------------------------------ 외벽

    private void facade() {
        for (int k = 0; k < floors; k++) {
            int level = t.levels[k], top = t.levels[k + 1] - 2;
            boolean pod = k < p;
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    if (!t.edge(i, j, k)) {
                        continue;
                    }
                    int nx = 0, nz = 0, faces = 0;
                    if (!t.inside(i, j + 1, k)) {
                        nz = 1;
                        faces++;
                    }
                    if (!t.inside(i, j - 1, k)) {
                        if (faces == 0) {
                            nz = -1;
                        }
                        faces++;
                    }
                    if (!t.inside(i + 1, j, k)) {
                        if (faces == 0) {
                            nx = 1;
                        }
                        faces++;
                    }
                    if (!t.inside(i - 1, j, k)) {
                        if (faces == 0) {
                            nx = -1;
                        }
                        faces++;
                    }
                    boolean corner = faces >= 2 && chamfer == 0;
                    int pos = nz != 0 ? i : j;
                    int y0 = k == 0 ? level : level - 1;
                    if (pod) {
                        podiumCell(k, i, j, nx, nz, corner, pos, y0, level, top);
                    } else if (k == 0) {
                        lobbyCell(i, j, nx, nz, corner, pos, level, top);
                    } else if (k == mechK) {
                        for (int y = y0; y <= top; y++) {
                            v.set(i, y, j, corner || pos % 3 == 0 || y == y0 ? frame : LOUVER);
                        }
                    } else {
                        towerCell(k, i, j, nx, nz, corner, pos, y0, level, top);
                    }
                }
            }
        }
    }

    private void out(int i, int y, int j, int nx, int nz, Block b) {
        int oi = i + nx, oj = j + nz;
        if (!v.inside(oi, y, oj)) {
            return;
        }
        Block cur = v.get(oi, y, oj);
        if (cur == null || cur.isAir() || cur.id().endsWith("_slab")) {
            v.set(oi, y, oj, b);
        }
    }

    private void towerCell(int k, int i, int j, int nx, int nz, boolean corner, int pos, int y0, int level, int top) {
        switch (style) {
            case STONE -> {
                if (piers) {
                    // 돌기둥(튀어나옴) 사이 바닥부터 천장까지 유리, 층 사이 돌 띠
                    boolean pier = corner || pos % bay == 0;
                    for (int y = y0; y <= top; y++) {
                        v.set(i, y, j, pier || (y == y0 && y0 == level - 1) ? wall : glass);
                        if (pier && !corner) {
                            out(i, y, j, nx, nz, wall);
                        }
                    }
                } else {
                    // 돌 벽에 뚫은 창: 쑥 들어가거나(바깥 한 겹 더) 세로 돌기둥이 튀어나옴
                    boolean win = pos % bay != 0 && !(bay == 4 && pos % bay == 3);
                    for (int y = y0; y <= top; y++) {
                        boolean row = y >= level + 1;
                        v.set(i, y, j, !corner && win && row ? glass : wall);
                        if (recessed && !(win && row && !corner)) {
                            out(i, y, j, nx, nz, wall);
                        }
                        if (fins && !win && !corner) {
                            out(i, y, j, nx, nz, frame);
                        }
                    }
                }
            }
            case BANDS -> {
                for (int y = y0; y <= top; y++) {
                    boolean row = y >= level + 1;
                    v.set(i, y, j, corner ? band : row ? (pos % 7 == 0 ? frame : glass) : band);
                }
                if (y0 == level - 1) {
                    out(i, y0, j, nx, nz, LEDGE);
                }
            }
            case PANEL -> {
                boolean accent = corner || (louvers && pos % 12 == 0);
                for (int y = y0; y <= top; y++) {
                    boolean row = y >= level + 1;
                    Block bl = y == y0 && y0 == level - 1 ? band : accent ? frame : row && pos % 3 != 0 ? glass : wall;
                    v.set(i, y, j, bl);
                }
                // 창 아래 실외기 가림 루버 (오피스텔)
                if (louvers && !accent && pos % 3 != 0 && (pos / 3 + k) % 3 == 0) {
                    v.set(i, level, j, LOUVER);
                }
            }
            case CURTAIN -> {
                boolean post = corner || pos % mEvery == 0;
                for (int y = y0; y <= top; y++) {
                    v.set(i, y, j, y == y0 && y0 == level - 1 ? spandrel : post ? frame : glass);
                    if (fins && !corner && pos % (mEvery * 2) == 0) {
                        out(i, y, j, nx, nz, frame);
                    }
                }
            }
            case RESIDENTIAL -> {
                boolean post = corner || pos % bay == 0;
                for (int y = y0; y <= top; y++) {
                    v.set(i, y, j, y == y0 && y0 == level - 1 ? frame : post ? frame : glass);
                }
                // 엇갈린 발코니 (남·북 면): 바닥판이 나오고 유리 난간
                if (balconies && nz != 0 && !corner && y0 == level - 1) {
                    int phase = (k % 2 == 0) ? 0 : bay;
                    int q = Math.floorMod(pos - phase, 2 * bay);
                    if (q >= 1 && q <= bay + 1) {
                        out(i, level - 1, j, nx, nz, frame);
                        out(i, level, j, nx, nz, Block.of("glass_pane", 0xC8DCE4));
                    }
                }
            }
        }
    }

    /** 포디움 층 바깥 벽 (1층 가게 앞은 groundFront 가 다시 그림) */
    private void podiumCell(int k, int i, int j, int nx, int nz, boolean corner, int pos, int y0, int level, int top) {
        boolean side = nx != 0 && (i == 0 || i == w - 1);
        boolean back = nz < 0;
        for (int y = y0; y <= top; y++) {
            Block bl;
            if (corner) {
                bl = podStone;
            } else if (k == 0) {
                bl = y == level ? podFrame : y >= level + 3 ? podStone : (pos % 8 == 0 ? podFrame : podGlass);
                if (side || back) {
                    bl = y >= level + 1 && y <= level + 2 && pos % 6 >= 2 && pos % 6 <= 3 ? podGlass : podStone;
                }
            } else if (side || back) {
                bl = y >= level + 1 && pos % 6 >= 2 && pos % 6 <= 4 ? podGlass : podStone;
            } else {
                bl = y == y0 && y0 == level - 1 ? podStone : pos % 5 == 0 ? podStone : y == level ? podStone : podGlass;
            }
            v.set(i, y, j, bl);
        }
    }

    /** 포디움 없는 건물의 1층 (로비 층) 바깥 벽: 큰 유리와 돌기둥 */
    private void lobbyCell(int i, int j, int nx, int nz, boolean corner, int pos, int level, int top) {
        int b = style == Style.CURTAIN ? mEvery * 2 : Math.max(3, bay);
        for (int y = level; y <= top; y++) {
            Block bl = corner || pos % b == 0 ? (style == Style.CURTAIN ? frame : podStone) : y == level ? podFrame : podGlass;
            v.set(i, y, j, bl);
        }
        if (colonnade && nz > 0) {
            // 회랑: 위층 바깥 벽 아래 기둥
            for (int y = level; y <= t.levels[1] - 2; y++) {
                if (pos % Math.max(4, bay) == 0) {
                    v.set(i, y, j + 2, wall);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 1층 정면: 가게 앞, 정문, 간판, 차양

    private void groundFront() {
        int level = t.levels[0], h = t.height(0), top = level + h - 2;
        int[] bb = t.bounds(0);
        int zf = bb[3];
        int cx = cx();
        // 가게 앞: 유리, 문, 간판
        for (int u = 0; u < units.size(); u++) {
            int[] un = units.get(u);
            if (un[3] != zf - 1) {
                continue;
            }
            int a0 = un[0], a1 = un[1];
            for (int i = a0 - 1; i <= a1 + 1; i++) {
                if (!t.edge(i, zf, 0)) {
                    continue;
                }
                boolean post = i == a0 - 1 || i == a1 + 1;
                for (int y = level; y <= top; y++) {
                    v.set(i, y, zf, post || y == level + 3 ? podFrame : Block.of("glass_pane", 0xC8DCE4));
                }
            }
            int di = (a0 + a1) / 2;
            v.fill(di, level, zf, di + (a1 - a0 >= 5 ? 1 : 0), level + 2, zf, AIR);
            String use = unitUse.get(u);
            String[] lines = signLines(use, unitName.get(u));
            v.set((a0 + a1) / 2, level + 3, zf + 1, Blocks.wallSign(signWood(use), "south", signColor(use), true, lines));
        }
        // 정문 (로비): 3칸 트고, 위 차양, 건물 이름
        if (t.edge(cx, zf, 0)) {
            for (int i = cx - 2; i <= cx + 2; i++) {
                if (t.edge(i, zf, 0)) {
                    for (int y = level; y <= top; y++) {
                        v.set(i, y, zf, Math.abs(i - cx) == 2 ? podFrame : y <= level + 2 ? AIR : Block.of("glass_pane", 0xC8DCE4));
                    }
                }
            }
            int cz1 = Math.min(d - 1, zf + 3);
            for (int i = cx - 4; i <= cx + 4; i++) {
                for (int j = zf + 1; j <= cz1; j++) {
                    if (v.get(i, level + 3, j) == null || v.get(i, level + 3, j).isAir()) {
                        v.set(i, level + 3, j, Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E));
                    }
                }
            }
            // 바깥으로 튀어나온 돌기둥이 정문을 막지 않게
            for (int i = cx - 1; i <= cx + 1; i++) {
                for (int y = level; y <= level + 2; y++) {
                    v.set(i, y, zf + 1, null);
                    if (colonnade) {
                        v.set(i, y, zf + 2, null);
                    }
                }
            }
            v.set(cx, level + 4, zf + 1, Blocks.wallSign("dark_oak", "south", "white", true, "", name));
            v.set(cx, level + 3, zf - 1, Interior.LIGHT);
        }
    }

    /** 포디움 위층 세입자 간판: 층마다 서쪽·동쪽 가게, 남쪽 창 아래 돌 띠에 */
    private void podiumSigns() {
        for (int k = 1; k < p && k - 1 < unitUseByFloor.size(); k++) {
            String[] uses = unitUseByFloor.get(k - 1);
            int[] bb = t.bounds(k);
            int level = t.levels[k], zf = bb[3], cx = cx();
            int[] at = {(bb[0] + cx) / 2, (cx + bb[2]) / 2};
            for (int q = 0; q < 2; q++) {
                int i = at[q];
                Block wallB = v.get(i, level, zf);
                if (wallB == null || wallB.isAir() || wallB.id().endsWith("_pane") || v.get(i, level, zf + 1) != null) {
                    continue;
                }
                String[] nm = tenantName(uses[q]);
                v.set(i, level, zf + 1, Blocks.wallSign(q == 0 ? "dark_oak" : "birch", "south", q == 0 ? "white" : "black", true,
                        (k + 1) + "층", nm[0], nm[1]));
            }
        }
    }

    private String[] tenantName(String use) {
        String pre = KoreanNames.prefix(r);
        return switch (use) {
            case "병원" -> new String[]{pre + pick(new String[]{"내과", "이비인후과", "정형외과", "소아과", "피부과"}), "진료 9시~7시"};
            case "치과" -> new String[]{pre + "치과", "임플란트·교정"};
            case "학원" -> new String[]{pre + pick(new String[]{"수학학원", "영어학원", "입시학원"}), "초·중·고"};
            case "운동" -> new String[]{pre + pick(new String[]{"필라테스", "피트니스", "요가"}), "PT 상담"};
            default -> new String[]{pre + pick(new String[]{"한식뷔페", "냉면", "갈비", "칼국수", "샤브샤브"}), "단체 예약"};
        };
    }

    private static String[] signLines(String use, String nm) {
        String sub = switch (use) {
            case "은행" -> "ATM 24시간";
            case "편의점" -> "24시간";
            case "카페" -> "COFFEE";
            case "약국" -> "약 처방 조제";
            case "증권" -> "고객센터";
            case "부동산" -> "공인중개사";
            default -> "점심 특선";
        };
        return new String[]{"", nm, sub, ""};
    }

    private static String signWood(String use) {
        return switch (use) {
            case "은행", "증권" -> "dark_oak";
            case "카페" -> "spruce";
            case "편의점" -> "birch";
            default -> "oak";
        };
    }

    private static String signColor(String use) {
        return switch (use) {
            case "은행", "증권" -> "white";
            case "카페" -> "white";
            case "편의점" -> "blue";
            case "약국" -> "green";
            default -> "black";
        };
    }

    // ------------------------------------------------------------------ 지붕: 포디움 옥상정원, 들여쓴 지붕 난간

    private void roofs() {
        for (int k = 1; k < floors; k++) {
            int y = t.levels[k] - 1;
            boolean podRoof = k == p || (penthouse && k == floors - 1);
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    if (!t.inside(i, j, k - 1) || t.inside(i, j, k)) {
                        continue;
                    }
                    // 아래층만 있는 칸 = 드러난 지붕
                    if (t.edge(i, j, k - 1)) {
                        v.set(i, y + 1, j, podRoof ? Block.of("glass_pane", 0xC8DCE4) : band);
                        if (!podRoof) {
                            v.set(i, y + 2, j, LEDGE);
                        }
                    } else if (podRoof) {
                        boolean near = isNearTower(i, j, k);
                        v.set(i, y, j, near ? POLISHED_ANDESITE : ((i * 7 + j * 3) % 11 == 0 ? Block.of("moss_block", 0x596E2D) : GRASS));
                        if (!near && (i * 5 + j * 9) % 23 == 0) {
                            v.set(i, y + 1, j, Kit.BOX_HEDGE);
                        }
                    }
                }
            }
            if (podRoof) {
                // 옥상정원 의자·등
                int[] bb = t.bounds(k);
                for (int i = bb[0]; i <= bb[2]; i += 7) {
                    int j = bb[3] + 2;
                    if (t.inside(i, j, k - 1) && !t.edge(i, j, k - 1) && !t.inside(i, j, k)) {
                        v.set(i, y + 1, j, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
                    }
                }
            }
        }
    }

    /** 라운지에서 옥상정원으로: 외벽을 2칸 트고 문턱 앞 칸 비움 (외벽은 facade 가 다시 그리므로 그 뒤에) */
    private void openLoungeDoor() {
        if (loungeDoor == null) {
            return;
        }
        int i = loungeDoor[0], y = loungeDoor[1], j = loungeDoor[2];
        v.fill(i, y, j, i + 1, y + 1, j, AIR);
        v.fill(i, y, j + 1, i + 1, y + 1, j + 1, AIR);
    }

    private boolean isNearTower(int i, int j, int k) {
        return t.inside(i - 1, j, k) || t.inside(i + 1, j, k) || t.inside(i, j - 1, k) || t.inside(i, j + 1, k)
                || t.inside(i - 2, j, k) || t.inside(i + 2, j, k) || t.inside(i, j - 2, k) || t.inside(i, j + 2, k);
    }

    // ------------------------------------------------------------------ 꼭대기

    private void crown() {
        int k = floors - 1, R = t.roofLevel;
        int ch = floors >= 40 ? 6 : floors >= 25 ? 5 : 4;
        if (crown == 1 || crown == 2 || crown == 3) {
            for (int j = 0; j < d; j++) {
                for (int i = 0; i < w; i++) {
                    if (!t.edge(i, j, k)) {
                        continue;
                    }
                    boolean ns = !t.inside(i, j - 1, k) || !t.inside(i, j + 1, k);
                    int pos = ns ? i : j;
                    boolean post = pos % mEvery == 0;
                    for (int y = R; y <= R + ch; y++) {
                        if (crown == 3) {
                            if (post) {
                                v.set(i, y, j, frame);
                            } else if (y == R) {
                                v.set(i, y, j, spandrel);
                            } else if (y == R + 1) {
                                v.set(i, y, j, AIR);
                            }
                        } else {
                            v.set(i, y, j, y == R + ch ? frame : post ? frame : glass);
                        }
                    }
                    // 밤에 빛나는 꼭대기: 가림벽 안쪽 바닥 등
                    if (crown != 3 && (i + j) % 4 == 0) {
                        int ii = i - (t.inside(i + 1, j, k) ? 0 : 1) + (t.inside(i - 1, j, k) ? 0 : 1);
                        int jj = j - (t.inside(i, j + 1, k) ? 0 : 1) + (t.inside(i, j - 1, k) ? 0 : 1);
                        if (v.get(ii, R, jj) == null) {
                            v.set(ii, R, jj, SEA_LANTERN);
                        }
                    }
                }
            }
        }
        // 평지붕: 냉각탑·물탱크 (헬기장이 없는 쪽)
        if (crown == 0) {
            int[] bb = t.bounds(k);
            int ci = bb[2] - 4, cj = bb[3] - 4;
            if (ci - bb[0] > 8 && v.get(ci, R, cj) == null && v.get(ci + 2, R, cj + 2) == null) {
                v.fill(ci, R, cj, ci + 2, R + 1, cj + 2, IRON_BLOCK);
                v.fill(ci, R + 2, cj, ci + 2, R + 2, cj + 2, Block.of("iron_bars", 0x888888));
                v.set(ci + 1, R + 2, cj + 1, CAULDRON);
            }
            if (oldStyle()) {
                int wi = bb[0] + 3, wj = bb[3] - 3;
                if (v.get(wi, R, wj) == null) {
                    v.cylinder(wi + 0.5, wj + 0.5, 1.6, R, R + 2, r.nextBoolean() ? IRON_BLOCK : Block.of("light_blue_concrete", 0x2389C7));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 껍데기 층: 코어 화장실 자리를 막아 계단·엘리베이터만

    /**
     * 껍데기 층이 밖에서 빈 유리 상자로 보이지 않게: 천장 등을 7칸마다 (밤에 사람이 있는 것처럼),
     * 창 맨 윗줄 한 칸 안쪽에 흰·밝은 회색 블라인드(유리판)를 둠.
     */
    private void shellDress() {
        Block[] blinds = {Kit.WHITE_PANE, Block.of("light_gray_stained_glass_pane", 0x999999)};
        for (int k = 0; k < floors; k++) {
            if (used(k)) {
                continue;
            }
            int level = t.levels[k], top = t.levels[k + 1] - 2;
            Block blind = blinds[k % 2];
            int[] bb = t.bounds(k);
            for (int j = bb[1]; j <= bb[3]; j++) {
                for (int i = bb[0]; i <= bb[2]; i++) {
                    if (!t.inside(i, j, k)) {
                        continue;
                    }
                    if (t.edge(i, j, k)) {
                        Block g = v.get(i, top, j);
                        if (g == null || !g.id().contains("glass")) {
                            continue;
                        }
                        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                        for (int[] dd : dirs) {
                            int ii = i - dd[0], jj = j - dd[1];
                            if (!t.inside(i + dd[0], j + dd[1], k) && t.inside(ii, jj, k) && !t.edge(ii, jj, k) && v.get(ii, top, jj) == null) {
                                v.set(ii, top, jj, blind);
                            }
                        }
                    } else if ((i - bb[0]) % 7 == 3 && (j - bb[1]) % 7 == 3 && v.get(i, top, j) == null
                            && !(i >= t.ci0 - 1 && i <= t.ci1 + 1 && j >= t.cj0 - 1 && j <= t.cj1 + 1)) {
                        v.set(i, top, j, Interior.LIGHT);
                    }
                }
            }
        }
    }

    private void shellCores() {
        if (restrooms == 0) {
            return;
        }
        for (int k = 0; k < floors; k++) {
            if (used(k)) {
                continue;
            }
            int level = t.levels[k], top = t.levels[k + 1] - 2;
            for (int n = 0; n < restrooms; n++) {
                int i0 = t.ci0 + 1 + 7 + 4 * elevators + 8 * n, i1 = i0 + 7;
                v.fill(i0, level, t.cj0, i1, top, t.cj1, Interior.CORE_WALL);
                for (int i = i0; i <= i1 + 1; i++) {
                    Block sg = v.get(i, level + 1, t.cj1 + 1);
                    if (sg != null && sg.text() != null) {
                        v.set(i, level + 1, t.cj1 + 1, null);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 화장실 이름표: 한 칸뿐이면 층마다 남녀 번갈아

    private void restroomSigns() {
        if (restrooms != 1) {
            return;
        }
        for (int k = 1; k < floors; k += 2) {
            int y = t.levels[k] + 1;
            for (int i = t.ci0; i <= t.ci1 + 1; i++) {
                Block s = v.get(i, y, t.cj1 + 1);
                if (s != null && s.text() != null && s.text().contains("남자 화장실")) {
                    v.set(i, y, t.cj1 + 1, Blocks.wallSign("birch", "south", "black", false, "", "여자 화장실"));
                }
            }
        }
    }
}
