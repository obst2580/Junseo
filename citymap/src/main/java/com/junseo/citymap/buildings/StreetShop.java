package com.junseo.citymap.buildings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;

import static com.junseo.citymap.buildings.Blocks.*;

/**
 * 구역마다 다른 상가 건물 (상가·근린생활, 종류 "shop"). 큰길·이면도로를 따라 맞벽으로 늘어서는 건물과 골목 안 건물.
 * <ul>
 *   <li>junggu: 을지로 1960~70년대 타일 건물(셔터 내린 인쇄소·조명·공구 가게, 녹슨 옥상 물탱크) 또는
 *       명동형 간판 많은 유리 상가</li>
 *   <li>gangnam: 이면 근린생활 (화강석·알루미늄 패널 + 유리, 층마다 병원·학원·필라테스 간판, 세로 층별 안내 간판)</li>
 *   <li>gwangjin / songpa: 건대·방이동 먹자골목 (벽돌·타일, 층마다 큰 간판과 돌출 간판, 고깃집·곱창·노래방·PC방)</li>
 *   <li>yongsan: 이태원 (노출 벽돌·검은 철골 창, 옥상 테라스, 펍·타코·케밥)</li>
 *   <li>hongdae: 홍대 상가 (간판 가득) 또는 상수동 카페 (흰 벽돌, 큰 창, 옥상 테라스)</li>
 *   <li>mapo: 망원시장 골목 가게 (열린 가게 앞, 차양, 길에 내놓은 물건, 위층 살림집)</li>
 *   <li>university: 녹두거리·샤로수길 (1층 복사집·서점·국밥집, 위층 고시원·원룸·독서실, 벽에 실외기)</li>
 *   <li>yeouido: 오피스 지구 근린생활 (짙은 화강석·갈색 유리, 1층 점심 식당, 위층 사무실·병원)</li>
 *   <li>bukhansan: 등산로 입구 (돌 기단·나무, 기와 처마, 파전·막걸리·등산복 가게)</li>
 *   <li>namsan: 오래된 동네 가게 (붉은 벽돌, 슈퍼·세탁소·철물점, 위층 살림집, 옥탑방)</li>
 * </ul>
 * 계단실은 앞쪽 한 옆(꺾인 계단, 좁으면 1칸 줄)이고, 1층 계단 입구 문이 길에 바로 납니다. 층마다 계단참 옆문으로
 * 그 층 가게에 들어가고, 옥상까지 오릅니다. 6층 이상이고 땅이 넉넉하면 계단실 뒤에 엘리베이터(1층은 계단참 옆 복도로).
 * 정면은 남쪽(j = d-1). 맨 앞 줄(j = d-1)은 간판·차양·입간판 자리, 겉벽은 j = d-2.
 * 맞벽(partyWall)이면 옆벽(i = 0, w-1)에 창·문이 없고 땅 폭을 꽉 채웁니다.
 * <p>
 * 모든 배치는 계단실이 왼쪽(서쪽)인 기준으로 그리고, 반은 좌우를 뒤집어서 오른쪽 계단실로 만듭니다.
 */
final class StreetShop {
    /** 구역 모양 */
    enum Look { EULJIRO, MYEONGDONG, GANGNAM, MUKJA, BANGI, ITAEWON, HONGDAE, SANGSU, MANGWON, CAMPUS, YEOUIDO, TRAIL, OLDTOWN, PLAIN }

    /** 가게·층 쓰임 */
    enum Use {
        FOOD, CAFE, BAR, STORE, RETAIL, BOOK, PRINT, TOOLS, LIGHTING, LAUNDRY, PHARMACY, MARKET,
        ACADEMY, CLINIC, GOSIWON, ONEROOM, OFFICE, PC, NORAE, HOME, STUDIO, WORKSHOP, BILLIARD
    }

    /** 가게 하나: 간판 이름, 아랫줄(업종·메뉴), 쓰임, 세부 종류 */
    record Tenant(Use use, String name, String sub, String kind) {
    }

    private enum Win { PUNCHED, SMALL, RIBBON, CURTAIN, STEEL }

    private enum Front { GLASS, SHUTTER, OPEN, FOLDING }

    // ------------------------------------------------------------------ 상호 목록 {이름, 쓰임, 아랫줄, 종류}

    private static final String[][] EULJIRO_G = {
            {"%s인쇄", "PRINT", "명함·전단", "offset"}, {"%s조명", "LIGHTING", "LED·샹들리에", ""},
            {"%s전기조명", "LIGHTING", "전기·조명자재", ""}, {"%s공구", "TOOLS", "공구·철물", "tools"},
            {"%s타일", "TOOLS", "타일·도기", "tile"}, {"%s철물", "TOOLS", "철물·자재", "tools"},
            {"%s지업사", "PRINT", "종이·지류", "paper"}, {"을지 노가리", "BAR", "생맥주·노가리", "hof"},
            {"%s면옥", "FOOD", "냉면·수육", "naengmyeon"}, {"%s골뱅이", "BAR", "골뱅이·생맥주", "hof"},
            {"%s다방", "CAFE", "커피·쌍화차", "dabang"}, {"%s기계", "TOOLS", "기계·부품", "tools"},
    };
    private static final String[][] EULJIRO_U = {
            {"%s인쇄소", "WORKSHOP", "인쇄·제본", "print"}, {"%s디자인", "OFFICE", "편집·출력", ""},
            {"%s공업사", "WORKSHOP", "금속·가공", "metal"}, {"%s기획", "OFFICE", "광고·기획", ""},
            {"%s다방", "CAFE", "커피·쌍화차", "dabang"}, {"%s당구장", "BILLIARD", "당구·포켓볼", ""},
            {"LP바 %s", "BAR", "LP·위스키", "lp"}, {"%s제본소", "WORKSHOP", "제본·코팅", "print"},
            {"%s사무소", "OFFICE", "", ""},
    };
    private static final String[][] MYEONGDONG_G = {
            {"%s코스메틱", "RETAIL", "화장품·마스크팩", "cosmetic"}, {"뷰티 %s", "RETAIL", "SALE 50%", "cosmetic"},
            {"%s패션", "RETAIL", "의류·잡화", "clothes"}, {"%s슈즈", "RETAIL", "신발·운동화", "shoes"},
            {"명동환전소", "OFFICE", "EXCHANGE", "exchange"}, {"%s칼국수", "FOOD", "칼국수·만두", "kalguksu"},
            {"%s기념품", "RETAIL", "SOUVENIR", "goods"}, {"%s약국", "PHARMACY", "PHARMACY", ""},
            {"%s안경", "RETAIL", "안경·렌즈", "optical"}, {"%s분식", "FOOD", "떡볶이·김밥", "bunsik"},
    };
    private static final String[][] MYEONGDONG_U = {
            {"%s피부과", "CLINIC", "피부·레이저", "skin"}, {"%s성형외과", "CLINIC", "상담 환영", "skin"},
            {"%s치과", "CLINIC", "임플란트", "dental"}, {"네일 %s", "STUDIO", "네일·속눈썹", "nail"},
            {"%s돈가스", "FOOD", "돈가스·우동", "donkatsu"}, {"카페 %s", "CAFE", "COFFEE", ""},
            {"%s노래방", "NORAE", "노래연습장", ""}, {"%s여행사", "OFFICE", "항공·호텔", ""},
            {"%s어학원", "ACADEMY", "영어·일본어", ""}, {"%s패션", "RETAIL", "의류·잡화", "clothes"},
    };
    private static final String[][] GANGNAM_G = {
            {"카페 %s", "CAFE", "COFFEE·DESSERT", ""}, {"%s약국", "PHARMACY", "", ""},
            {"24시 편의점", "STORE", "", "cvs"}, {"%s샐러드", "FOOD", "SALAD·POKE", "salad"},
            {"%s한우", "FOOD", "한우·점심특선", "bbq"}, {"%s스시", "FOOD", "오마카세", "sushi"},
            {"%s베이커리", "CAFE", "BAKERY", "bakery"}, {"%s부동산", "OFFICE", "공인중개사", "realty"},
            {"%s꽃집", "RETAIL", "FLOWER", "flower"}, {"%s쌀국수", "FOOD", "PHO", "noodle"},
            {"%s안경", "RETAIL", "안경·렌즈", "optical"},
    };
    private static final String[][] GANGNAM_U = {
            {"%s피부과", "CLINIC", "피부·레이저", "skin"}, {"%s치과", "CLINIC", "교정·임플란트", "dental"},
            {"%s정형외과", "CLINIC", "도수치료", "therapy"}, {"%s한의원", "CLINIC", "추나·침", "therapy"},
            {"%s내과", "CLINIC", "건강검진", "clinic"}, {"%s수학학원", "ACADEMY", "중·고등부", ""},
            {"%s영어학원", "ACADEMY", "초·중등부", ""}, {"%s논술", "ACADEMY", "논술·국어", ""},
            {"%s필라테스", "STUDIO", "1:1 PT", "pilates"}, {"%s요가", "STUDIO", "요가·명상", "yoga"},
            {"%s세무회계", "OFFICE", "세무·회계", ""}, {"%s스터디카페", "ACADEMY", "24시간", "study"},
            {"%s PT", "STUDIO", "헬스·PT", "gym"},
    };
    private static final String[][] MUKJA_G = {
            {"%s양꼬치", "FOOD", "양꼬치·꿔바로우", "skewer"}, {"%s곱창", "FOOD", "곱창·막창", "gopchang"},
            {"%s삼겹살", "FOOD", "삼겹살·목살", "bbq"}, {"%s포차", "BAR", "안주·소주", "pocha"},
            {"%s호프", "BAR", "치킨·생맥주", "hof"}, {"%s치킨", "FOOD", "후라이드·양념", "chicken"},
            {"%s떡볶이", "FOOD", "떡볶이·튀김", "bunsik"}, {"%s마라탕", "FOOD", "마라탕·마라샹궈", "mala"},
            {"24시 편의점", "STORE", "", "cvs"}, {"%s닭갈비", "FOOD", "철판 닭갈비", "bbq"},
            {"%s이자카야", "BAR", "사케·꼬치", "izakaya"}, {"%s족발", "FOOD", "족발·보쌈", "jokbal"},
    };
    private static final String[][] MUKJA_U = {
            {"%s노래방", "NORAE", "노래연습장", ""}, {"코인노래 %s", "NORAE", "1곡 500원", "coin"},
            {"%s PC방", "PC", "최신 사양", ""}, {"%s당구장", "BILLIARD", "당구·포켓볼", ""},
            {"%s포차", "BAR", "2층 포차", "pocha"}, {"%s이자카야", "BAR", "사케·꼬치", "izakaya"},
            {"%s고기", "FOOD", "숯불구이", "bbq"}, {"%s보드카페", "CAFE", "보드게임", "board"},
            {"%s술집", "BAR", "칵테일·소주", "pocha"}, {"%s호프", "BAR", "생맥주", "hof"},
    };
    private static final String[][] BANGI_G = {
            {"%s갈비", "FOOD", "돼지갈비·냉면", "bbq"}, {"%s횟집", "FOOD", "활어·회", "sashimi"},
            {"%s곱창", "FOOD", "곱창·대창", "gopchang"}, {"%s족발", "FOOD", "족발·보쌈", "jokbal"},
            {"%s호프", "BAR", "치킨·생맥주", "hof"}, {"%s아구찜", "FOOD", "아구찜·해물찜", "stew"},
            {"%s순대국", "FOOD", "순대국·수육", "gukbap"}, {"%s치킨", "FOOD", "후라이드", "chicken"},
            {"%s포차", "BAR", "실내포차", "pocha"}, {"24시 편의점", "STORE", "", "cvs"},
    };
    private static final String[][] BANGI_U = {
            {"%s노래방", "NORAE", "노래연습장", ""}, {"%s이자카야", "BAR", "사케·꼬치", "izakaya"},
            {"%s당구장", "BILLIARD", "당구·포켓볼", ""}, {"%s PC방", "PC", "최신 사양", ""},
            {"%s스크린골프", "STUDIO", "예약 환영", "golf"}, {"%s횟집", "FOOD", "2층 단체석", "sashimi"},
            {"%s호프", "BAR", "생맥주", "hof"}, {"%s세무사", "OFFICE", "세무·기장", ""},
    };
    private static final String[] EN = {"CASA", "LUNA", "ROJO", "SOUL", "NORTH", "OLD", "BLUE", "HILL", "URBAN", "MAMA", "GOLD", "WILD"};
    private static final String[][] ITAEWON_G = {
            {"#s TACO", "FOOD", "TACO·BURRITO", "taco"}, {"#s KEBAB", "FOOD", "KEBAB·FALAFEL", "kebab"},
            {"할랄 키친", "FOOD", "HALAL FOOD", "halal"}, {"#s BURGER", "FOOD", "BURGER·BEER", "burger"},
            {"#s CURRY", "FOOD", "INDIAN CURRY", "curry"}, {"#s BRUNCH", "CAFE", "BRUNCH·COFFEE", "brunch"},
            {"#s WINE", "BAR", "WINE BAR", "wine"}, {"#s PUB", "BAR", "CRAFT BEER", "pub"},
            {"#s ROASTERS", "CAFE", "COFFEE", ""}, {"#s VINTAGE", "RETAIL", "VINTAGE", "clothes"},
            {"%s테일러", "RETAIL", "TAILOR SHOP", "tailor"}, {"%s앤틱", "RETAIL", "ANTIQUE", "antique"},
    };
    private static final String[][] ITAEWON_U = {
            {"#s LOUNGE", "BAR", "COCKTAIL", "lounge"}, {"#s PUB", "BAR", "CRAFT BEER", "pub"},
            {"#s GRILL", "FOOD", "STEAK·GRILL", "grill"}, {"#s TACO", "FOOD", "TACO·BURRITO", "taco"},
            {"#s YOGA", "STUDIO", "YOGA", "yoga"}, {"#s TATTOO", "STUDIO", "TATTOO", "tattoo"},
            {"#s WINE", "BAR", "WINE BAR", "wine"}, {"%s무역", "OFFICE", "TRADING", ""},
    };
    private static final String[][] HONGDAE_G = {
            {"스타일 %s", "RETAIL", "WOMEN·MEN", "clothes"}, {"%s화장품", "RETAIL", "COSMETIC", "cosmetic"},
            {"카페 %s", "CAFE", "COFFEE", ""}, {"%s떡볶이", "FOOD", "즉석떡볶이", "bunsik"},
            {"인생사진 %s", "STUDIO", "셀프 사진관", "photo"}, {"%s라멘", "FOOD", "돈코츠 라멘", "ramen"},
            {"코인노래 %s", "NORAE", "1곡 500원", "coin"}, {"%s소품샵", "RETAIL", "소품·문구", "goods"},
            {"%s레코드", "RETAIL", "LP·CD", "records"}, {"%s포차", "BAR", "안주·소주", "pocha"},
    };
    private static final String[][] HONGDAE_U = {
            {"%s PC방", "PC", "최신 사양", ""}, {"코인노래 %s", "NORAE", "1곡 500원", "coin"},
            {"%s댄스", "STUDIO", "댄스 학원", "dance"}, {"%s이자카야", "BAR", "사케·꼬치", "izakaya"},
            {"%s칵테일", "BAR", "COCKTAIL BAR", "cocktail"}, {"%s합주실", "STUDIO", "합주·녹음", "band"},
            {"%s보드카페", "CAFE", "보드게임", "board"}, {"%s만화카페", "CAFE", "만화·라면", "comic"},
            {"%s타투", "STUDIO", "TATTOO", "tattoo"}, {"%s라멘", "FOOD", "돈코츠 라멘", "ramen"},
            {"%s스튜디오", "STUDIO", "PHOTO", "photo"},
    };
    private static final String[][] SANGSU_G = {
            {"카페 %s", "CAFE", "COFFEE·BAKE", ""}, {"#s ROASTERS", "CAFE", "COFFEE", ""},
            {"%s 디저트", "CAFE", "DESSERT", "bakery"}, {"%s 와인", "BAR", "WINE·PLATE", "wine"},
            {"%s 파스타", "FOOD", "PASTA", "pasta"}, {"%s 소품", "RETAIL", "소품·문구", "goods"},
            {"%s 플라워", "RETAIL", "FLOWER", "flower"},
    };
    private static final String[][] SANGSU_U = {
            {"카페 %s", "CAFE", "2F 좌석", ""}, {"%s 와인", "BAR", "WINE·PLATE", "wine"},
            {"%s 스튜디오", "STUDIO", "PHOTO", "photo"}, {"%s 디자인", "OFFICE", "DESIGN", ""},
    };
    private static final String[][] MANGWON_G = {
            {"%s반찬", "MARKET", "반찬·김치", "banchan"}, {"%s떡집", "MARKET", "떡·한과", "tteok"},
            {"%s청과", "MARKET", "과일·채소", "fruit"}, {"%s정육점", "MARKET", "한우·한돈", "meat"},
            {"%s수산", "MARKET", "생선·조개", "fish"}, {"%s닭강정", "MARKET", "닭강정·튀김", "chicken"},
            {"%s고로케", "MARKET", "고로케·꽈배기", "bakery"}, {"%s칼국수", "FOOD", "칼국수·수제비", "kalguksu"},
            {"%s건어물", "MARKET", "멸치·김", "dried"}, {"%s방앗간", "MARKET", "참기름·고춧가루", "mill"},
            {"%s분식", "FOOD", "떡볶이·순대", "bunsik"}, {"%s슈퍼", "STORE", "", "super"},
    };
    private static final String[][] CAMPUS_G = {
            {"%s복사", "PRINT", "제본·출력", "copy"}, {"%s서점", "BOOK", "전공·수험서", ""},
            {"%s PC방", "PC", "24시간", ""}, {"%s국밥", "FOOD", "순대국밥", "gukbap"},
            {"%s분식", "FOOD", "김밥·라면", "bunsik"}, {"%s호프", "BAR", "치킨·생맥주", "hof"},
            {"카페 %s", "CAFE", "COFFEE", ""}, {"%s 파스타", "FOOD", "샤로수길", "pasta"},
            {"%s라멘", "FOOD", "라멘·덮밥", "ramen"}, {"24시 편의점", "STORE", "", "cvs"},
            {"%s빨래방", "LAUNDRY", "코인 세탁", "coin"},
    };
    private static final String[][] CAMPUS_U = {
            {"%s PC방", "PC", "24시간", ""}, {"%s독서실", "ACADEMY", "1인 좌석", "study"},
            {"%s당구장", "BILLIARD", "당구·포켓볼", ""}, {"%s고시학원", "ACADEMY", "공무원·고시", ""},
            {"%s노래방", "NORAE", "노래연습장", ""}, {"%s호프", "BAR", "2층 호프", "hof"},
    };
    private static final String[][] YEOUIDO_G = {
            {"%s한식뷔페", "FOOD", "점심 뷔페", "buffet"}, {"%s칼국수", "FOOD", "칼국수·보쌈", "kalguksu"},
            {"카페 %s", "CAFE", "COFFEE", ""}, {"24시 편의점", "STORE", "", "cvs"},
            {"%s김밥", "FOOD", "김밥·라면", "bunsik"}, {"%s약국", "PHARMACY", "", ""},
            {"%s설렁탕", "FOOD", "설렁탕·수육", "gukbap"}, {"%s증권", "OFFICE", "투자상담", "bank"},
            {"%s샐러드", "FOOD", "SALAD", "salad"},
    };
    private static final String[][] YEOUIDO_U = {
            {"%s회계법인", "OFFICE", "", ""}, {"%s투자자문", "OFFICE", "", ""}, {"%s법률사무소", "OFFICE", "", ""},
            {"%s세무사", "OFFICE", "세무·기장", ""}, {"%s내과", "CLINIC", "건강검진", "clinic"},
            {"%s치과", "CLINIC", "임플란트", "dental"}, {"%s안과", "CLINIC", "라식·라섹", "clinic"},
            {"%s일식", "FOOD", "점심특선", "sushi"}, {"%s중식당", "FOOD", "코스요리", "chinese"},
            {"%s필라테스", "STUDIO", "1:1 PT", "pilates"}, {"%s어학원", "ACADEMY", "비즈니스 영어", ""},
            {"%s호프", "BAR", "생맥주", "hof"},
    };
    private static final String[][] TRAIL_G = {
            {"%s산채비빔밥", "FOOD", "산채·더덕구이", "sanchae"}, {"%s파전", "FOOD", "파전·막걸리", "pajeon"},
            {"%s두부집", "FOOD", "손두부·청국장", "tofu"}, {"%s아웃도어", "RETAIL", "등산복·등산화", "outdoor"},
            {"%s등산장비", "RETAIL", "스틱·배낭", "outdoor"}, {"%s백숙", "FOOD", "닭백숙·도토리묵", "baeksuk"},
            {"%s손칼국수", "FOOD", "칼국수·감자전", "kalguksu"}, {"등산로 매점", "STORE", "생수·김밥", "super"},
    };
    private static final String[][] TRAIL_U = {
            {"%s막걸리", "FOOD", "2층 단체석", "pajeon"}, {"%s산장", "FOOD", "백숙·오리", "baeksuk"},
            {"%s아웃도어", "RETAIL", "2F 할인매장", "outdoor"},
    };
    private static final String[][] OLDTOWN_G = {
            {"%s슈퍼", "STORE", "", "super"}, {"%s세탁소", "LAUNDRY", "세탁·수선", ""},
            {"%s철물점", "TOOLS", "철물·열쇠", "tools"}, {"%s미용실", "STUDIO", "커트·파마", "salon"},
            {"%s방앗간", "MARKET", "참기름·떡", "mill"}, {"%s쌀상회", "MARKET", "쌀·잡곡", "rice"},
            {"%s분식", "FOOD", "떡볶이·김밥", "bunsik"}, {"%s수선", "LAUNDRY", "옷수선", "tailor"},
            {"%s부동산", "OFFICE", "공인중개사", "realty"}, {"%s약국", "PHARMACY", "", ""},
    };
    private static final String[][] PLAIN_G = {
            {"%s약국", "PHARMACY", "", ""}, {"%s부동산", "OFFICE", "공인중개사", "realty"},
            {"%s슈퍼", "STORE", "", "super"}, {"%s세탁소", "LAUNDRY", "세탁·수선", ""},
            {"할매국밥", "FOOD", "순대국밥", "gukbap"}, {"%s분식", "FOOD", "김밥·라면", "bunsik"},
            {"%s반점", "FOOD", "짜장·짬뽕", "chinese"}, {"%s치킨", "FOOD", "후라이드", "chicken"},
            {"카페 %s", "CAFE", "COFFEE", ""}, {"%s미용실", "STUDIO", "커트·파마", "salon"},
            {"%s안경", "RETAIL", "안경·렌즈", "optical"}, {"24시 편의점", "STORE", "", "cvs"},
    };
    private static final String[][] PLAIN_U = {
            {"%s수학학원", "ACADEMY", "초·중등부", ""}, {"%s영어학원", "ACADEMY", "초·중등부", ""},
            {"%s태권도", "STUDIO", "태권도장", "taekwondo"}, {"%s내과의원", "CLINIC", "내과·소아과", "clinic"},
            {"%s치과", "CLINIC", "임플란트", "dental"}, {"%s한의원", "CLINIC", "추나·침", "therapy"},
            {"%s노래방", "NORAE", "노래연습장", ""}, {"%s PC방", "PC", "최신 사양", ""},
            {"%s당구장", "BILLIARD", "당구·포켓볼", ""}, {"%s세무회계", "OFFICE", "세무·회계", ""},
            {"%s필라테스", "STUDIO", "1:1 PT", "pilates"},
    };

    // ------------------------------------------------------------------ 재료 {벽, 띠, 1층 기단·기둥, 창틀}

    private static final Block QUARTZ_BRICKS = Block.of("quartz_bricks", 0xEAE4DC);
    private static final Block CALCITE_TILE = Block.of("calcite", 0xDFE0DC);
    private static final Block BROWN_TILE = Block.of("terracotta", 0x985E43);
    private static final Block PACKED_MUD_TILE = Block.of("packed_mud", 0x8E6B50);
    private static final Block DARK_PLANKS = Block.of("dark_oak_planks", 0x432B14);
    private static final Block TUFF = Block.of("polished_tuff", 0x626A65);

    private static Block[][] skins(Look look) {
        return switch (look) {
            case EULJIRO -> new Block[][]{
                    {WHITE_TERRACOTTA, SMOOTH_STONE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {SANDSTONE, WHITE_TERRACOTTA, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {MUD_BRICKS, SMOOTH_STONE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {BROWN_TILE, WHITE_CONCRETE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {CALCITE_TILE, LIGHT_GRAY_TERRACOTTA, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {LIGHT_GRAY_TERRACOTTA, SMOOTH_STONE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {PACKED_MUD_TILE, SMOOTH_STONE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
            };
            case MYEONGDONG -> new Block[][]{
                    {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, GRAY_CONCRETE},
                    {QUARTZ_BRICKS, SMOOTH_STONE, POLISHED_DIORITE, LIGHT_GRAY_CONCRETE},
                    {POLISHED_ANDESITE, SMOOTH_STONE, POLISHED_DEEPSLATE, GRAY_CONCRETE},
                    {GRAY_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_DEEPSLATE, BLACK_CONCRETE},
                    {SMOOTH_QUARTZ, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, GRAY_CONCRETE},
            };
            case GANGNAM -> new Block[][]{
                    {POLISHED_DIORITE, SMOOTH_STONE, POLISHED_ANDESITE, GRAY_CONCRETE},
                    {POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE, POLISHED_DEEPSLATE, BLACK_CONCRETE},
                    {SMOOTH_QUARTZ, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, GRAY_CONCRETE},
                    {SMOOTH_STONE, LIGHT_GRAY_CONCRETE, POLISHED_DEEPSLATE, BLACK_CONCRETE},
                    {TUFF, SMOOTH_STONE, POLISHED_DEEPSLATE, GRAY_CONCRETE},
                    {POLISHED_GRANITE, SMOOTH_STONE, POLISHED_ANDESITE, GRAY_CONCRETE},
            };
            case MUKJA, BANGI -> new Block[][]{
                    {BRICKS, SMOOTH_STONE, POLISHED_GRANITE, WHITE_CONCRETE},
                    {BROWN_TILE, SMOOTH_STONE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {WHITE_TERRACOTTA, LIGHT_GRAY_TERRACOTTA, POLISHED_GRANITE, WHITE_CONCRETE},
                    {MUD_BRICKS, SMOOTH_STONE, POLISHED_ANDESITE, WHITE_CONCRETE},
                    {LIGHT_GRAY_CONCRETE, WHITE_CONCRETE, POLISHED_ANDESITE, GRAY_CONCRETE},
                    {SANDSTONE, SMOOTH_STONE, POLISHED_GRANITE, WHITE_CONCRETE},
            };
            case ITAEWON -> new Block[][]{
                    {BRICKS, BLACK_CONCRETE, POLISHED_BLACKSTONE, BLACK_CONCRETE},
                    {DEEPSLATE_BRICKS, POLISHED_DEEPSLATE, POLISHED_BLACKSTONE, BLACK_CONCRETE},
                    {WHITE_CONCRETE, GRAY_CONCRETE, POLISHED_BLACKSTONE, BLACK_CONCRETE},
                    {LIGHT_GRAY_CONCRETE, GRAY_CONCRETE, POLISHED_BLACKSTONE, BLACK_CONCRETE},
                    {MUD_BRICKS, BLACK_CONCRETE, POLISHED_BLACKSTONE, BLACK_CONCRETE},
                    {QUARTZ_BRICKS, BLACK_CONCRETE, POLISHED_BLACKSTONE, BLACK_CONCRETE},
            };
            case HONGDAE -> new Block[][]{
                    {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, GRAY_CONCRETE},
                    {GRAY_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_BLACKSTONE, BLACK_CONCRETE},
                    {BRICKS, SMOOTH_STONE, POLISHED_DEEPSLATE, BLACK_CONCRETE},
                    {LIGHT_GRAY_CONCRETE, WHITE_CONCRETE, POLISHED_ANDESITE, GRAY_CONCRETE},
                    {WHITE_TERRACOTTA, SMOOTH_STONE, POLISHED_GRANITE, GRAY_CONCRETE},
            };
            case SANGSU -> new Block[][]{
                    {QUARTZ_BRICKS, SMOOTH_STONE, POLISHED_ANDESITE, BLACK_CONCRETE},
                    {LIGHT_GRAY_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, BLACK_CONCRETE},
                    {BRICKS, SMOOTH_STONE, POLISHED_DEEPSLATE, BLACK_CONCRETE},
                    {WHITE_CONCRETE, WHITE_CONCRETE, POLISHED_ANDESITE, DARK_PLANKS},
            };
            case MANGWON, OLDTOWN -> new Block[][]{
                    {BRICKS, SMOOTH_STONE, POLISHED_ANDESITE, WHITE_CONCRETE},
                    {WHITE_TERRACOTTA, SMOOTH_STONE, POLISHED_ANDESITE, WHITE_CONCRETE},
                    {LIGHT_GRAY_CONCRETE, SMOOTH_STONE, POLISHED_ANDESITE, WHITE_CONCRETE},
                    {MUD_BRICKS, SMOOTH_STONE, POLISHED_ANDESITE, WHITE_CONCRETE},
                    {BRICKS, WHITE_CONCRETE, POLISHED_GRANITE, WHITE_CONCRETE},
            };
            case CAMPUS -> new Block[][]{
                    {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {CALCITE_TILE, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {LIGHT_GRAY_CONCRETE, WHITE_CONCRETE, POLISHED_ANDESITE, WHITE_CONCRETE},
                    {WHITE_TERRACOTTA, SMOOTH_STONE, POLISHED_GRANITE, WHITE_CONCRETE},
                    {BRICKS, SMOOTH_STONE, POLISHED_ANDESITE, WHITE_CONCRETE},
            };
            case YEOUIDO -> new Block[][]{
                    {POLISHED_GRANITE, SMOOTH_STONE, POLISHED_DEEPSLATE, BROWN_CONCRETE},
                    {POLISHED_DEEPSLATE, POLISHED_ANDESITE, POLISHED_BLACKSTONE, BLACK_CONCRETE},
                    {POLISHED_ANDESITE, SMOOTH_STONE, POLISHED_DEEPSLATE, GRAY_CONCRETE},
                    {BROWN_TILE, SMOOTH_STONE, POLISHED_GRANITE, BROWN_CONCRETE},
            };
            case TRAIL -> new Block[][]{
                    {WHITE_TERRACOTTA, SPRUCE_PLANKS, STONE_BRICKS, DARK_PLANKS},
                    {SPRUCE_PLANKS, DARK_PLANKS, COBBLESTONE, DARK_PLANKS},
                    {CALCITE_TILE, DARK_PLANKS, MOSSY_STONE_BRICKS, DARK_PLANKS},
                    {MUD_BRICKS, SPRUCE_PLANKS, STONE_BRICKS, DARK_PLANKS},
            };
            case PLAIN -> new Block[][]{
                    {POLISHED_GRANITE, SMOOTH_STONE, POLISHED_GRANITE, LIGHT_GRAY_CONCRETE},
                    {WHITE_TERRACOTTA, LIGHT_GRAY_CONCRETE, POLISHED_ANDESITE, LIGHT_GRAY_CONCRETE},
                    {LIGHT_GRAY_CONCRETE, WHITE_CONCRETE, POLISHED_ANDESITE, GRAY_CONCRETE},
                    {BRICKS, SMOOTH_STONE, POLISHED_GRANITE, WHITE_CONCRETE},
                    {WHITE_CONCRETE, LIGHT_GRAY_CONCRETE, POLISHED_DIORITE, GRAY_CONCRETE},
            };
        };
    }

    /** 간판 {판 블록, 표지판 나무, 글자색} */
    private static Object[][] boards(Look look) {
        return switch (look) {
            case EULJIRO, OLDTOWN -> new Object[][]{
                    {WHITE_CONCRETE, "birch", "red"}, {WHITE_CONCRETE, "birch", "blue"}, {BLUE_CONCRETE, "dark_oak", "white"},
                    {RED_CONCRETE, "mangrove", "white"}, {YELLOW_CONCRETE, "bamboo", "black"}, {LIGHT_GRAY_CONCRETE, "oak", "black"},
                    {GREEN_CONCRETE, "warped", "white"}};
            case MYEONGDONG -> new Object[][]{
                    {WHITE_CONCRETE, "birch", "black"}, {BLACK_CONCRETE, "dark_oak", "white"}, {PINK_CONCRETE, "cherry", "white"},
                    {RED_CONCRETE, "mangrove", "white"}, {WHITE_CONCRETE, "birch", "red"}, {GRAY_CONCRETE, "dark_oak", "white"},
                    {BLUE_CONCRETE, "dark_oak", "white"}};
            case GANGNAM -> new Object[][]{
                    {GRAY_CONCRETE, "dark_oak", "white"}, {WHITE_CONCRETE, "birch", "blue"}, {BLACK_CONCRETE, "dark_oak", "white"},
                    {WHITE_CONCRETE, "birch", "green"}, {BLUE_CONCRETE, "dark_oak", "white"}, {WHITE_CONCRETE, "birch", "black"}};
            case MUKJA, BANGI -> new Object[][]{
                    {RED_CONCRETE, "mangrove", "white"}, {YELLOW_CONCRETE, "bamboo", "red"}, {BLACK_CONCRETE, "dark_oak", "yellow"},
                    {ORANGE_CONCRETE, "acacia", "white"}, {WHITE_CONCRETE, "birch", "red"}, {GREEN_CONCRETE, "warped", "white"},
                    {BLUE_CONCRETE, "dark_oak", "white"}, {BROWN_CONCRETE, "spruce", "white"}};
            case ITAEWON -> new Object[][]{
                    {BLACK_CONCRETE, "dark_oak", "white"}, {DARK_PLANKS, "dark_oak", "white"}, {SPRUCE_PLANKS, "spruce", "white"},
                    {GRAY_CONCRETE, "dark_oak", "yellow"}, {BLACK_CONCRETE, "dark_oak", "orange"}};
            case HONGDAE -> new Object[][]{
                    {BLACK_CONCRETE, "dark_oak", "white"}, {WHITE_CONCRETE, "birch", "black"}, {PINK_CONCRETE, "cherry", "white"},
                    {YELLOW_CONCRETE, "bamboo", "black"}, {RED_CONCRETE, "mangrove", "white"}, {BLUE_CONCRETE, "dark_oak", "white"},
                    {PURPLE_CONCRETE, "crimson", "white"}, {GREEN_CONCRETE, "warped", "white"}};
            case SANGSU -> new Object[][]{
                    {WHITE_CONCRETE, "birch", "black"}, {BLACK_CONCRETE, "dark_oak", "white"}, {OAK_PLANKS, "oak", "black"}};
            case MANGWON -> new Object[][]{
                    {WHITE_CONCRETE, "birch", "red"}, {WHITE_CONCRETE, "birch", "blue"}, {YELLOW_CONCRETE, "bamboo", "black"},
                    {GREEN_CONCRETE, "warped", "white"}, {RED_CONCRETE, "mangrove", "white"}, {BLUE_CONCRETE, "dark_oak", "white"}};
            case CAMPUS -> new Object[][]{
                    {WHITE_CONCRETE, "birch", "blue"}, {BLUE_CONCRETE, "dark_oak", "white"}, {YELLOW_CONCRETE, "bamboo", "black"},
                    {RED_CONCRETE, "mangrove", "white"}, {GREEN_CONCRETE, "warped", "white"}, {WHITE_CONCRETE, "birch", "red"}};
            case YEOUIDO -> new Object[][]{
                    {GRAY_CONCRETE, "dark_oak", "white"}, {WHITE_CONCRETE, "birch", "black"}, {BROWN_CONCRETE, "spruce", "white"},
                    {BLUE_CONCRETE, "dark_oak", "white"}};
            case TRAIL -> new Object[][]{
                    {DARK_PLANKS, "dark_oak", "white"}, {SPRUCE_PLANKS, "spruce", "yellow"}, {GREEN_CONCRETE, "warped", "white"},
                    {OAK_PLANKS, "oak", "black"}, {RED_CONCRETE, "mangrove", "white"}};
            case PLAIN -> ShopHouse.BOARDS;
        };
    }

    // ------------------------------------------------------------------ 상태

    private final Random r;
    private final Look look;
    private final boolean party;
    private final int w, d, floors, g, roof;
    private final int i0, i1, ia, ib, ja, jb, jf;
    private final boolean core, elevator, terrace, mirror;
    private final int lane, width, cx1, oi, hc, depth, jsw, jsb;
    /** 서는 높이들 (층마다 + 옥상) */
    final int[] levels;
    private final Win win;
    private final Front front;
    private final Block wall, band, base, frame, glass;
    private final Object[][] boards;
    private final int density;
    private final String stairMat;
    private final int stairRgb;
    private final Tenant[] tenants;
    private Tenant second;
    private int split = -1;
    private final Voxels v;
    private Voxels out;
    private final Frame F;

    private StreetShop(String district, boolean partyWall, int w, int d, int floors, Random r) {
        this.r = r;
        this.w = w;
        this.d = d;
        this.floors = Math.max(1, floors);
        this.party = partyWall;
        look = lookOf(district, this.floors, r);
        Block[][] sk = skins(look);
        Block[] skin = sk[r.nextInt(sk.length)];
        wall = skin[0];
        band = skin[1];
        base = skin[2];
        frame = skin[3];
        boards = boards(look);
        win = windowOf(look, r);
        front = frontOf(look, r);
        glass = switch (look) {
            case GANGNAM -> r.nextBoolean() ? GRAY_PANE : GLASS_PANE;
            case YEOUIDO -> r.nextBoolean() ? Block.of("brown_stained_glass_pane", 0x664C33) : GRAY_PANE;
            default -> GLASS_PANE;
        };
        density = switch (look) {
            case MYEONGDONG, MUKJA, BANGI, HONGDAE -> 3;
            case EULJIRO, GANGNAM, MANGWON, CAMPUS, TRAIL, PLAIN -> 2;
            default -> 1;
        };
        double tc = switch (look) {
            case ITAEWON -> 0.7;
            case SANGSU -> 0.6;
            case HONGDAE, TRAIL -> 0.3;
            case MUKJA, GANGNAM -> 0.12;
            default -> 0;
        };
        terrace = w >= 8 && r.nextDouble() < tc;
        stairMat = switch (look) {
            case EULJIRO, OLDTOWN, MANGWON, CAMPUS -> "stone_brick";
            case GANGNAM, YEOUIDO, MYEONGDONG -> "polished_granite";
            case TRAIL, SANGSU -> "spruce";
            default -> "polished_andesite";
        };
        stairRgb = switch (stairMat) {
            case "stone_brick" -> 0x7A7979;
            case "polished_granite" -> 0x9A6A59;
            case "spruce" -> 0x725430;
            default -> 0x848685;
        };
        i0 = !party && w >= 10 ? 1 : 0;
        i1 = w - 1 - i0;
        jf = d - 2;
        ia = i0 + 1;
        ib = i1 - 1;
        ja = 1;
        jb = jf - 1;
        core = this.floors >= 2 || terrace;
        lane = w >= 16 && this.floors >= 4 ? 2 : 1;
        width = 2 * lane + 1;
        int gh = switch (look) {
            case MYEONGDONG, GANGNAM, YEOUIDO -> 5;
            case BANGI, HONGDAE -> this.floors >= 5 ? 5 : 4;
            default -> 4;
        };
        int[] lv = Floors.levels(gh, Floors.OFFICE, this.floors);
        boolean elev = false;
        if (core && this.floors >= 6) {
            // 계단실 뒤 엘리베이터: 깊이와 1층 복도 옆 가게 폭이 될 때만 (1층 층고를 낮춰 보기도 함)
            for (int g2 : new int[]{gh, 4}) {
                int[] l2 = Floors.levels(g2, Floors.OFFICE, this.floors);
                int sw = jf - 1 - Interior.stairDepth(l2);
                if (sw - 3 >= 0 && ib - (i0 + width + 4) + 1 >= 3) {
                    gh = g2;
                    lv = l2;
                    elev = true;
                    break;
                }
            }
        }
        g = gh;
        levels = lv;
        elevator = elev;
        roof = levels[this.floors];
        depth = core ? Interior.stairDepth(levels) : 0;
        jsw = jf - 1 - depth;
        cx1 = core ? i0 + width + 1 : i0;
        oi = i0 + width;
        hc = cx1 + 1;
        jsb = !core ? jf + 1 : elevator ? jsw - 3 : jsw;
        mirror = r.nextBoolean();
        v = new Voxels(w, d, -1, roof + 8);
        F = Frame.of(v);
        tenants = new Tenant[this.floors];
        tenants[0] = groundTenant(null);
        int fs = shopStart(), fw = ib - fs + 1;
        if (fw >= 12 && look != Look.SANGSU) {
            split = fs + fw / 2;
            second = groundTenant(tenants[0]);
        }
        for (int k = 1; k < this.floors; k++) {
            tenants[k] = livable(k, upperTenant(k, tenants[k - 1]));
        }
    }

    /** 구역마다 다른 상가 건물 */
    static Voxels build(String district, boolean partyWall, int w, int d, int floors, Random r) {
        return create(district, partyWall, w, d, floors, r).out;
    }

    /** 검사·미리보기용: 건물과 층 높이 */
    static StreetShop create(String district, boolean partyWall, int w, int d, int floors, Random r) {
        StreetShop s = new StreetShop(district, partyWall, w, d, floors, r);
        s.shell();
        s.core();
        s.elevator();
        s.groundFloor();
        for (int k = 1; k < s.floors; k++) {
            s.upperFacade(k);
            s.upperInterior(k);
        }
        s.roofDeck();
        s.signs();
        s.details();
        s.out = s.mirror ? mirrored(s.v) : s.v;
        s.out.connect();
        return s;
    }

    Voxels voxels() {
        return out;
    }

    /** 1층 가게 안 한 칸 {i, j} (검사용, 뒤집기 반영) */
    int[] probe(int k) {
        int i = k == 0 ? Math.min(ib, shopStart() + 1) : Math.min(ib, hc + 1), j = jb - 1;
        return new int[]{mirror ? w - 1 - i : i, j};
    }

    Tenant tenant(int k) {
        return tenants[k];
    }

    Look look() {
        return look;
    }

    /** 배치 이름 (예: "을지로 인쇄소 건물", "먹자골목 상가", "근린생활시설") */
    static String name(String district, Random r) {
        String[] names = switch (district == null ? "" : district) {
            case "junggu" -> new String[]{"을지로 인쇄소 건물", "을지로 조명상가", "을지로 공구상가", "명동 상가", "남대문 상가", "을지로 상가"};
            case "gangnam" -> new String[]{"근린생활시설", "메디컬 빌딩", "학원 빌딩", "이면 상가"};
            case "gwangjin" -> new String[]{"건대 먹자골목 상가", "먹자골목 상가", "양꼬치 골목 상가"};
            case "songpa" -> new String[]{"방이동 먹자골목 상가", "먹자골목 상가"};
            case "yongsan" -> new String[]{"이태원 상가", "이태원 펍 건물", "경리단길 상가"};
            case "hongdae" -> new String[]{"홍대 상가", "상수동 카페 건물", "홍대 걷고싶은거리 상가"};
            case "mapo" -> new String[]{"망원시장 가게", "망원동 상가주택"};
            case "university" -> new String[]{"녹두거리 상가", "샤로수길 상가", "고시원 건물"};
            case "yeouido" -> new String[]{"여의도 근린상가", "여의도 상가"};
            case "bukhansan" -> new String[]{"등산로 입구 식당", "등산복 가게", "등산로 상가"};
            case "namsan" -> new String[]{"동네 가게", "후암동 슈퍼", "동네 상가주택"};
            default -> new String[]{"근린생활시설", "상가 건물"};
        };
        return names[r.nextInt(names.length)];
    }

    // ------------------------------------------------------------------ 구역 성격

    private static Look lookOf(String district, int floors, Random r) {
        return switch (district == null ? "" : district) {
            case "junggu" -> r.nextInt(10) < (floors >= 5 ? 4 : 7) ? Look.EULJIRO : Look.MYEONGDONG;
            case "gangnam" -> Look.GANGNAM;
            case "gwangjin" -> Look.MUKJA;
            case "songpa" -> Look.BANGI;
            case "yongsan" -> Look.ITAEWON;
            case "hongdae" -> floors <= 3 && r.nextInt(10) < 6 ? Look.SANGSU : Look.HONGDAE;
            case "mapo" -> Look.MANGWON;
            case "university" -> Look.CAMPUS;
            case "yeouido" -> Look.YEOUIDO;
            case "bukhansan" -> Look.TRAIL;
            case "namsan" -> Look.OLDTOWN;
            default -> Look.PLAIN;
        };
    }

    private static Win windowOf(Look look, Random r) {
        return switch (look) {
            case EULJIRO -> r.nextInt(10) < 6 ? Win.PUNCHED : Win.RIBBON;
            case MYEONGDONG -> Win.CURTAIN;
            case GANGNAM -> r.nextInt(10) < 6 ? Win.RIBBON : Win.CURTAIN;
            case MUKJA, PLAIN, TRAIL -> Win.PUNCHED;
            case BANGI -> r.nextBoolean() ? Win.PUNCHED : Win.RIBBON;
            case ITAEWON -> Win.STEEL;
            case HONGDAE -> new Win[]{Win.RIBBON, Win.CURTAIN, Win.PUNCHED}[r.nextInt(3)];
            case SANGSU -> r.nextBoolean() ? Win.STEEL : Win.CURTAIN;
            case MANGWON, CAMPUS, OLDTOWN -> Win.SMALL;
            case YEOUIDO -> Win.RIBBON;
        };
    }

    private static Front frontOf(Look look, Random r) {
        return switch (look) {
            case EULJIRO -> Front.SHUTTER;
            case OLDTOWN -> r.nextBoolean() ? Front.SHUTTER : Front.GLASS;
            case MANGWON -> Front.OPEN;
            case ITAEWON, SANGSU -> Front.FOLDING;
            case MUKJA, BANGI -> r.nextInt(10) < 3 ? Front.FOLDING : Front.GLASS;
            default -> Front.GLASS;
        };
    }

    private String[][] groundPool() {
        return switch (look) {
            case EULJIRO -> EULJIRO_G;
            case MYEONGDONG -> MYEONGDONG_G;
            case GANGNAM -> GANGNAM_G;
            case MUKJA -> MUKJA_G;
            case BANGI -> BANGI_G;
            case ITAEWON -> ITAEWON_G;
            case HONGDAE -> HONGDAE_G;
            case SANGSU -> SANGSU_G;
            case MANGWON -> MANGWON_G;
            case CAMPUS -> CAMPUS_G;
            case YEOUIDO -> YEOUIDO_G;
            case TRAIL -> TRAIL_G;
            case OLDTOWN -> OLDTOWN_G;
            case PLAIN -> PLAIN_G;
        };
    }

    private String[][] upperPool() {
        return switch (look) {
            case EULJIRO -> EULJIRO_U;
            case MYEONGDONG -> MYEONGDONG_U;
            case GANGNAM -> GANGNAM_U;
            case MUKJA -> MUKJA_U;
            case BANGI -> BANGI_U;
            case ITAEWON -> ITAEWON_U;
            case HONGDAE -> HONGDAE_U;
            case SANGSU -> SANGSU_U;
            case CAMPUS -> CAMPUS_U;
            case YEOUIDO -> YEOUIDO_U;
            case TRAIL -> TRAIL_U;
            default -> PLAIN_U;
        };
    }

    private Tenant tenant(String[] s) {
        String name = s[0].replace("%s", KoreanNames.prefix(r)).replace("#s", EN[r.nextInt(EN.length)]);
        return new Tenant(Use.valueOf(s[1]), name, s[2], s[3]);
    }

    private Tenant pick(String[][] pool, Tenant not) {
        Tenant t = tenant(pool[r.nextInt(pool.length)]);
        if (not != null && t.use() == not.use() && t.kind().equals(not.kind())) {
            t = tenant(pool[r.nextInt(pool.length)]);
        }
        return t;
    }

    private Tenant groundTenant(Tenant not) {
        return pick(groundPool(), not);
    }

    private static final Tenant HOME = new Tenant(Use.HOME, "", "", "");

    /** 위층 쓰임 (구역마다 층 성격) */
    private Tenant upperTenant(int k, Tenant below) {
        boolean top = k == floors - 1;
        return switch (look) {
            case MANGWON, OLDTOWN -> r.nextInt(10) < 8 || k > 1 ? HOME : pick(look == Look.MANGWON
                    ? new String[][]{{"%s미용실", "STUDIO", "커트·파마", "salon"}, {"%s세무사", "OFFICE", "세무·기장", ""}}
                    : new String[][]{{"%s미용실", "STUDIO", "커트·파마", "salon"}, {"%s교습소", "ACADEMY", "피아노", "piano"}}, below);
            case TRAIL -> k == 1 && r.nextInt(10) < 7 ? pick(TRAIL_U, below) : HOME;
            case SANGSU -> k == 1 && r.nextInt(10) < 6 ? pick(SANGSU_U, below) : r.nextBoolean() ? HOME : pick(SANGSU_U, below);
            case CAMPUS -> {
                if (k >= 2 || (k == 1 && floors >= 5 && r.nextInt(10) < 3)) {
                    int x = r.nextInt(10);
                    yield x < 4 ? new Tenant(Use.GOSIWON, KoreanNames.prefix(r) + "고시원", "1인실 월 35만", "")
                            : x < 8 ? new Tenant(Use.ONEROOM, KoreanNames.prefix(r) + "하우스", "원룸 임대", "")
                            : pick(CAMPUS_U, below);
                }
                yield pick(CAMPUS_U, below);
            }
            case MUKJA, BANGI -> k == 1 && r.nextInt(10) < 5 ? pick(groundPool(), below)
                    : top && floors >= 5 && r.nextInt(10) < 3 ? new Tenant(Use.OFFICE, KoreanNames.prefix(r) + "기획", "", "") : pick(upperPool(), below);
            case EULJIRO -> top && floors >= 3 && r.nextInt(10) < 3 ? HOME : pick(EULJIRO_U, below);
            case ITAEWON -> top && floors >= 3 && r.nextInt(10) < 3 ? HOME : pick(ITAEWON_U, below);
            default -> pick(upperPool(), below);
        };
    }

    /** 1층 가게 앞이 시작하는 칸 (계단실·엘리베이터 복도 다음) */
    private int shopStart() {
        return elevator ? hc + 2 : core ? hc : ia;
    }

    // ------------------------------------------------------------------ 칸 판정

    /** 계단실 줄(벽·엘리베이터 포함) 안인지 */
    private boolean inStrip(int i, int j) {
        return core && i <= cx1 && j >= jsb;
    }

    /** 층 바닥 (계단실 줄 밖, 겉벽 안) */
    private boolean plate(int i, int j) {
        return i >= ia && i <= ib && j >= ja && j <= jb && !inStrip(i, j);
    }

    /** 1층 엘리베이터 복도 (복도와 가게 쪽 벽) */
    private boolean corridor1F(int i, int j) {
        return elevator && i >= hc && i <= hc + 1 && j >= jsw - 3 && j <= jb;
    }

    // ------------------------------------------------------------------ 몸체

    private void shell() {
        Block pave = look == Look.TRAIL || look == Look.SANGSU ? POLISHED_ANDESITE : LIGHT_GRAY_CONCRETE;
        v.fill(0, -1, 0, w - 1, -1, d - 1, pave);
        for (int k = 0; k <= floors; k++) {
            v.fill(i0, levels[k] - 1, 0, i1, levels[k] - 1, jf, k == 0 ? Interior.LANDING : SMOOTH_STONE);
        }
        v.walls(i0, 0, 0, i1, roof - 1, jf, wall);
        if (!party) {
            // 골목 건물: 옆·뒤 1층 아랫단 돌 마감
            v.walls(i0, 0, 0, i1, 0, jf, base);
        }
        // 층 띠 (바닥 높이): 앞·뒤, 골목 건물은 옆도
        for (int k = 1; k < floors; k++) {
            int y = levels[k] - 1;
            v.fill(i0, y, jf, i1, y, jf, band);
            v.fill(i0, y, 0, i1, y, 0, band);
            if (!party) {
                v.fill(i0, y, 0, i0, y, jf, band);
                v.fill(i1, y, 0, i1, y, jf, band);
            }
        }
    }

    /** 앞 한쪽 계단실 (꺾인 계단, 옥상까지). 층마다 계단참 옆 벽에 그 층으로 나가는 문 */
    private void core() {
        if (!core) {
            return;
        }
        Interior.stairCore(Frame.facing(v, oi, jf - 1, "north"), levels, Interior.CORE_WALL, stairMat, stairRgb, lane);
        // 바깥에 닿는 계단실 벽(옆벽·앞벽, 옥탑 네 벽)은 건물 재료로
        for (int y = 0; y <= roof + 2; y++) {
            for (int j = jsw; j <= jf; j++) {
                for (int i = i0; i <= cx1; i++) {
                    boolean outer = i == i0 || j == jf || (y >= roof && (i == cx1 || j == jsw));
                    Block b = v.get(i, y, j);
                    if (outer && (Interior.CORE_WALL.equals(b) || Interior.LIGHT.equals(b))) {
                        v.set(i, y, j, wall);
                    }
                }
            }
        }
        v.fill(i0, roof + 3, jsw, cx1, roof + 3, jf, band);
        for (int k = 0; k <= floors; k++) {
            int L = levels[k];
            if (k > 0 || elevator) {
                String wood = k == floors ? "spruce" : "pale_oak";
                v.set(cx1, L, jb, Blocks.door(wood, "east", false));
                v.set(cx1, L + 1, jb, Blocks.door(wood, "east", true));
            }
            v.set(cx1, L + 2, jb, Interior.LIGHT);
        }
        // 옥탑 위 피뢰침·안테나
        v.set(i0 + 1, roof + 4, jsw + 1, Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
    }

    /** 계단실 뒤 엘리베이터 (문은 층 쪽), 1층은 계단참 옆문에서 이어지는 복도 */
    private void elevator() {
        if (!elevator) {
            return;
        }
        Interior.elevator(Frame.facing(v, cx1 - 2, jsw - 1, "east"), Arrays.copyOf(levels, floors), Interior.CORE_WALL);
        // 계단실과 승강로 사이 빈틈(넓은 계단실)은 설비 공간(PS)으로 막음
        if (cx1 - 4 >= ia) {
            v.fill(ia, 0, jsw - 3, cx1 - 4, roof - 1, jsw - 1, Interior.CORE_WALL);
        }
        // 옥상 기계실
        v.fill(i0, roof, jsw - 3, cx1, roof + 2, jsw, wall);
        v.fill(i0, roof + 3, jsw - 3, cx1, roof + 3, jsw, band);
        // 1층 복도: 계단참 옆문 → 엘리베이터 앞
        v.fill(hc + 1, 0, jsw - 3, hc + 1, g - 2, jb, Interior.INNER_WALL);
        v.fill(hc, 0, jsw - 3, hc, g - 2, jsw - 3, Interior.INNER_WALL);
        v.fill(hc, -1, jsw - 2, hc, -1, jb, Interior.LANDING);
        v.set(hc, g - 2, (jsw + jb) / 2, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 1층

    private void groundFloor() {
        if (core) {
            entrance();
        }
        int fs = shopStart();
        if (split < 0) {
            shop(fs, ib, tenants[0], (i, j) -> plate(i, j) && !corridor1F(i, j), ia, ib);
        } else {
            int m = split;
            v.fill(m, 0, ja, m, g - 2, jb, Interior.INNER_WALL);
            shop(fs, m - 1, tenants[0], (i, j) -> plate(i, j) && !corridor1F(i, j) && i < m, ia, m - 1);
            shop(m + 1, ib, second, (i, j) -> plate(i, j) && i > m, m + 1, ib);
        }
    }

    /** 1층 계단 입구: 기둥과 돌 마감, 문, 문 위 유리, 건물 이름판, 층별 안내 */
    private void entrance() {
        for (int y = 0; y <= g - 1; y++) {
            for (int i = i0; i <= cx1; i++) {
                v.set(i, y, jf, y >= 3 ? band : i == i0 || i == cx1 ? wall : base);
            }
        }
        boolean open = look == Look.EULJIRO || look == Look.OLDTOWN || look == Look.MANGWON || look == Look.CAMPUS;
        for (int a = 0; a < lane; a++) {
            int di = oi - a;
            if (open) {
                v.set(di, 0, jf, AIR);
                v.set(di, 1, jf, AIR);
            } else {
                v.set(di, 0, jf, Blocks.door("birch", "south", false));
                v.set(di, 1, jf, Blocks.door("birch", "south", true));
            }
            v.set(di, 2, jf, glass);
        }
        // 건물 이름판 (문 옆 기둥)
        int pi = oi - lane;
        if (pi > i0) {
            v.set(pi, 1, d - 1, sign("dark_oak", "south", "white", false, buildingName(), KoreanNames.address(r)));
        }
        // 문 위 층별 안내
        if (floors >= 2) {
            String[] lines = new String[Math.min(4, floors - 1)];
            int n = 0;
            for (int k = floors - 1; k >= 1 && n < lines.length; k--) {
                Tenant t = tenants[k];
                lines[n++] = (k + 1) + "F " + (t.use() == Use.HOME ? "주택" : t.name());
            }
            v.set(oi, 3, d - 1, sign("birch", "south", "black", false, lines));
        }
    }

    private String buildingName() {
        return switch (look) {
            case GANGNAM, YEOUIDO, MYEONGDONG -> KoreanNames.prefix(r) + "빌딩";
            case CAMPUS -> KoreanNames.prefix(r) + "빌";
            case TRAIL -> KoreanNames.prefix(r) + "산장";
            default -> KoreanNames.prefix(r) + "상가";
        };
    }

    private int curSa, curSb;

    /** 1층 가게 하나: 가게 앞, 바닥, 업종에 맞는 실내 */
    private void shop(int sa, int sb, Tenant t, BiPredicate<Integer, Integer> in, int a0, int a1) {
        if (sb < sa) {
            return;
        }
        int doorI = (sa + sb) / 2;
        storefront(sa, sb, t, doorI);
        Block fl = floorFor(t);
        for (int j = ja; j <= jb; j++) {
            for (int i = a0; i <= a1; i++) {
                if (in.test(i, j)) {
                    v.set(i, -1, j, fl);
                }
            }
        }
        Area A = new Area(a0, a1, ja, jb, 0, g, doorI, jb, in, false);
        furnish(t, A);
    }

    /** 가게 앞 (sa..sb 칸, 높이 0..2), 간판판(3)과 간판 글씨, 차양 */
    private void storefront(int sa, int sb, Tenant t, int doorI) {
        curSa = sa;
        curSb = sb;
        for (int y = 0; y <= 2; y++) {
            if (sa - 1 > cx1 || !core) {
                v.set(sa - 1, y, jf, base);
            }
            v.set(sb + 1, y, jf, base);
        }
        boolean kick = look == Look.EULJIRO || look == Look.OLDTOWN || look == Look.CAMPUS || look == Look.MUKJA || look == Look.PLAIN;
        boolean wideDoor = sb - sa >= 6;
        for (int i = sa; i <= sb; i++) {
            int t3 = i - sa;
            for (int y = 0; y <= 2; y++) {
                Block b = switch (front) {
                    case GLASS -> t3 % 3 == 2 && i != sb ? frame : y == 0 && kick ? base : glass;
                    case SHUTTER -> y == 2 ? frame : AIR;
                    case OPEN -> AIR;
                    case FOLDING -> t3 % 2 == 0 ? frame : glass;
                };
                v.set(i, y, jf, b);
            }
        }
        if (front == Front.SHUTTER && sb - sa >= 3 && r.nextInt(3) == 0) {
            // 반쯤 내린 셔터 (문 반대쪽)
            int a = doorI - sa > sb - doorI ? sa : doorI + 2, b = doorI - sa > sb - doorI ? doorI - 2 : sb;
            for (int i = a; i <= b; i++) {
                v.set(i, 1, jf, Block.of("iron_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            }
        }
        if (front == Front.FOLDING) {
            // 접이문 열어 둔 곳
            for (int i = Math.max(sa, doorI - 1); i <= Math.min(sb, doorI + 1); i++) {
                v.set(i, 0, jf, AIR);
                v.set(i, 1, jf, AIR);
            }
        }
        v.set(doorI, 0, jf, AIR);
        v.set(doorI, 1, jf, AIR);
        if (wideDoor && front == Front.GLASS) {
            v.set(doorI + 1, 0, jf, AIR);
            v.set(doorI + 1, 1, jf, AIR);
            v.set(doorI + 1, 2, jf, glass);
        }
        if (front != Front.OPEN && front != Front.SHUTTER) {
            v.set(doorI, 2, jf, glass);
        }
        // 간판판과 글씨
        Object[] b = board(t);
        for (int y = 3; y <= Math.max(3, g - 2); y++) {
            v.fill(sa - 1, y, jf, sb + 1, y, jf, (Block) b[0]);
        }
        groundSigns(sa, sb, t, b);
        // 차양
        int chance = switch (look) {
            case MANGWON -> 10;
            case TRAIL -> 7;
            case OLDTOWN -> 4;
            case SANGSU, ITAEWON -> t.use() == Use.CAFE || t.use() == Use.FOOD ? 4 : 0;
            case MUKJA, BANGI, CAMPUS -> t.use() == Use.FOOD ? 3 : 0;
            default -> 1;
        };
        if (r.nextInt(10) < chance) {
            String[] woods = switch (look) {
                case MANGWON -> new String[]{"mangrove", "warped", "bamboo", "acacia", "crimson", "spruce"};
                case TRAIL -> new String[]{"dark_oak", "spruce"};
                case SANGSU, ITAEWON -> new String[]{"dark_oak", "spruce", "mangrove"};
                default -> new String[]{"spruce", "dark_oak", "mangrove", "warped", "acacia"};
            };
            String wood = woods[r.nextInt(woods.length)];
            Block awning = Block.of(wood + "_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]", Furniture.rgb(wood));
            for (int i = sa - (front == Front.OPEN ? 1 : 0); i <= sb + (front == Front.OPEN ? 1 : 0); i++) {
                if (v.get(i, 2, d - 1) == null) {
                    v.set(i, 2, d - 1, awning);
                }
            }
        }
    }

    private Object[] board(Tenant t) {
        if (look == Look.PLAIN || t.use() == Use.HOME) {
            return boards[r.nextInt(boards.length)];
        }
        // 업종마다 어울리는 색 (약국 흰 판에 초록 글씨, 편의점 흰 판)
        return switch (t.use()) {
            case PHARMACY -> new Object[]{WHITE_CONCRETE, "birch", "green"};
            case CLINIC -> r.nextBoolean() ? new Object[]{WHITE_CONCRETE, "birch", "blue"} : boards[r.nextInt(boards.length)];
            case STORE -> new Object[]{WHITE_CONCRETE, "birch", r.nextBoolean() ? "blue" : "green"};
            default -> boards[r.nextInt(boards.length)];
        };
    }

    private static final String[] EXTRAS = {"24시간 영업", "배달 가능", "포장 환영", "단체 예약", "OPEN 11:00", "주차 가능", "카드 가능"};

    private String extra() {
        int x = r.nextInt(EXTRAS.length + 2);
        if (x >= EXTRAS.length) {
            return x == EXTRAS.length ? "TEL 02-" + (300 + r.nextInt(600)) + "-" + (1000 + r.nextInt(9000)) : "SINCE 19" + (70 + r.nextInt(30));
        }
        return EXTRAS[x];
    }

    /** 1층 간판 글씨: 가운데 상호, 옆에 업종·메뉴, 간판 많은 동네는 양 끝에 전화번호·영업시간 */
    private void groundSigns(int sa, int sb, Tenant t, Object[] b) {
        String wood = (String) b[1], color = (String) b[2];
        int c = (sa + sb) / 2, y = 3, j = d - 1;
        boolean two = density >= 2 && sb - sa >= 2 && !t.sub().isEmpty();
        int c0 = two ? Math.max(sa, c - (sb - sa >= 4 ? 1 : 0)) : c;
        v.set(c0, y, j, sign(wood, "south", color, true, "", t.name(), two ? "" : t.sub()));
        if (two) {
            v.set(c0 + 1, y, j, sign(wood, "south", color, true, "", t.sub()));
        }
        if (density >= 3 && sb - sa >= 6) {
            v.set(sa, y, j, sign(wood, "south", color, true, "", extra()));
            v.set(sb, y, j, sign(wood, "south", color, true, "", extra()));
        }
    }

    // ------------------------------------------------------------------ 위층 겉모습

    private void upperFacade(int k) {
        int L = levels[k];
        if (core) {
            // 계단참 창
            boolean full = win == Win.CURTAIN || win == Win.RIBBON || win == Win.STEEL;
            for (int i = i0 + 1; i <= cx1 - 1; i++) {
                boolean on = full || i == i0 + 1 + lane;
                v.set(i, L, jf, win == Win.CURTAIN ? glass : wall);
                v.set(i, L + 1, jf, on ? glass : wall);
                v.set(i, L + 2, jf, on ? glass : wall);
            }
        }
        int s = core ? cx1 + 1 : i0 + 1, e = i1 - 1;
        frontWindows(s, e, L);
        // 뒤 창
        for (int i = ia + 1; i + 1 <= ib; i += 4) {
            if (!inStrip(i, ja) && !inStrip(i + 1, ja)) {
                for (int y = L + 1; y <= L + 2; y++) {
                    v.set(i, y, 0, glass);
                    v.set(i + 1, y, 0, glass);
                }
            }
        }
        // 옆 창 (골목 건물)
        if (!party) {
            for (int j = ja + 2; j + 1 <= jb - 1; j += 4) {
                for (int y = L + 1; y <= L + 2; y++) {
                    if (!inStrip(ia, j) && !inStrip(ia, j + 1)) {
                        v.set(i0, y, j, glass);
                        v.set(i0, y, j + 1, glass);
                    }
                    v.set(i1, y, j, glass);
                    v.set(i1, y, j + 1, glass);
                }
            }
        }
        upperSigns(k);
    }

    /** 앞면 창 (구역마다 다른 창 모양) */
    private void frontWindows(int s, int e, int L) {
        int n = e - s + 1;
        if (n <= 0) {
            return;
        }
        switch (win) {
            case PUNCHED, SMALL -> {
                // 2칸 창, 사이 벽 1칸 (작은 창은 2칸)
                int gap = win == Win.SMALL ? 2 : 1, period = 2 + gap;
                int q = Math.max(0, (n - gap) / period), left = n - (q * period + gap);
                int start = s + gap + left / 2;
                for (int m = 0; m < q; m++) {
                    for (int x = 0; x < 2; x++) {
                        int i = start + m * period + x;
                        v.set(i, L + 1, jf, glass);
                        v.set(i, L + 2, jf, glass);
                    }
                }
                if (q == 0) {
                    int i = s + n / 2;
                    v.set(i, L + 1, jf, glass);
                    v.set(i, L + 2, jf, glass);
                }
            }
            case RIBBON -> {
                for (int i = s; i <= e; i++) {
                    boolean mull = (i - s) % 3 == 0 || i == e;
                    v.set(i, L, jf, band);
                    v.set(i, L + 1, jf, mull ? frame : glass);
                    v.set(i, L + 2, jf, mull ? frame : glass);
                }
            }
            case CURTAIN -> {
                for (int i = s; i <= e; i++) {
                    boolean mull = (i - s) % 3 == 0 || i == e;
                    for (int y = L; y <= L + 2; y++) {
                        v.set(i, y, jf, mull ? frame : glass);
                    }
                }
            }
            case STEEL -> {
                // 철골 창: 3칸 유리마다 벽 기둥, 창 아래 검은 틀
                for (int i = s; i <= e; i++) {
                    boolean pier = (i - s) % 4 == 0 || i == e;
                    if (pier) {
                        continue;
                    }
                    v.set(i, L, jf, frame);
                    v.set(i, L + 1, jf, glass);
                    v.set(i, L + 2, jf, glass);
                }
            }
        }
    }

    /** 위층 간판 (층 띠 위 창 아랫줄): 업종마다, 간판 많은 동네는 여러 개 */
    private void upperSigns(int k) {
        Tenant t = tenants[k];
        if (t.use() == Use.HOME) {
            return;
        }
        int L = levels[k];
        int s = core ? cx1 + 1 : i0 + 1, e = i1 - 1, n = e - s + 1;
        if (n < 2) {
            return;
        }
        boolean quiet = t.use() == Use.OFFICE && density < 3;
        if (quiet && r.nextInt(3) > 0) {
            return;
        }
        Object[] b = board(t);
        String wood = (String) b[1], color = (String) b[2];
        String fl = (k + 1) + "F";
        int c = (s + e) / 2;
        if (density <= 1 || quiet) {
            int len = Math.min(n, 4);
            v.fill(c - len / 2, L, jf, c - len / 2 + len - 1, L, jf, (Block) b[0]);
            v.set(c, L, d - 1, sign(wood, "south", color, true, fl, t.name(), t.sub()));
            return;
        }
        v.fill(s, L, jf, e, L, jf, (Block) b[0]);
        v.set(c, L, d - 1, sign(wood, "south", color, true, fl, t.name()));
        if (n >= 5 && !t.sub().isEmpty()) {
            v.set(c + 1, L, d - 1, sign(wood, "south", color, true, "", t.sub()));
        }
        if (density >= 3 && n >= 8) {
            v.set(s + 1, L, d - 1, sign(wood, "south", color, true, fl, t.name()));
            if (look == Look.MUKJA || look == Look.HONGDAE || look == Look.BANGI) {
                v.set(e - 1, L, d - 1, sign(wood, "south", color, true, "", extra()));
            }
        }
    }

    // ------------------------------------------------------------------ 간판 글씨

    /** 표지판 한 줄 폭 (한글 9, 나머지 6, 90 넘으면 안 들어감) */
    static int textWidth(String line) {
        int px = 0;
        for (char ch : line.toCharArray()) {
            px += ch >= 0xAC00 && ch <= 0xD7A3 ? 9 : 6;
        }
        return px;
    }

    private static String fit(String line) {
        String s = line == null ? "" : line;
        while (textWidth(s) > 90) {
            s = s.substring(0, s.length() - 1);
        }
        return s.strip();
    }

    private static String[] fit(String[] lines) {
        String[] out = new String[Math.min(4, lines.length)];
        for (int k = 0; k < out.length; k++) {
            out[k] = fit(lines[k]);
        }
        return out;
    }

    private static Block sign(String wood, String facing, String color, boolean glow, String... lines) {
        return Blocks.wallSign(wood, facing, color, glow, fit(lines));
    }

    private static Block blade(String wood, String facing, String color, boolean glow, String... lines) {
        return Blocks.wallHangingSign(wood, facing, color, glow, fit(lines));
    }

    private static Block hanging(String wood, int rotation, String color, boolean glow, String... lines) {
        return Blocks.hangingSign(wood, rotation, color, glow, fit(lines));
    }

    private static Block standSign(String wood, String color, String... lines) {
        return Block.of(wood + "_sign[rotation=0,waterlogged=false]", Furniture.rgb(wood)).withText(color, false, fit(lines));
    }

    // ------------------------------------------------------------------ 실내 공통

    /** 가게·층 하나의 바닥 (a0..a1 × b0..b1 중 in 인 칸), 서는 높이 L, 층고 h, 들어오는 칸 (ei, ej) */
    private record Area(int a0, int a1, int b0, int b1, int L, int h, int ei, int ej, BiPredicate<Integer, Integer> in, boolean upper) {
        boolean has(int i, int j) {
            return i >= a0 && i <= a1 && j >= b0 && j <= b1 && in.test(i, j);
        }

        int top() {
            return L + h - 2;
        }
    }

    /** 가구를 놓아도 되는 칸 (들어오는 길과 위층 계단 옆 통로는 비움) */
    private boolean free(Area A, int i, int j) {
        if (!A.has(i, j) || v.get(i, A.L, j) != null) {
            return false;
        }
        if (i == A.ei) {
            return false; // 문에서 안쪽까지 곧은 통로
        }
        if (Math.abs(i - A.ei) == 1 && j == A.ej) {
            return false;
        }
        // 계단실 옆 열은 비움 (위층은 복도, 1층은 계단실 뒤 공간으로 가는 길)
        return !(core && i == hc);
    }

    private boolean put(Area A, int i, int j, Block b) {
        if (free(A, i, j)) {
            v.set(i, A.L, j, b);
            return true;
        }
        return false;
    }

    private boolean freeBox(Area A, int i0b, int j0b, int i1b, int j1b) {
        for (int j = j0b; j <= j1b; j++) {
            for (int i = i0b; i <= i1b; i++) {
                if (!free(A, i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void upperInterior(int k) {
        Tenant t = tenants[k];
        int L = levels[k], h = levels[k + 1] - L;
        Block fl = floorFor(t);
        for (int j = ja; j <= jb; j++) {
            for (int i = ia; i <= ib; i++) {
                if (plate(i, j)) {
                    v.set(i, L - 1, j, fl);
                }
            }
        }
        curFloor = k;
        furnish(t, floorArea(k));
    }

    private Block floorFor(Tenant t) {
        return switch (t.use()) {
            case FOOD -> switch (t.kind()) {
                case "bbq", "gopchang", "skewer", "jokbal", "stew", "mala" -> SMOOTH_STONE;
                case "sanchae", "pajeon", "tofu", "baeksuk" -> SPRUCE_PLANKS;
                case "sushi", "donkatsu", "ramen", "grill", "taco", "kebab", "burger" -> OAK_PLANKS;
                default -> WHITE_TERRACOTTA;
            };
            case CAFE -> t.kind().equals("dabang") ? Block.of("red_concrete_powder", 0x9C2B27) : OAK_PLANKS;
            case BAR -> t.kind().equals("hof") || t.kind().equals("pocha") ? SMOOTH_STONE : DARK_PLANKS;
            case STORE, PHARMACY, LIGHTING -> WHITE_CONCRETE;
            case RETAIL -> t.kind().equals("outdoor") || t.kind().equals("antique") ? SPRUCE_PLANKS : Rooms.MARU_LIGHT;
            case BOOK -> OAK_PLANKS;
            case PRINT, TOOLS, WORKSHOP, LAUNDRY -> LIGHT_GRAY_CONCRETE;
            case MARKET -> SMOOTH_STONE;
            case ACADEMY, OFFICE, BILLIARD -> Rooms.CARPET_TILE;
            case CLINIC -> WHITE_CONCRETE;
            case GOSIWON, ONEROOM, HOME -> r.nextBoolean() ? Rooms.MARU : Rooms.MARU_LIGHT;
            case PC, NORAE -> GRAY_CONCRETE;
            case STUDIO -> switch (t.kind()) {
                case "gym", "golf", "band" -> GRAY_CONCRETE;
                case "salon", "nail", "tattoo", "photo" -> WHITE_CONCRETE;
                default -> OAK_PLANKS;
            };
        };
    }

    private void furnish(Tenant t, Area A) {
        switch (t.use()) {
            case FOOD -> food(t, A);
            case CAFE -> cafe(t, A);
            case BAR -> bar(t, A);
            case STORE -> store(t, A);
            case RETAIL -> retail(t, A);
            case BOOK -> book(A);
            case PRINT -> print(t, A);
            case TOOLS -> tools(t, A);
            case LIGHTING -> lighting(A);
            case LAUNDRY -> laundry(t, A);
            case PHARMACY -> pharmacy(A);
            case MARKET -> market(t, A);
            case ACADEMY -> academy(t, A);
            case CLINIC -> clinic(t, A);
            case GOSIWON -> gosiwon(A);
            case ONEROOM -> oneroom(A);
            case OFFICE -> office(t, A);
            case PC -> pc(A);
            case NORAE -> norae(A);
            case HOME -> home(A);
            case STUDIO -> studio(t, A);
            case WORKSHOP -> workshop(t, A);
            case BILLIARD -> billiard(A);
        }
        ceilingLights(t, A);
    }

    private void ceilingLights(Tenant t, Area A) {
        int top = A.top();
        boolean warm = t.use() == Use.BAR || (t.use() == Use.FOOD && !t.kind().equals("buffet") && !t.kind().equals("gukbap")
                && !t.kind().equals("bunsik") && !t.kind().equals("kalguksu")) || (t.use() == Use.CAFE && density <= 1);
        Block light = warm ? LANTERN_HANGING : Interior.LIGHT;
        int step = warm ? 3 : 4;
        for (int j = A.b0 + 1; j <= A.b1; j += step) {
            for (int i = A.a0 + 1; i <= A.a1; i += step) {
                if (A.has(i, j) && v.get(i, top, j) == null && v.get(i, top - 1, j) == null) {
                    v.set(i, top, j, light);
                }
            }
        }
    }

    private static final Block SMOKER_LIT = Block.of("smoker[facing=south,lit=true]", 0x555451);
    private static final Block CHAIN = Block.of("iron_chain[axis=y,waterlogged=false]", 0x505050);
    private static final Block GRILL = Block.of("iron_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]", 0xC2C1C1);
    private static final Block STONE_TOP = Block.of("smooth_stone_slab[type=top,waterlogged=false]", 0x9E9E9E);
    private static final Block SPRUCE_TOP = Block.of("spruce_slab[type=top,waterlogged=false]", 0x725430);
    private static final Block BIRCH_TOP = Block.of("birch_slab[type=top,waterlogged=false]", 0xC0AF79);
    private static final Block LOW_TABLE = Block.of("dark_oak_slab[type=bottom,waterlogged=false]", 0x432B14);
    private static final Block MIRROR = Block.of("light_blue_stained_glass", 0x6699D8);
    private static final Block CURTAIN = Block.of("light_blue_stained_glass_pane", 0x6699D8);
    private static final Block GLASS_BLOCK = Block.of("glass", 0xC8DCE4);
    private static final Block FENCE = Block.of("dark_oak_fence", 0x432B14);
    private static final Block PLATE = Block.of("dark_oak_pressure_plate[powered=false]", 0x432B14);
    private static final Block WHITE_SHEET = Block.of("white_wool", 0xE9ECEC);
    private static final Block PILLOW = Block.of("white_carpet", 0xE9ECEC);
    private static final Block TOILET = Block.of("quartz_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE);
    private static final Block NOTE = Block.of("note_block[instrument=harp,note=0,powered=false]", 0x58352A);

    private static Block carpet(String color) {
        return Block.of(color + "_carpet", Blocks.wool(color).rgb());
    }

    private static Block candle(String color) {
        return Block.of(color + "_candle[candles=3,lit=false,waterlogged=false]", Blocks.wool(color).rgb());
    }

    // ------------------------------------------------------------------ 먹고 마시는 곳

    /** 식당: 뒷벽 주방, 배식대와 메뉴판, 식탁 (고깃집은 불판과 환기통, 산 아래 식당은 좌식) */
    private void food(Tenant t, Area A) {
        int L = A.L, top = A.top();
        String kind = t.kind();
        boolean seated = kind.equals("sanchae") || kind.equals("pajeon") || kind.equals("tofu") || kind.equals("baeksuk");
        boolean grill = kind.equals("bbq") || kind.equals("gopchang") || kind.equals("skewer");
        for (int i = A.a0; i <= A.a1; i++) {
            if (!free(A, i, A.b0)) {
                continue;
            }
            int m = Math.floorMod(i - A.a0, 4);
            v.set(i, L, A.b0, m == 0 ? SMOKER_LIT : m == 1 ? CAULDRON : m == 2 ? Furniture.COUNTER : Furniture.FRIDGE);
            if (m == 3) {
                v.set(i, L + 1, A.b0, Furniture.FRIDGE);
            } else if (top >= L + 2) {
                v.set(i, top, A.b0, Furniture.COUNTER);
            }
        }
        int pass = A.b0 + 2;
        boolean deep = A.b1 - A.b0 >= 7;
        int mid = (A.a0 + A.a1) / 2, menuI = -1;
        if (deep) {
            for (int i = A.a0; i <= A.a1 - 2; i++) {
                if (free(A, i, pass) && A.has(i, pass - 1)) {
                    v.set(i, L, pass, kind.equals("buffet") && i % 2 == 0 ? CAULDRON : Furniture.COUNTER);
                    if (menuI < 0 || Math.abs(i - mid) < Math.abs(menuI - mid)) {
                        menuI = i;
                    }
                    if (kind.equals("sushi") && free(A, i, pass + 1)) {
                        Furniture.chair(F, i, L, pass + 1, "south", "spruce");
                    }
                }
            }
        }
        if (menuI >= 0 && v.get(menuI, top, pass) == null) {
            String[] menu = t.sub().split("·");
            v.set(menuI, top, pass, hanging("dark_oak", 0, "white", true, "메뉴", menu[0], menu.length > 1 ? menu[1] : ""));
        }
        String wood = switch (kind) {
            case "bbq", "gopchang", "skewer", "jokbal", "stew", "mala" -> "spruce";
            case "taco", "curry", "halal" -> "acacia";
            case "kebab", "grill", "burger" -> "dark_oak";
            case "salad", "pasta", "sushi", "ramen", "donkatsu", "noodle" -> "birch";
            default -> "oak";
        };
        int start = deep ? pass + 3 + (kind.equals("sushi") ? 1 : 0) : A.b0 + 3;
        for (int j = start; j + 1 <= A.b1; j += 3) {
            for (int i = A.a0 + 1; i + 1 <= A.a1; i += 4) {
                if (!freeBox(A, i, j - 1, i + 1, j + 1)) {
                    continue;
                }
                if (seated) {
                    // 좌식: 낮은 상과 방석
                    v.set(i, L, j, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
                    v.set(i + 1, L, j, Block.of("spruce_slab[type=bottom,waterlogged=false]", 0x725430));
                    for (int x = i; x <= i + 1; x++) {
                        v.set(x, L, j - 1, carpet("brown"));
                        v.set(x, L, j + 1, carpet("red"));
                    }
                    continue;
                }
                Furniture.table(F, i, L, j, 2, 1, wood);
                if (grill) {
                    // 불판과 위 환기통
                    v.set(i, L, j, GRILL);
                    if (kind.equals("skewer")) {
                        v.set(i + 1, L, j, GRILL);
                    }
                    v.set(i, top, j, CHAIN);
                }
            }
        }
        if (kind.equals("sashimi")) {
            // 창가 수족관
            for (int i = A.a1 - 2; i <= A.a1; i++) {
                if (free(A, i, A.b1)) {
                    v.set(i, L, A.b1, MIRROR);
                    v.set(i, L + 1, A.b1, MIRROR);
                }
            }
        }
        if (kind.equals("ramen") || kind.equals("noodle") || kind.equals("burger")) {
            // 창가 바 자리
            for (int i = A.a0; i <= A.a1; i++) {
                if (free(A, i, A.b1) && free(A, i, A.b1 - 1)) {
                    v.set(i, L, A.b1, Furniture.DESK_TOP);
                    Furniture.chair(F, i, L, A.b1 - 1, "north", "birch");
                }
            }
        }
    }

    /** 카페: 뒤 조리대와 커피 기계, 계산대와 메뉴판, 탁자 (다방은 소파와 낮은 탁자, 보드카페는 보드판) */
    private void cafe(Tenant t, Area A) {
        int L = A.L, top = A.top();
        String kind = t.kind();
        for (int i = A.a0; i <= A.a1; i++) {
            if (free(A, i, A.b0)) {
                v.set(i, L, A.b0, Furniture.COUNTER);
                if (Math.floorMod(i - A.a0, 3) == 1) {
                    v.set(i, L + 1, A.b0, IRON_BLOCK);
                } else if (kind.equals("comic") || kind.equals("board")) {
                    v.set(i, L, A.b0, Furniture.BOOKSHELF);
                    v.set(i, L + 1, A.b0, Furniture.BOOKSHELF);
                }
            }
        }
        int cj = A.b0 + 2, mid = (A.a0 + A.a1) / 2, menuI = -1;
        if (A.b1 - A.b0 >= 6) {
            for (int i = A.a0; i <= A.a1 - 2; i++) {
                if (free(A, i, cj) && A.has(i, cj - 1)) {
                    boolean show = i == A.a0 + 1 || kind.equals("bakery") && i % 2 == 0;
                    v.set(i, L, cj, show ? GLASS_BLOCK : Furniture.COUNTER);
                    if (menuI < 0 || Math.abs(i - mid) < Math.abs(menuI - mid)) {
                        menuI = i;
                    }
                }
            }
        }
        if (menuI >= 0 && v.get(menuI, top, cj) == null) {
            v.set(menuI, top, cj, kind.equals("dabang") ? hanging("spruce", 0, "white", false, "차림표", "커피 쌍화차", "생강차")
                    : hanging("birch", 0, "black", false, "MENU", "아메리카노", "카페라떼"));
        }
        String wood = kind.equals("board") || kind.equals("comic") ? "dark_oak" : "birch";
        if (kind.equals("dabang")) {
            // 소파 두 줄이 낮은 탁자를 마주 봄
            for (int j = cj + 3; j + 2 <= A.b1; j += 4) {
                for (int i = A.a0 + 1; i + 2 <= A.a1; i += 4) {
                    if (freeBox(A, i, j, i + 2, j + 2)) {
                        Furniture.sofa(F, r, i, L, j, 3, "south");
                        v.fill(i, L, j + 1, i + 2, L, j + 1, LOW_TABLE);
                        Furniture.sofa(F, r, i, L, j + 2, 3, "north");
                    }
                }
            }
        } else {
            for (int j = cj + 3; j + 1 <= A.b1; j += 3) {
                for (int i = A.a0 + 1; i <= A.a1 - 1; i += 3) {
                    if (free(A, i, j) && free(A, i, j - 1) && free(A, i, j + 1)) {
                        v.set(i, L, j, wood.equals("birch") ? BIRCH_TOP : Block.of("dark_oak_slab[type=top,waterlogged=false]", 0x432B14));
                        Furniture.chair(F, i, L, j - 1, "north", wood);
                        Furniture.chair(F, i, L, j + 1, "south", wood);
                        if (kind.equals("board")) {
                            v.set(i, L + 1, j, carpet("green"));
                        }
                    }
                }
            }
        }
        for (int i = A.a0; i <= A.a1; i += 4) {
            if (free(A, i, A.b1)) {
                Furniture.plant(F, r, i, L, A.b1);
            }
        }
    }

    /** 술집: 호프·포차는 식탁과 맥주 냉장고, 바·펍·와인바는 긴 바와 술장, 높은 탁자 */
    private void bar(Tenant t, Area A) {
        int L = A.L;
        String kind = t.kind();
        if (kind.equals("hof") || kind.equals("pocha") || kind.equals("izakaya")) {
            for (int i = A.a0; i <= A.a1; i++) {
                if (free(A, i, A.b0)) {
                    int m = Math.floorMod(i - A.a0, 3);
                    v.set(i, L, A.b0, m == 0 ? Furniture.FRIDGE : m == 1 ? SMOKER_LIT : Furniture.COUNTER);
                    if (m == 0) {
                        v.set(i, L + 1, A.b0, MIRROR);
                    }
                }
            }
            String wood = kind.equals("pocha") ? "acacia" : kind.equals("izakaya") ? "dark_oak" : "spruce";
            for (int j = A.b0 + 3; j + 1 <= A.b1; j += 3) {
                for (int i = A.a0 + 1; i + 1 <= A.a1; i += 4) {
                    if (freeBox(A, i, j - 1, i + 1, j + 1)) {
                        Furniture.table(F, i, L, j, 2, 1, wood);
                    }
                }
            }
            return;
        }
        int back = A.a1, ci = A.a1 - 2;
        Block counter = kind.equals("wine") ? Block.of("stripped_dark_oak_wood[axis=y]", 0x4B3A28) : DARK_PLANKS;
        for (int j = A.b0 + 1; j <= A.b1 - 2; j++) {
            if (!free(A, ci, j) || !A.has(back, j)) {
                continue;
            }
            v.set(ci, L, j, counter);
            if (j % 3 == 0) {
                v.set(ci, L + 1, j, Block.of("lever[face=floor,facing=north,powered=false]", 0x6B6B6B));
            }
            if (free(A, ci - 1, j)) {
                Furniture.chair(F, ci - 1, L, j, "west", "spruce");
            }
            if (free(A, back, j)) {
                v.set(back, L, j, BARREL);
                v.set(back, L + 1, j, j % 2 == 0 ? GLASS_BLOCK : DARK_PLANKS);
            }
        }
        for (int j = A.b0 + 2; j <= A.b1 - 1; j += 3) {
            for (int i = A.a0 + 1; i <= ci - 3; i += 3) {
                if (free(A, i, j) && free(A, i, j + 1)) {
                    if (kind.equals("lounge")) {
                        v.set(i, L, j, LOW_TABLE);
                        Furniture.chair(F, i, L, j + 1, "south", "mangrove");
                    } else {
                        v.set(i, L, j, FENCE);
                        v.set(i, L + 1, j, PLATE);
                        Furniture.chair(F, i, L, j + 1, "south", "dark_oak");
                    }
                }
            }
        }
        if (kind.equals("lp")) {
            // LP 장과 턴테이블
            for (int i = A.a0; i <= ci - 2; i++) {
                if (free(A, i, A.b0)) {
                    v.set(i, L, A.b0, Furniture.BOOKSHELF);
                    v.set(i, L + 1, A.b0, Furniture.BOOKSHELF);
                }
            }
            put(A, A.a0 + 1, A.b0 + 1, NOTE);
        }
        if (kind.equals("pub")) {
            // 다트 기계 (뒷벽 앞)
            if (free(A, A.a0, A.b0)) {
                v.set(A.a0, L, A.b0, BLACK_CONCRETE);
                v.set(A.a0, L + 1, A.b0, Block.of("target[power=0]", 0xE5D8C4));
            }
        }
    }

    // ------------------------------------------------------------------ 파는 곳

    /** 편의점·슈퍼: 뒷벽 냉장고, 진열대 줄, 문 옆 계산대 */
    private void store(Tenant t, Area A) {
        int L = A.L;
        for (int i = A.a0; i <= A.a1; i++) {
            if (free(A, i, A.b0)) {
                v.set(i, L, A.b0, Furniture.FRIDGE);
                v.set(i, L + 1, A.b0, MIRROR);
            }
        }
        for (int i = A.a0 + 1; i <= A.a1 - 1; i += 3) {
            for (int j = A.b0 + 3; j <= A.b1 - 3; j++) {
                if (free(A, i, j)) {
                    v.set(i, L, j, SMOOTH_QUARTZ);
                    v.set(i, L + 1, j, (j & 1) == 0 ? BARREL : Furniture.BOOKSHELF);
                }
            }
        }
        int ci = Math.min(A.a1, A.ei + 2);
        for (int j = A.b1 - 2; j <= A.b1; j++) {
            put(A, ci, j, Furniture.COUNTER);
        }
        if (free(A, ci + 1, A.b1 - 1)) {
            v.set(ci + 1, L, A.b1 - 1, Furniture.BOOKSHELF);
            v.set(ci + 1, L + 1, A.b1 - 1, Furniture.BOOKSHELF);
        }
        if (!A.upper) {
            if (t.kind().equals("super")) {
                Block[] fruit = {MELON, PUMPKIN, HAY, BARREL};
                for (int i = curSa; i <= curSb; i++) {
                    if (Math.abs(i - A.ei) > 1 && v.get(i, 0, d - 1) == null) {
                        v.set(i, 0, d - 1, fruit[r.nextInt(fruit.length)]);
                    }
                }
            } else if (curSb - A.ei >= 3 && v.get(curSb, 0, d - 1) == null) {
                // 가게 앞 파라솔 탁자
                v.set(curSb, 0, d - 1, FENCE);
                v.set(curSb, 1, d - 1, carpet("green"));
            }
        }
    }

    /** 옷·화장품·신발·안경·등산복·소품·양복점·꽃집 */
    private void retail(Tenant t, Area A) {
        int L = A.L, top = A.top();
        String k = t.kind();
        String[] colors = switch (k) {
            case "outdoor" -> new String[]{"red", "orange", "blue", "green", "yellow", "black", "light_blue"};
            case "tailor" -> new String[]{"gray", "black", "blue", "light_gray", "brown", "white"};
            default -> new String[]{"white", "black", "light_blue", "pink", "gray", "yellow", "red", "green", "brown"};
        };
        switch (k) {
            case "cosmetic", "optical", "goods", "souvenir", "records", "antique" -> {
                // 벽 진열장과 가운데 진열대
                for (int j = A.b0; j <= A.b1 - 2; j++) {
                    for (int i : new int[]{A.a0, A.a1}) {
                        if (free(A, i, j)) {
                            Block shelf = k.equals("records") || k.equals("antique") ? Furniture.BOOKSHELF : SMOOTH_QUARTZ;
                            v.set(i, L, j, shelf);
                            v.set(i, L + 1, j, shelf);
                            if (k.equals("cosmetic")) {
                                v.set(i, L + 2, j, candle(colors[r.nextInt(colors.length)]));
                            }
                        }
                    }
                }
                for (int i = A.a0 + 2; i <= A.a1 - 2; i += 3) {
                    for (int j = A.b0 + 2; j <= A.b1 - 3; j++) {
                        if (free(A, i, j)) {
                            v.set(i, L, j, k.equals("optical") ? GLASS_BLOCK : SMOOTH_QUARTZ);
                            Block item = switch (k) {
                                case "cosmetic" -> candle(colors[r.nextInt(colors.length)]);
                                case "antique" -> r.nextBoolean() ? LANTERN : Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)];
                                case "optical" -> null;
                                default -> r.nextBoolean() ? carpet(colors[r.nextInt(colors.length)]) : Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)];
                            };
                            if (item != null) {
                                v.set(i, L + 1, j, item);
                            }
                        }
                    }
                }
                if (k.equals("optical") && free(A, A.a0 + 1, A.b0 + 1)) {
                    Furniture.chair(F, A.a0 + 1, L, A.b0 + 1, "south", "birch");
                }
            }
            case "flower" -> {
                // 꽃 진열대 줄 (사이 통로), 뒷벽 꽃 냉장고
                for (int j = A.b0; j <= A.b1 - 1; j += 2) {
                    for (int i = A.a0; i <= A.a1; i++) {
                        if (Math.floorMod(i - A.a0, 4) != 3 && free(A, i, j)) {
                            v.set(i, L, j, j == A.b0 ? MIRROR : (i + j) % 3 == 0 ? Block.of("flowering_azalea", 0x63753A)
                                    : Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
                        }
                    }
                }
                if (!A.upper) {
                    for (int i = curSa; i <= curSb; i++) {
                        if (Math.abs(i - A.ei) > 1 && v.get(i, 0, d - 1) == null) {
                            v.set(i, 0, d - 1, Furniture.PLANTS[r.nextInt(Furniture.PLANTS.length)]);
                        }
                    }
                }
            }
            case "shoes" -> {
                for (int j = A.b0; j <= A.b1 - 2; j++) {
                    for (int i : new int[]{A.a0, A.a1}) {
                        if (free(A, i, j)) {
                            v.set(i, L, j, SMOOTH_QUARTZ);
                            v.set(i, L + 1, j, carpet(colors[r.nextInt(colors.length)]));
                        }
                    }
                }
                for (int j = A.b0 + 2; j <= A.b1 - 3; j += 3) {
                    for (int i = A.a0 + 2; i <= A.a1 - 2; i += 3) {
                        if (free(A, i, j)) {
                            Furniture.chair(F, i, L, j, "north", "birch");
                        }
                    }
                }
            }
            default -> {
                // 옷: 양옆 벽 옷걸이(사슬에 걸린 옷), 가운데 진열대, 거울
                boolean tailor = k.equals("tailor");
                for (int j = A.b0; j <= A.b1 - 2; j++) {
                    for (int i : new int[]{A.a0, A.a1}) {
                        if (free(A, i, j)) {
                            if (tailor) {
                                v.set(i, L, j, Furniture.BOOKSHELF);
                                v.set(i, L + 1, j, Blocks.wool(colors[r.nextInt(colors.length)]));
                            } else {
                                v.set(i, L + 1, j, Blocks.wool(colors[r.nextInt(colors.length)]));
                                for (int y = L + 2; y <= top; y++) {
                                    v.set(i, y, j, CHAIN);
                                }
                            }
                        }
                    }
                }
                int mi = (A.a0 + A.a1) / 2 + 1;
                for (int j = A.b0 + 2; j <= A.b1 - 3; j++) {
                    if (free(A, mi, j)) {
                        v.set(mi, L, j, tailor ? OAK_PLANKS : SPRUCE_TOP);
                        if (!tailor) {
                            v.set(mi, L + 1, j, carpet(colors[r.nextInt(colors.length)]));
                        }
                    }
                }
                if (free(A, A.a0 + 1, A.b0)) {
                    v.set(A.a0 + 1, L, A.b0, MIRROR);
                    v.set(A.a0 + 1, L + 1, A.b0, MIRROR);
                }
                if (k.equals("outdoor") && !A.upper) {
                    // 가게 앞 옷걸이와 배낭
                    for (int i = curSa; i <= curSb; i += 2) {
                        if (Math.abs(i - A.ei) > 1 && v.get(i, 0, d - 1) == null) {
                            v.set(i, 0, d - 1, Block.of("spruce_fence", 0x725430));
                            v.set(i, 1, d - 1, Blocks.wool(colors[r.nextInt(colors.length)]));
                        }
                    }
                }
            }
        }
        // 계산대 (문 옆)
        int ci = Math.min(A.a1 - 1, A.ei + 2);
        put(A, ci, A.b1 - 2, Furniture.COUNTER);
        put(A, ci + 1, A.b1 - 2, Furniture.COUNTER);
    }

    /** 서점: 책장 줄, 계산대, 읽는 탁자 */
    private void book(Area A) {
        int L = A.L;
        for (int i = A.a0; i <= A.a1; i += 3) {
            for (int j = A.b0; j <= A.b1 - 3; j++) {
                if (Math.floorMod(j - A.b0, 4) != 2 && free(A, i, j)) {
                    v.set(i, L, j, Furniture.BOOKSHELF);
                    v.set(i, L + 1, j, Furniture.BOOKSHELF);
                }
            }
        }
        put(A, Math.min(A.a1, A.ei + 2), A.b1 - 1, Furniture.COUNTER);
        put(A, Math.min(A.a1, A.ei + 3), A.b1 - 1, Furniture.COUNTER);
    }

    /** 복사집·인쇄소·지업사: 복사기·인쇄기, 종이 더미, 재단기, 계산대 */
    private void print(Tenant t, Area A) {
        int L = A.L;
        String k = t.kind();
        if (k.equals("copy")) {
            for (int j = A.b0; j <= A.b1 - 3; j += 2) {
                if (free(A, A.a1, j)) {
                    v.set(A.a1, L, j, IRON_BLOCK);
                    v.set(A.a1, L + 1, j, carpet("light_gray"));
                }
            }
            for (int i = A.a0; i <= A.a1 - 1; i++) {
                if (free(A, i, A.b0)) {
                    v.set(i, L, A.b0, WHITE_SHEET);
                    v.set(i, L + 1, A.b0, (i & 1) == 0 ? WHITE_SHEET : Block.of("light_gray_wool", 0x8E8E86));
                }
            }
            for (int i = A.a0; i <= A.a1 - 2; i++) {
                put(A, i, A.b1 - 2, Furniture.COUNTER);
            }
            put(A, A.a0, A.b0 + 2, Block.of("stonecutter[facing=south]", 0x7B7774));
            put(A, A.a0 + 1, A.b0 + 2, Block.of("loom[facing=south]", 0x8B6C47));
            return;
        }
        // 인쇄기 (회색·파랑 덩어리) 와 종이 더미
        for (int j = A.b0 + 1; j + 1 <= A.b1 - 2; j += 4) {
            for (int i = A.a0 + 1; i + 2 <= A.a1 - 1; i += 5) {
                if (k.equals("offset") && freeBox(A, i, j, i + 2, j + 1)) {
                    v.fill(i, L, j, i + 2, L, j + 1, (i / 5) % 2 == 0 ? GRAY_CONCRETE : BLUE_CONCRETE);
                    v.fill(i, L + 1, j, i + 1, L + 1, j, IRON_BLOCK);
                } else if (freeBox(A, i, j, i + 1, j + 1)) {
                    v.fill(i, L, j, i + 1, L + 1, j + 1, (j & 1) == 0 ? WHITE_SHEET : Block.of("light_gray_wool", 0x8E8E86));
                }
            }
        }
        put(A, A.a1, A.b1 - 2, Block.of("stonecutter[facing=south]", 0x7B7774));
        for (int i = A.a0; i <= A.a0 + 2; i++) {
            put(A, i, A.b1 - 1, Furniture.COUNTER);
        }
    }

    /** 공구·철물·타일 가게: 빽빽한 선반, 가게 앞에 내놓은 물건 */
    private void tools(Tenant t, Area A) {
        int L = A.L;
        boolean tile = t.kind().equals("tile");
        Block[] goods = tile ? new Block[]{WHITE_TERRACOTTA, LIGHT_GRAY_TERRACOTTA, QUARTZ_BRICKS, SMOOTH_STONE}
                : new Block[]{BARREL, IRON_BARS, CHAIN, Block.of("grindstone[face=floor,facing=south]", 0x8E8E8E), CAULDRON};
        for (int i = A.a0; i <= A.a1; i += 2) {
            for (int j = A.b0; j <= A.b1 - 3; j++) {
                if ((i - A.a0) % 4 == 2 && j > A.b0 || Math.floorMod(j - A.b0, 4) == 2) {
                    continue;
                }
                if (free(A, i, j)) {
                    v.set(i, L, j, tile ? goods[r.nextInt(goods.length)] : Furniture.BOOKSHELF);
                    v.set(i, L + 1, j, goods[r.nextInt(goods.length)]);
                }
            }
        }
        put(A, Math.min(A.a1, A.ei + 2), A.b1 - 1, Furniture.COUNTER);
        if (!A.upper) {
            for (int i = curSa; i <= curSb; i++) {
                if (Math.abs(i - A.ei) > 1 && v.get(i, 0, d - 1) == null && r.nextInt(3) > 0) {
                    v.set(i, 0, d - 1, tile ? goods[r.nextInt(goods.length)] : r.nextBoolean() ? BARREL : CAULDRON);
                }
            }
        }
    }

    /** 조명 가게: 천장 가득 매단 등, 진열대 위 스탠드 */
    private void lighting(Area A) {
        int L = A.L, top = A.top();
        for (int j = A.b0; j <= A.b1; j++) {
            for (int i = A.a0; i <= A.a1; i++) {
                if (!A.has(i, j) || v.get(i, top, j) != null || (i + j) % 2 != 0) {
                    continue;
                }
                int x = Math.floorMod(i * 7 + j * 3, 4);
                v.set(i, top, j, x == 0 ? Interior.LIGHT : x == 1 ? Block.of("end_rod[facing=down]", 0xE6E1D9)
                        : x == 2 ? LANTERN_HANGING : SEA_LANTERN);
            }
        }
        for (int i = A.a0; i <= A.a1; i++) {
            if (free(A, i, A.b0)) {
                v.set(i, L, A.b0, SMOOTH_QUARTZ);
                v.set(i, L + 1, A.b0, (i & 1) == 0 ? Block.of("pearlescent_froglight[axis=y]", 0xEDDBDC) : LANTERN);
            }
        }
        for (int i = A.a0 + 2; i <= A.a1 - 2; i += 3) {
            for (int j = A.b0 + 2; j <= A.b1 - 3; j++) {
                if (free(A, i, j)) {
                    v.set(i, L, j, SPRUCE_TOP);
                    v.set(i, L + 1, j, (j & 1) == 0 ? LANTERN : Block.of("end_rod[facing=up]", 0xE6E1D9));
                }
            }
        }
        put(A, Math.min(A.a1, A.ei + 2), A.b1 - 1, Furniture.COUNTER);
    }

    /** 세탁소·빨래방·수선집: 계산대, 걸린 옷, 세탁기 */
    private void laundry(Tenant t, Area A) {
        int L = A.L, top = A.top();
        boolean coin = t.kind().equals("coin");
        if (coin) {
            for (int j = A.b0; j <= A.b1 - 1; j++) {
                for (int i : new int[]{A.a0, A.a1}) {
                    if (free(A, i, j)) {
                        v.set(i, L, j, IRON_BLOCK);
                        v.set(i, L + 1, j, (j & 1) == 0 ? IRON_BLOCK : GLASS_BLOCK);
                    }
                }
            }
            for (int j = A.b0 + 2; j <= A.b1 - 2; j += 3) {
                put(A, (A.a0 + A.a1) / 2, j, STONE_TOP);
            }
            return;
        }
        for (int i = A.a0; i <= A.a1 - 2; i++) {
            put(A, i, A.b1 - 2, Furniture.COUNTER);
        }
        // 비닐 씌운 옷이 걸린 레일
        for (int j = A.b0; j <= A.b1 - 4; j += 2) {
            for (int i = A.a0; i <= A.a1; i++) {
                if (free(A, i, j)) {
                    v.set(i, L + 1, j, (i + j) % 3 == 0 ? Blocks.wool(r.nextBoolean() ? "gray" : "blue") : Block.of("white_stained_glass", 0xF0F0F0));
                    for (int y = L + 2; y <= top; y++) {
                        v.set(i, y, j, CHAIN);
                    }
                }
            }
        }
        put(A, A.a1, A.b1 - 3, t.kind().equals("tailor") ? Block.of("loom[facing=west]", 0x8B6C47) : IRON_BLOCK);
    }

    /** 약국: 긴 계산대와 뒤 약장, 벽 진열장 */
    private void pharmacy(Area A) {
        int L = A.L;
        int cj = Math.max(A.b0 + 2, A.b1 - 4);
        for (int i = A.a0; i <= A.a1 - 1; i++) {
            if (free(A, i, cj)) {
                v.set(i, L, cj, Furniture.COUNTER);
            }
            if (free(A, i, A.b0)) {
                v.set(i, L, A.b0, WHITE_CONCRETE);
                v.set(i, L + 1, A.b0, WHITE_CONCRETE);
                v.set(i, L + 2, A.b0, candle((i & 1) == 0 ? "white" : "light_blue"));
            }
        }
        for (int j = cj + 1; j <= A.b1 - 1; j++) {
            if (free(A, A.a0, j)) {
                v.set(A.a0, L, j, SMOOTH_QUARTZ);
                v.set(A.a0, L + 1, j, candle((j & 1) == 0 ? "green" : "white"));
            }
        }
    }

    /** 시장 가게: 앞이 트인 진열대에 물건, 길에도 내놓음, 뒤 냉장고와 주인 자리 */
    private void market(Tenant t, Area A) {
        int L = A.L;
        String k = t.kind();
        Block[] goods = switch (k) {
            case "banchan" -> new Block[]{carpet("red"), carpet("green"), carpet("brown"), carpet("orange")};
            case "tteok" -> new Block[]{carpet("white"), carpet("pink"), carpet("lime"), carpet("light_gray")};
            case "fruit" -> new Block[]{MELON, PUMPKIN, HAY, Block.of("red_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=true]", 0xC52D2A)};
            case "meat" -> new Block[]{Block.of("pink_terracotta", 0xA14E4E), Block.of("red_terracotta", 0x8F3D2E)};
            case "fish" -> new Block[]{Block.of("packed_ice", 0x8DB4FA), carpet("light_gray")};
            case "dried" -> new Block[]{DRIED_KELP, carpet("brown")};
            case "mill", "rice" -> new Block[]{WHITE_SHEET, HAY, Block.of("red_wool", 0xA12722)};
            default -> new Block[]{HAY, Block.of("orange_terracotta", 0xA15325)};
        };
        boolean solid = k.equals("fruit") || k.equals("mill") || k.equals("rice") || k.equals("meat") || k.equals("fish") || k.equals("dried");
        // 앞 진열대 (안쪽 앞줄)와 가운데 진열대
        for (int j : new int[]{A.b1, A.b1 - 3}) {
            for (int i = A.a0; i <= A.a1; i++) {
                if (free(A, i, j)) {
                    if (solid) {
                        v.set(i, L, j, k.equals("meat") || k.equals("fish") ? GLASS_BLOCK : SPRUCE_TOP);
                        v.set(i, L + 1, j, goods[r.nextInt(goods.length)]);
                    } else {
                        v.set(i, L, j, STONE_TOP);
                        v.set(i, L + 1, j, goods[r.nextInt(goods.length)]);
                    }
                }
            }
        }
        // 뒤: 냉장고·기계, 주인 의자
        for (int i = A.a0; i <= A.a1; i++) {
            if (free(A, i, A.b0)) {
                v.set(i, L, A.b0, k.equals("mill") ? (i & 1) == 0 ? IRON_BLOCK : Block.of("grindstone[face=floor,facing=south]", 0x8E8E8E)
                        : k.equals("chicken") ? CAULDRON : (i & 1) == 0 ? Furniture.FRIDGE : BARREL);
            }
        }
        if (k.equals("meat")) {
            for (int i = A.a0 + 1; i <= A.a1 - 1; i += 2) {
                if (A.has(i, A.b0 + 1) && v.get(i, L + 1, A.b0 + 1) == null) {
                    v.set(i, A.top(), A.b0 + 1, CHAIN);
                }
            }
        }
        put(A, A.a0, A.b1 - 4, Block.of("oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", 0xA2834F));
        if (!A.upper) {
            // 길에 내놓은 좌판
            for (int i = curSa - 1; i <= curSb + 1; i++) {
                if (Math.abs(i - A.ei) > 1 && v.get(i, 0, d - 1) == null) {
                    v.set(i, 0, d - 1, SPRUCE_TOP);
                    v.set(i, 1, d - 1, goods[r.nextInt(goods.length)]);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 방 나누기

    /** 방 하나 (안쪽 s..e × r0..r1). south 면 복도가 북쪽(r0-1 벽에 문), 아니면 남쪽(r1+1 벽). door 는 문 열 */
    private record Room(int s, int e, int r0, int r1, boolean south, int door) {
        int near() {
            return south ? r0 : r1;
        }

        int far() {
            return south ? r1 : r0;
        }

        int dir() {
            return south ? 1 : -1;
        }

        int other() {
            return door == s ? e : s;
        }

        /** 방 안에서 등받이가 문 쪽을 보는 의자 방향 */
        String back() {
            return south ? "north" : "south";
        }
    }

    /**
     * 통로 열 sc 를 따라 뒤로 가며 가로 복도(행 c)를 내고, 복도 양쪽에 방(안쪽 너비 rw, 깊이 rd)을 붙입니다.
     * 방은 roomFront 행까지 쓰고 그 앞 줄에 벽을 쌓습니다 (그 앞은 대기실·카운터 같은 앞 공간).
     * 방마다 fit 을 부르고, 놓은 방 수를 돌려줍니다.
     */
    private int roomGrid(Area A, int sc, int roomFront, int rw, int rd, String doorWood, java.util.function.Consumer<Room> fit) {
        return roomGrid(A, sc, roomFront, rw, rd, 2, 2, doorWood, fit);
    }

    /** 방 크기 하한 (짧은 변 ≥ lo, 긴 변 ≥ hi; 방향은 상관없음) 을 둔 방 나누기. dry 면 세기만 */
    private int roomGrid(Area A, int sc, int roomFront, int rw, int rd, int lo, int hi, String doorWood, java.util.function.Consumer<Room> fit) {
        int count = 0;
        int c = Math.max(A.b0, roomFront - rd - 1);
        int southEnd = roomFront;
        while (c >= A.b0) {
            int cn = c - 2 * rd - 4;
            int northStart = cn < A.b0 ? A.b0 : c - 1 - rd;
            count += roomsAlong(A, sc, c, c + 2, southEnd, true, rw, lo, hi, doorWood, fit);
            if (c - 2 >= northStart) {
                count += roomsAlong(A, sc, c, northStart, c - 2, false, rw, lo, hi, doorWood, fit);
            }
            // 복도 등
            for (int i = A.a0; i <= A.a1 && !dry; i += 3) {
                if (A.has(i, c) && v.get(i, A.top(), c) == null) {
                    v.set(i, A.top(), c, Interior.LIGHT);
                }
            }
            c = cn;
            southEnd = cn + 1 + rd;
        }
        return count;
    }

    /** 세기만 하는 중 (층 쓰임을 정할 때) */
    private boolean dry;

    private int roomsAlong(Area A, int sc, int c, int r0, int r1, boolean south, int rw, int lo, int hi, String doorWood,
                           java.util.function.Consumer<Room> fit) {
        if (r1 - r0 + 1 < 2) {
            return 0;
        }
        int n = 0, depth = r1 - r0 + 1;
        // 1층은 통로 한쪽에만 (계단실 뒤 공간을 막지 않게)
        for (int side : A.upper ? new int[]{1, -1} : new int[]{1}) {
            int near = sc + 2 * side, far = side > 0 ? A.a1 : A.a0;
            int x = near;
            while (side > 0 ? x <= far - 1 : x >= far + 1) {
                int x2 = x + side * (rw - 1);
                int rest = side > 0 ? far - x2 : x2 - far;
                // 남는 칸으로 다음 방이 안 나오면 이 방에 붙임
                if (rest - 1 < Math.max(2, Math.min(rw, lo))) {
                    x2 = far;
                }
                int s = Math.min(x, x2), e = Math.max(x, x2);
                if (side > 0 ? x2 > far : x2 < far) {
                    break;
                }
                int W = e - s + 1;
                boolean big = Math.min(W, depth) >= lo && Math.max(W, depth) >= hi;
                if (big && roomFits(A, s, e, r0, r1, c, south)) {
                    if (!dry) {
                        int door = side > 0 ? s : e;
                        roomWalls(A, s, e, r0, r1, c);
                        int wj = south ? r0 - 1 : r1 + 1;
                        v.set(door, A.L, wj, Blocks.door(doorWood, south ? "north" : "south", false));
                        v.set(door, A.L + 1, wj, Blocks.door(doorWood, south ? "north" : "south", true));
                        fit.accept(new Room(s, e, r0, r1, south, door));
                    }
                    n++;
                }
                x = x2 + 2 * side;
            }
        }
        return n;
    }

    private boolean roomFits(Area A, int s, int e, int r0, int r1, int c, boolean south) {
        if (e - s + 1 < 2) {
            return false;
        }
        for (int j = r0; j <= r1; j++) {
            for (int i = s; i <= e; i++) {
                if (!A.has(i, j) || v.get(i, A.L, j) != null) {
                    return false;
                }
            }
        }
        int door = A.has(s, c) ? s : e;
        return A.has(door, c) && A.has(door, south ? r0 - 1 : r1 + 1);
    }

    private void roomWalls(Area A, int s, int e, int r0, int r1, int c) {
        int top = A.top();
        for (int j = r0 - 1; j <= r1 + 1; j++) {
            for (int i = s - 1; i <= e + 1; i++) {
                boolean ring = i == s - 1 || i == e + 1 || j == r0 - 1 || j == r1 + 1;
                if (ring && j != c && A.has(i, j)) {
                    v.fill(i, A.L, j, i, top, j, Interior.INNER_WALL);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 위층 쓰임

    /** 학원: 앞에 안내 데스크, 뒤로 교실 (칠판을 보는 책상 줄). 독서실·스터디카페는 칸막이 책상 */
    private void academy(Tenant t, Area A) {
        int L = A.L;
        if (t.kind().equals("study")) {
            for (int j = A.b0 + 1; j <= A.b1 - 2; j += 2) {
                for (int i = A.a0; i <= A.a1; i++) {
                    if (free(A, i, j) && free(A, i, j - 1) && Math.floorMod(i - A.a0, 5) != 4) {
                        v.set(i, L, j, Furniture.DESK_TOP);
                        v.set(i, L + 1, j, Block.of("white_stained_glass_pane", 0xF0F0F0));
                        Furniture.chair(F, i, L, j - 1, "north", "dark_oak");
                    }
                }
            }
            return;
        }
        int n = roomGrid(A, A.ei, A.b1 - 3, 6, 5, "oak", room -> classroom(A, room));
        if (n == 0) {
            for (int j = A.b0 + 1; j <= A.b1 - 2; j += 2) {
                for (int i = A.a0 + 1; i <= A.a1 - 1; i++) {
                    if (free(A, i, j) && free(A, i, j + 1)) {
                        v.set(i, L, j, Furniture.DESK_TOP);
                        Furniture.chair(F, i, L, j + 1, "south", "oak");
                    }
                }
            }
        }
        // 안내 데스크와 상담 탁자
        put(A, A.ei + 2, A.b1 - 1, Furniture.COUNTER);
        put(A, A.ei + 3, A.b1 - 1, Furniture.COUNTER);
        if (free(A, A.a1 - 1, A.b1 - 1) && free(A, A.a1 - 1, A.b1)) {
            v.set(A.a1 - 1, L, A.b1 - 1, BIRCH_TOP);
            Furniture.chair(F, A.a1 - 1, L, A.b1, "south", "birch");
        }
    }

    private void classroom(Area A, Room R) {
        int L = A.L, top = A.top(), far = R.far(), dir = R.dir();
        for (int i = R.s; i <= R.e; i++) {
            v.set(i, L, far, STONE_TOP);
            v.set(i, L + 1, far, GREEN_CONCRETE);
            if (top > L + 1) {
                v.set(i, L + 2, far, GREEN_CONCRETE);
            }
        }
        int mid = (R.s + R.e) / 2;
        if (mid != R.door) {
            v.set(mid, L, far - dir, Furniture.DESK_TOP);
        }
        for (int j = far - 2 * dir; R.south ? j > R.near() : j < R.near(); j -= 2 * dir) {
            for (int i = R.s; i <= R.e; i++) {
                if (i == R.door) {
                    continue;
                }
                v.set(i, L, j, Furniture.DESK_TOP);
                Furniture.chair(F, i, L, j - dir, R.back(), "oak");
            }
        }
        v.set(mid, top, (R.r0 + R.r1) / 2, Interior.LIGHT);
    }

    /** 병원: 앞에 접수대와 대기 의자, 뒤로 진료실 (치과는 진료 의자, 정형외과·한의원은 치료 침대 칸) */
    private void clinic(Tenant t, Area A) {
        int L = A.L;
        String k = t.kind();
        int rw = k.equals("therapy") || k.equals("dental") ? 3 : 4;
        roomGrid(A, A.ei, A.b1 - 4, rw, 3, "pale_oak", room -> exam(A, room, k));
        // 접수대
        int rj = A.b1 - 2;
        for (int i = A.ei + 2; i <= Math.min(A.a1, A.ei + 4); i++) {
            put(A, i, rj, Furniture.COUNTER);
        }
        // 대기 의자 (창가)
        for (int i = A.ei + 2; i <= A.a1; i++) {
            if (free(A, i, A.b1)) {
                Furniture.chair(F, i, L, A.b1, "south", "birch");
            }
        }
        if (free(A, A.a1, A.b1 - 2)) {
            v.set(A.a1, L, A.b1 - 2, IRON_BLOCK);
            v.set(A.a1, L + 1, A.b1 - 2, MIRROR);
        }
        if (free(A, A.a0, A.b1)) {
            Furniture.plant(F, r, A.a0, L, A.b1);
        }
    }

    private void exam(Area A, Room R, String kind) {
        int L = A.L, top = A.top(), far = R.far(), dir = R.dir(), bi = R.other();
        if (kind.equals("dental")) {
            v.set(bi, L, far, Block.of("quartz_stairs[facing=" + (R.south ? "south" : "north") + ",half=bottom,shape=straight,waterlogged=false]", 0xEBE5DE));
            v.set(bi, L, far - dir, WHITE_SHEET);
            v.set(bi, top, far, Block.of("end_rod[facing=down]", 0xE6E1D9));
        } else {
            v.set(bi, L, far, WHITE_SHEET);
            v.set(bi, L, far - dir, WHITE_SHEET);
            v.set(bi, L + 1, far, PILLOW);
            if (kind.equals("therapy")) {
                v.set(bi, L, R.near(), Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
            }
        }
        int di = R.door;
        if (di != bi && R.r1 - R.r0 >= 2) {
            v.set(di, L, far, Furniture.DESK_TOP);
            v.set(di, L + 1, far, Block.of("iron_trapdoor[facing=" + (R.south ? "north" : "south") + ",half=bottom,open=true,powered=false,waterlogged=false]", 0xC2C1C1));
            Furniture.chair(F, di, L, far - dir, R.back(), "birch");
        }
        if (kind.equals("skin")) {
            v.set(bi, L, R.near(), IRON_BLOCK);
            v.set(bi, L + 1, R.near(), Block.of("end_rod[facing=up]", 0xE6E1D9));
        } else if (!kind.equals("therapy")) {
            v.set(bi, L, R.near(), CAULDRON);
        }
        v.set((R.s + R.e) / 2, top, (R.r0 + R.r1) / 2, Interior.LIGHT);
    }

    // ------------------------------------------------------------------ 살림집 (실거주 크기)

    /** 집 한 칸 (검사용): 종류("투룸", "원룸", "고시원"), 층, 안쪽 크기(가로×세로 칸, 벽·계단실·공용 복도 뺌), 방들 */
    record Unit(String type, int floor, int w, int d, List<Rect> rooms) {
    }

    /** 방 하나의 안쪽 (벽 빼고, 기준 좌표) */
    record Rect(String name, int i0, int j0, int i1, int j1) {
        int w() {
            return i1 - i0 + 1;
        }

        int d() {
            return j1 - j0 + 1;
        }

        /** 짧은 변 ≥ min(a, b), 긴 변 ≥ max(a, b) (방향 상관없음) */
        boolean atLeast(int a, int b) {
            return Math.min(w(), d()) >= Math.min(a, b) && Math.max(w(), d()) >= Math.max(a, b);
        }
    }

    /** 이 건물에 들어간 집들 (검사용) */
    final List<Unit> units = new ArrayList<>();

    /** 지금 그리는 층 */
    private int curFloor;

    /** 계단실 뒤 날개 (안쪽 ia..cx1-1 × ja..jsb-1) 너비·깊이 */
    private int wingW() {
        return cx1 - ia;
    }

    private int wingD() {
        return jsb - ja;
    }

    /**
     * 투룸 한 층 (안 되면 null). 계단참 문 → 현관(2×2) → 계단실 옆 앞쪽에 주방·거실, 뒤쪽에 욕실과 침실,
     * 계단실 뒤 날개는 침실 (날개가 작으면 뒤쪽을 침실 둘로 나눔).
     */
    private List<Rect> twoRoomPlan() {
        int W = ib - hc + 1, D = jb - ja + 1;
        if (!core || elevator || W < 8 || D < 10) {
            return null;
        }
        boolean wing = wingW() >= 3 && wingD() >= 4;
        if (!wing && W < 12) {
            return null;
        }
        int back1 = ja + 3, f0 = ja + 5;
        boolean galley = jb - f0 + 1 >= 6;
        List<Rect> p = new ArrayList<>();
        p.add(new Rect("현관", hc, jb - 1, hc + 1, jb));
        p.add(galley ? new Rect("주방", hc, f0, hc + 1, f0 + 3) : new Rect("주방", ib - 1, f0, ib, f0 + 3));
        p.add(new Rect("거실", hc + 2, f0, galley ? ib : ib - 2, jb));
        p.add(new Rect("욕실", hc + 2, ja, hc + 3, back1));
        if (wing) {
            p.add(new Rect("침실", hc + 5, ja, ib, back1));
            p.add(new Rect("침실", ia, ja, cx1 - 1, jsb - 1));
        } else {
            int m = hc + 5 + (ib - hc - 5) / 2;
            p.add(new Rect("침실", hc + 5, ja, m - 1, back1));
            p.add(new Rect("침실", m + 1, ja, ib, back1));
        }
        return p;
    }

    /** 원룸 한 층 (안 되면 null): 계단실 옆 앞뒤로 긴 방 (욕실 포함 6×8 이상), 계단실 뒤 날개는 다용도실 */
    private List<Rect> oneRoomPlan() {
        int W = ib - hc + 1, D = jb - ja + 1;
        if (!core || elevator || Math.min(W, D) < 6 || Math.max(W, D) < 8) {
            return null;
        }
        List<Rect> p = new ArrayList<>();
        p.add(new Rect("원룸", hc, ja, ib, jb));
        p.add(new Rect("욕실", ib - 1, jb - 2, ib, jb));
        p.add(new Rect("주방", hc, jb - 5, hc + 1, jb - 2));
        if (wingW() >= 2 && wingD() >= 2) {
            p.add(new Rect("다용도실", ia, ja, cx1 - 1, jsb - 1));
        }
        return p;
    }

    private Area floorArea(int k) {
        return new Area(ia, ib, ja, jb, levels[k], levels[k + 1] - levels[k], hc, jb, this::plate, true);
    }

    /** 복도 양쪽 원룸 (6×8 이상) 이 몇 개 들어가는지 / 들어가게 그림. 가로·세로 방향 중 많이 나오는 쪽 */
    private int studios(int k, boolean draw) {
        Area A = floorArea(k);
        dry = true;
        int a = roomGrid(A, A.ei, A.b1, 6, 8, 6, 8, "pale_oak", room -> { });
        int b = roomGrid(A, A.ei, A.b1, 8, 6, 6, 8, "pale_oak", room -> { });
        dry = false;
        if (draw) {
            int rw = a >= b ? 6 : 8;
            roomGrid(A, A.ei, A.b1, rw, 14 - rw, 6, 8, "pale_oak", room -> {
                int su = room.other() > room.door() ? 1 : -1;
                Rect bath = studio(A.L, A.top(), room.door(), su, room.near(), room.dir(), room.e() - room.s() + 1,
                        room.r1() - room.r0() + 1, false);
                Rect in = new Rect("원룸", room.s(), room.r0(), room.e(), room.r1());
                units.add(new Unit("원룸", k, in.w(), in.d(), List.of(in, bath)));
            });
        }
        return Math.max(a, b);
    }

    /** 고시원 방 (3×4 이상) 이 몇 개 들어가는지 (첫 방은 공용 욕실) */
    private int gosiwonRooms(int k) {
        Area A = floorArea(k);
        dry = true;
        int n = roomGrid(A, A.ei, A.b1 - 3, 3, 4, 3, 4, "pale_oak", room -> { });
        dry = false;
        return n;
    }

    /** 위층 살림집·원룸·고시원이 실거주 크기로 안 들어가면 다른 쓰임으로 바꿈 */
    private Tenant livable(int k, Tenant t) {
        boolean single = twoRoomPlan() != null || oneRoomPlan() != null;
        switch (t.use()) {
            case HOME -> {
                if (single) {
                    return t;
                }
            }
            case ONEROOM -> {
                if (studios(k, false) >= 2 || single) {
                    return t;
                }
                if (gosiwonRooms(k) >= 4) {
                    return new Tenant(Use.GOSIWON, KoreanNames.prefix(r) + "고시원", "1인실 월 35만", "");
                }
            }
            case GOSIWON -> {
                if (gosiwonRooms(k) >= 4) {
                    return t;
                }
                if (studios(k, false) >= 2 || single) {
                    return new Tenant(Use.ONEROOM, KoreanNames.prefix(r) + "하우스", "원룸 임대", "");
                }
            }
            default -> {
                return t;
            }
        }
        // 집이 안 들어가는 좁은 층: 동네에 맞는 다른 쓰임
        String p = KoreanNames.prefix(r);
        return switch (look) {
            case CAMPUS -> r.nextBoolean() ? new Tenant(Use.ACADEMY, p + "독서실", "1인 좌석", "study") : new Tenant(Use.PC, p + " PC방", "24시간", "");
            case MANGWON, OLDTOWN -> r.nextBoolean() ? new Tenant(Use.STUDIO, p + "미용실", "커트·파마", "salon") : new Tenant(Use.OFFICE, p + "세무사", "세무·기장", "");
            case TRAIL -> new Tenant(Use.FOOD, p + "막걸리", "2층 단체석", "pajeon");
            case ITAEWON -> new Tenant(Use.STUDIO, EN[r.nextInt(EN.length)] + " YOGA", "YOGA", "yoga");
            default -> new Tenant(Use.OFFICE, p + "사무소", "", "");
        };
    }

    /** 살림집 층: 투룸이 되면 투룸, 아니면 원룸 한 채 */
    private void home(Area A) {
        List<Rect> two = twoRoomPlan();
        if (two != null) {
            twoRoom(A, two);
            return;
        }
        List<Rect> one = oneRoomPlan();
        if (one != null) {
            oneRoomUnit(A, one);
        }
    }

    /** 원룸 층: 복도 양쪽에 원룸이 둘 이상 나오면 그렇게, 아니면 한 층 한 채 */
    private void oneroom(Area A) {
        if (studios(curFloor, false) >= 2) {
            studios(curFloor, true);
        } else {
            home(A);
        }
    }

    /** 고시원 층: 복도 양쪽 방 (첫 방은 공용 욕실), 창가 공용 주방 */
    private void gosiwon(Area A) {
        int L = A.L;
        int[] idx = {0};
        List<Rect> rooms = new ArrayList<>();
        roomGrid(A, A.ei, A.b1 - 3, 3, 4, 3, 4, "pale_oak", room -> {
            boolean bath = idx[0]++ == 0;
            rooms.add(new Rect(bath ? "공용욕실" : "방", room.s(), room.r0(), room.e(), room.r1()));
            if (bath) {
                sharedBath(A, room);
            } else {
                gosiwonRoom(A, room);
            }
        });
        // 공용 주방 (창가 줄)
        for (int i = A.ei + 2; i <= A.a1; i++) {
            if (free(A, i, A.b1)) {
                int m = Math.floorMod(i - A.ei, 4);
                v.set(i, L, A.b1, m == 0 ? CAULDRON : m == 1 ? SMOKER_LIT : m == 2 ? Furniture.FRIDGE : Furniture.COUNTER);
                if (m == 2) {
                    v.set(i, L + 1, A.b1, Furniture.FRIDGE);
                }
            }
        }
        rooms.add(new Rect("공용주방", A.ei + 1, A.b1 - 1, A.a1, A.b1));
        units.add(new Unit("고시원", curFloor, A.a1 - A.ei, A.b1 - A.b0 + 1, rooms));
    }

    /** 고시원 방: 안쪽 벽 침대, 문 쪽 열에 책상과 위 선반, 문 옆 옷걸이 */
    private void gosiwonRoom(Area A, Room R) {
        int L = A.L, top = A.top(), far = R.far(), dir = R.dir(), o = R.other();
        Furniture.bed(F, r, o, L, far, R.south ? "north" : "south");
        v.set(R.door, L, far, Furniture.DESK_TOP);
        v.set(R.door, L + 2, far, Furniture.BOOKSHELF);
        Furniture.chair(F, R.door, L, far - dir, R.back(), "birch");
        if (R.r1 - R.r0 >= 3) {
            v.set(o, L, R.near(), Rooms.WARDROBE);
            v.set(o, L + 1, R.near(), Rooms.WARDROBE);
        }
        v.set((R.s + R.e) / 2, top, (R.r0 + R.r1) / 2, Interior.LIGHT);
    }

    /** 고시원 공용 욕실: 타일, 안쪽 줄 변기 칸, 옆 세면대, 문 쪽 샤워 칸 */
    private void sharedBath(Area A, Room R) {
        int L = A.L, top = A.top(), far = R.far(), dir = R.dir(), o = R.other();
        for (int j = R.r0; j <= R.r1; j++) {
            for (int i = R.s; i <= R.e; i++) {
                v.set(i, L - 1, j, Interior.TILE);
            }
        }
        for (int i = R.s; i <= R.e; i++) {
            if (i != R.door) {
                v.set(i, L, far, TOILET);
            }
        }
        v.set(o, L, far - dir, CAULDRON);
        v.set(o, L + 1, far - dir, MIRROR);
        if (R.r1 - R.r0 >= 3) {
            v.set(o, L, R.near(), CURTAIN);
            v.set(o, top, R.near(), Block.of("end_rod[facing=down]", 0xE6E1D9));
        }
        v.set(R.door, top, (R.r0 + R.r1) / 2, Interior.LIGHT);
        // 문 옆 이름표 (복도 쪽)
        int wj = R.south ? R.r0 - 1 : R.r1 + 1, cj = wj - dir, si = R.door == R.s ? R.door + 1 : R.door - 1;
        if (A.has(si, cj) && v.get(si, L + 1, cj) == null) {
            v.set(si, L + 1, cj, sign("birch", R.south ? "north" : "south", "black", false, "", "샤워실", "화장실"));
        }
    }

    /**
     * 원룸 한 칸 (안쪽 W×D, 욕실 포함). (u, t) 좌표: u 는 문 쪽 열(0)에서 반대쪽(W-1)으로, t 는 문 쪽 줄(0)에서
     * 안쪽(D-1)으로, 칸 = (ou + u·su, ot + t·st). (0, 0) 으로 들어옴.
     * 욕실 2×3 (변기·세면대·샤워, 문 반대쪽 앞 구석, 벽과 문), 문 쪽 벽 부엌(싱크·가스레인지·조리대·냉장고),
     * 안쪽 침대·옷장·책상. 욕실 안쪽 사각형을 돌려줍니다.
     */
    private Rect studio(int L, int top, int ou, int su, int ot, int st, int W, int D, boolean deskAway) {
        java.util.function.IntBinaryOperator I = (u, t) -> ou + u * su, J = (u, t) -> ot + t * st;
        // 욕실
        for (int t = 0; t <= 3; t++) {
            v.fill(I.applyAsInt(W - 3, t), L, J.applyAsInt(W - 3, t), I.applyAsInt(W - 3, t), top, J.applyAsInt(W - 3, t), Interior.INNER_WALL);
        }
        for (int u = W - 2; u <= W - 1; u++) {
            v.fill(I.applyAsInt(u, 3), L, J.applyAsInt(u, 3), I.applyAsInt(u, 3), top, J.applyAsInt(u, 3), Interior.INNER_WALL);
            for (int t = 0; t <= 2; t++) {
                v.set(I.applyAsInt(u, t), L - 1, J.applyAsInt(u, t), Interior.TILE);
            }
        }
        String toward = su > 0 ? "west" : "east";
        v.set(I.applyAsInt(W - 3, 1), L, J.applyAsInt(W - 3, 1), Blocks.door("pale_oak", toward, false));
        v.set(I.applyAsInt(W - 3, 1), L + 1, J.applyAsInt(W - 3, 1), Blocks.door("pale_oak", toward, true));
        v.set(I.applyAsInt(W - 1, 0), L, J.applyAsInt(W - 1, 0), TOILET);
        v.set(I.applyAsInt(W - 2, 0), L, J.applyAsInt(W - 2, 0), CAULDRON);
        v.set(I.applyAsInt(W - 2, 0), L + 1, J.applyAsInt(W - 2, 0), MIRROR);
        v.set(I.applyAsInt(W - 2, 2), L, J.applyAsInt(W - 2, 2), CURTAIN);
        v.set(I.applyAsInt(W - 1, 2), top, J.applyAsInt(W - 1, 2), Block.of("end_rod[facing=down]", 0xE6E1D9));
        v.set(I.applyAsInt(W - 1, 1), top, J.applyAsInt(W - 1, 1), Interior.LIGHT);
        // 부엌 (문 쪽 열)
        Block[] kit = {CAULDRON, Block.of("smoker[facing=" + (su > 0 ? "east" : "west") + ",lit=false]", 0x555451), Furniture.COUNTER, Furniture.FRIDGE};
        for (int n = 0; n < kit.length && 2 + n <= D - 1; n++) {
            int i = I.applyAsInt(0, 2 + n), j = J.applyAsInt(0, 2 + n);
            v.set(i, L, j, kit[n]);
            v.set(i, top, j, kit[n] == Furniture.FRIDGE ? Furniture.FRIDGE : Furniture.COUNTER);
            if (kit[n] == Furniture.FRIDGE) {
                v.set(i, L + 1, j, Furniture.FRIDGE);
            }
        }
        // 침대 (안쪽 줄, 머리는 반대쪽 벽)
        Furniture.bed(F, r, I.applyAsInt(W - 1, D - 1), L, J.applyAsInt(W - 1, D - 1), toward);
        if (D - 2 >= 4) {
            v.set(I.applyAsInt(W - 1, D - 2), L, J.applyAsInt(W - 1, D - 2), Rooms.WARDROBE);
            v.set(I.applyAsInt(W - 1, D - 2), L + 1, J.applyAsInt(W - 1, D - 2), Rooms.WARDROBE);
        }
        // 책상 (부엌과 안 겹치게)
        int du = deskAway || D - 2 <= 5 ? 2 : 0;
        if (du < W - 2) {
            v.set(I.applyAsInt(du, D - 1), L, J.applyAsInt(du, D - 1), Furniture.DESK_TOP);
            Furniture.chair(F, I.applyAsInt(du, D - 2), L, J.applyAsInt(du, D - 2), st > 0 ? "north" : "south", "birch");
        }
        // 넓으면 소파와 낮은 탁자
        if (W >= 8 && D >= 8) {
            int su0 = 2, t0 = D / 2;
            if (v.get(I.applyAsInt(su0, t0), L, J.applyAsInt(su0, t0)) == null) {
                Furniture.sofa(Frame.of(v), r, Math.min(I.applyAsInt(su0, t0), I.applyAsInt(su0 + 1, t0)), L, J.applyAsInt(su0, t0), 2,
                        st > 0 ? "north" : "south");
                v.set(I.applyAsInt(su0, t0 - 1), L, J.applyAsInt(su0, t0 - 1), LOW_TABLE);
            }
        }
        v.set(I.applyAsInt(W / 2, D / 2 + 1), top, J.applyAsInt(W / 2, D / 2 + 1), Interior.LIGHT);
        v.set(I.applyAsInt(1, 1), top, J.applyAsInt(1, 1), Interior.LIGHT);
        int bi0 = I.applyAsInt(W - 2, 0), bi1 = I.applyAsInt(W - 1, 0), bj0 = J.applyAsInt(W - 2, 0), bj1 = J.applyAsInt(W - 2, 2);
        return new Rect("욕실", Math.min(bi0, bi1), Math.min(bj0, bj1), Math.max(bi0, bi1), Math.max(bj0, bj1));
    }

    /** 한 층 원룸 한 채 (계단참 문으로 바로 들어감), 계단실 뒤 날개는 세탁기 둔 다용도실 */
    private void oneRoomUnit(Area A, List<Rect> p) {
        int L = A.L, top = A.top();
        Rect in = p.get(0);
        boolean wing = p.size() > 3;
        studio(L, top, hc, 1, jb, -1, in.w(), in.d(), wing);
        if (wing) {
            Rect x = p.get(3);
            v.fill(cx1, L, ja, cx1, top, jsb - 1, Interior.INNER_WALL);
            v.set(cx1, L, ja, Blocks.door("pale_oak", "west", false));
            v.set(cx1, L + 1, ja, Blocks.door("pale_oak", "west", true));
            for (int j = x.j0(); j <= x.j1(); j++) {
                if (j != ja && v.get(x.i0(), L, j) == null) {
                    v.set(x.i0(), L, j, (j & 1) == 0 ? IRON_BLOCK : BARREL);
                }
            }
            v.set((x.i0() + x.i1()) / 2, top, (x.j0() + x.j1()) / 2, Interior.LIGHT);
        }
        units.add(new Unit("원룸", curFloor, in.w(), in.d(), p));
    }

    /** 한 층 투룸 한 채: 현관·주방·거실(앞), 욕실·침실(뒤), 날개 침실 */
    private void twoRoom(Area A, List<Rect> p) {
        int L = A.L, top = A.top();
        Rect hall = p.get(0), kit = p.get(1), liv = p.get(2), bath = p.get(3), bedA = p.get(4), bedB = p.get(5);
        boolean galley = kit.i0() == hc, wing = bedB.i1() < hc;
        int back1 = bath.j1(), wallRow = back1 + 1;
        Block in = Interior.INNER_WALL;
        // 벽: 앞·뒤 가름 (복도 열 hc 는 열어 둠), 욕실 양옆, 날개 방 / 두 침실 사이
        v.fill(hc + 1, L, wallRow, ib, top, wallRow, in);
        v.fill(hc + 1, L, ja, hc + 1, top, back1, in);
        v.fill(hc + 4, L, ja, hc + 4, top, back1, in);
        if (wing) {
            v.fill(cx1, L, ja, cx1, top, jsb - 1, in);
        } else {
            v.fill(bedA.i1() + 1, L, ja, bedA.i1() + 1, top, back1, in);
        }
        // 문
        homeDoor(hc + 1, ja + 1, "east");
        homeDoor(bedA.i0(), wallRow, "north");
        if (wing) {
            homeDoor(cx1, ja + 1, "west");
        } else {
            homeDoor(bedB.i0(), wallRow, "north");
        }
        // 바닥 (현관·욕실 타일)
        for (Rect t : new Rect[]{hall, bath}) {
            for (int j = t.j0(); j <= t.j1(); j++) {
                for (int i = t.i0(); i <= t.i1(); i++) {
                    v.set(i, L - 1, j, Interior.TILE);
                }
            }
        }
        // 현관 신발장
        v.set(hc + 1, L, jb, Rooms.WARDROBE);
        // 주방 (조리대 한 줄, 앞에 설 자리)
        int ci = galley ? hc + 1 : ib;
        Block[] row = {Furniture.FRIDGE, CAULDRON, Block.of("smoker[facing=" + (galley ? "west" : "west") + ",lit=false]", 0x555451), Furniture.COUNTER};
        for (int n = 0; n < 4; n++) {
            int j = kit.j0() + n;
            v.set(ci, L, j, row[n]);
            v.set(ci, top, j, n == 0 ? Furniture.FRIDGE : Furniture.COUNTER);
            if (n == 0) {
                v.set(ci, L + 1, j, Furniture.FRIDGE);
            }
        }
        // 거실: 창가 소파, 맞은편 TV, 낮은 탁자, 식탁
        int sx = liv.i1() - 2;
        if (sx > liv.i0()) {
            Furniture.sofa(F, r, sx, L, jb, 3, "north");
            v.set(sx + 1, L, jb - 2, LOW_TABLE);
            int tv = liv.i1();
            if (tv != bedA.i0() && tv != bedB.i0()) {
                v.set(tv, L, liv.j0(), LOW_TABLE);
                v.set(tv, L + 1, liv.j0(), Furniture.TV);
            }
        }
        if (galley && liv.w() >= 6 && liv.d() >= 6) {
            Furniture.table(F, liv.i0() + 1, L, liv.j0() + 2, 2, 1, "oak");
        }
        Furniture.plant(F, r, liv.i0(), L, jb);
        // 욕실: 세면대, 변기, 샤워 칸
        v.set(bath.i0(), L, ja, CAULDRON);
        v.set(bath.i0(), L + 1, ja, MIRROR);
        v.set(bath.i1(), L, ja, TOILET);
        v.set(bath.i0(), L, back1, CURTAIN);
        v.set(bath.i1(), top, back1, Block.of("end_rod[facing=down]", 0xE6E1D9));
        v.set(bath.i1(), top, ja + 1, Interior.LIGHT);
        // 침실
        bedroom(bedA, bedA.i0(), back1, L, top);
        bedroom(bedB, wing ? cx1 - 1 : bedB.i0(), wing ? ja + 1 : back1, L, top);
        for (int[] c : new int[][]{{(liv.i0() + liv.i1()) / 2, (liv.j0() + liv.j1()) / 2}, {hc, jb - 1}, {hc, kit.j0() + 1}, {hc, ja + 1}}) {
            if (v.get(c[0], top, c[1]) == null) {
                v.set(c[0], top, c[1], Interior.LIGHT);
            }
        }
        units.add(new Unit("투룸", curFloor, wing ? ib - ia + 1 : ib - hc + 1, jb - ja + 1, p));
    }

    private void homeDoor(int i, int j, String facing) {
        int L = levels[curFloor];
        v.set(i, L, j, Blocks.door("pale_oak", facing, false));
        v.set(i, L + 1, j, Blocks.door("pale_oak", facing, true));
    }

    /** 침실: 문에서 가장 먼 구석에 침대, 다른 구석에 옷장, 넓으면 책상 (문 앞 칸 di, dj 는 비움) */
    private void bedroom(Rect R, int di, int dj, int L, int top) {
        int ci = Math.abs(R.i0() - di) >= Math.abs(R.i1() - di) ? R.i0() : R.i1();
        int cj = Math.abs(R.j0() - dj) >= Math.abs(R.j1() - dj) ? R.j0() : R.j1();
        boolean alongI = R.w() > R.d();
        String toward = alongI ? (ci == R.i0() ? "east" : "west") : (cj == R.j0() ? "south" : "north");
        Furniture.bed(F, r, ci, L, cj, toward);
        int wi = alongI ? ci : (ci == R.i0() ? R.i1() : R.i0());
        int wj = alongI ? (cj == R.j0() ? R.j1() : R.j0()) : cj;
        if (Math.abs(wi - di) + Math.abs(wj - dj) > 1 && v.get(wi, L, wj) == null) {
            v.set(wi, L, wj, Rooms.WARDROBE);
            v.set(wi, L + 1, wj, Rooms.WARDROBE);
        }
        if (R.w() >= 4 && R.d() >= 4) {
            int xi = ci == R.i0() ? R.i1() : R.i0(), xj = cj;
            if (!alongI && Math.abs(xi - di) + Math.abs(xj - dj) > 1 && v.get(xi, L, xj) == null) {
                v.set(xi, L, xj, Furniture.DESK_TOP);
            }
        }
        v.set((R.i0() + R.i1()) / 2, top, (R.j0() + R.j1()) / 2, Interior.LIGHT);
    }

    /** 사무실: 입구 안내 데스크, 마주 보는 책상 줄, 뒤 회의실. 부동산·환전소·증권사는 손님 계산대 */
    private void office(Tenant t, Area A) {
        int L = A.L;
        String k = t.kind();
        if (k.equals("exchange") || k.equals("bank")) {
            int cj = Math.max(A.b0 + 2, A.b1 - 4);
            for (int i = A.a0; i <= A.a1 - 1; i++) {
                if (free(A, i, cj)) {
                    v.set(i, L, cj, Furniture.COUNTER);
                    v.set(i, L + 1, cj, Block.of("glass_pane", 0xC8DCE4));
                    if (free(A, i, cj - 1) && (i & 1) == 0) {
                        Furniture.chair(F, i, L, cj - 1, "north", "dark_oak");
                    }
                }
            }
            for (int i = A.a0; i <= A.a1; i += 2) {
                if (free(A, i, A.b1)) {
                    Furniture.chair(F, i, L, A.b1, "south", "birch");
                }
            }
            Rooms.office(F, r, A.a0 - 1, A.b0 - 1, A.a1 + 1, cj - 2, L, A.h, (i, j) -> free(A, i, j) && j < cj - 1);
            return;
        }
        if (k.equals("realty")) {
            for (int i = A.a0 + 1; i <= A.a1 - 1; i += 3) {
                if (free(A, i, A.b0 + 2)) {
                    Furniture.desk(F, i, L, A.b0 + 2, "north");
                    if (free(A, i, A.b0 + 3)) {
                        Furniture.chair(F, i, L, A.b0 + 3, "south", "birch");
                    }
                }
            }
            if (freeBox(A, A.a1 - 2, A.b1 - 1, A.a1, A.b1 - 1)) {
                Furniture.sofa(F, r, A.a1 - 2, L, A.b1 - 1, 3, "north");
            }
            return;
        }
        // 회의실 (뒤 오른쪽 구석)
        int ma0 = A.a1 - 5, mb1 = A.b0 + 4;
        if (ma0 > A.ei + 2 && freeBox(A, ma0, A.b0, A.a1, mb1)) {
            Rooms.meeting(F, ma0, A.b0, A.a1, mb1, L, A.h);
            v.set(ma0, L, A.b0, Block.of("white_stained_glass_pane", 0xF0F0F0));
        }
        put(A, A.ei + 2, A.b1 - 1, Furniture.COUNTER);
        Rooms.office(F, r, A.a0 - 1, A.b0 - 1, A.a1 + 1, A.b1 + 1, L, A.h, (i, j) -> free(A, i, j));
    }

    /** PC방: 입구 계산대와 음료 냉장고, 마주 보는 컴퓨터 책상 줄 */
    private void pc(Area A) {
        int L = A.L;
        for (int j = A.b1 - 2; j <= A.b1 - 1; j++) {
            put(A, Math.min(A.a1, A.ei + 2), j, Furniture.COUNTER);
        }
        if (free(A, A.a1, A.b1)) {
            v.set(A.a1, L, A.b1, Furniture.FRIDGE);
            v.set(A.a1, L + 1, A.b1, MIRROR);
        }
        for (int j = A.b0 + 1; j + 1 <= A.b1 - 3; j += 5) {
            for (int i = A.a0; i <= A.a1; i++) {
                if (Math.floorMod(i - A.a0, 7) == 6) {
                    continue;
                }
                if (free(A, i, j) && free(A, i, j + 1) && free(A, i, j - 1) && free(A, i, j + 2)) {
                    Furniture.desk(F, i, L, j, "north");
                    Furniture.desk(F, i, L, j + 1, "south");
                }
            }
        }
    }

    /** 노래방: 카운터, 복도 양쪽 방 (소파, 낮은 탁자, 화면, 색 조명) */
    private void norae(Area A) {
        roomGrid(A, A.ei, A.b1 - 2, 3, 3, "dark_oak", room -> noraeRoom(A, room));
        put(A, Math.min(A.a1, A.ei + 2), A.b1 - 1, Furniture.COUNTER);
        put(A, Math.min(A.a1, A.ei + 3), A.b1 - 1, Furniture.COUNTER);
        if (free(A, A.a1, A.b1)) {
            v.set(A.a1, A.L, A.b1, Furniture.FRIDGE);
            v.set(A.a1, A.L + 1, A.b1, MIRROR);
        }
    }

    private void noraeRoom(Area A, Room R) {
        int L = A.L, top = A.top(), far = R.far(), dir = R.dir();
        Furniture.sofa(F, r, R.s, L, far, R.e - R.s + 1, R.south ? "north" : "south");
        int o = R.other();
        if (R.r1 - R.r0 >= 2) {
            v.set(o, L, far - dir, LOW_TABLE);
        }
        v.set(o, L + 1, R.near(), Furniture.TV);
        v.set((R.s + R.e) / 2, top, (R.r0 + R.r1) / 2, Block.of("shroomlight", 0xF09246));
    }

    /** 운동·미용·사진관·합주실·스크린골프 */
    private void studio(Tenant t, Area A) {
        int L = A.L, top = A.top();
        String k = t.kind();
        switch (k) {
            case "golf" -> {
                roomGrid(A, A.ei, A.b1 - 2, 4, 5, "dark_oak", room -> {
                    for (int j = room.r0(); j <= room.r1(); j++) {
                        for (int i = room.s(); i <= room.e(); i++) {
                            v.set(i, L - 1, j, Block.of("green_concrete_powder", 0x5E7A2A));
                        }
                        }
                    for (int i = room.s(); i <= room.e(); i++) {
                        for (int y = L; y <= top; y++) {
                            v.set(i, y, room.far(), WHITE_CONCRETE);
                        }
                    }
                    v.set(room.other(), L, room.far() - 2 * room.dir(), carpet("white"));
                    int sl = Math.min(2, room.e() - room.s());
                    Furniture.sofa(F, r, room.door() == room.s() ? room.e() - sl + 1 : room.s(), L, room.near(), sl, room.south() ? "south" : "north");
                    v.set((room.s() + room.e()) / 2, top, (room.r0() + room.r1()) / 2, Interior.LIGHT);
                });
                put(A, Math.min(A.a1, A.ei + 2), A.b1 - 1, Furniture.COUNTER);
            }
            case "band", "piano" -> {
                roomGrid(A, A.ei, A.b1 - 2, 3, k.equals("band") ? 4 : 3, "dark_oak", room -> {
                    int far = room.far(), o = room.other();
                    if (k.equals("band")) {
                        v.set(o, L, far, NOTE);
                        v.set(room.door(), L, far, BLACK_CONCRETE);
                        v.set(room.door(), L + 1, far, BLACK_CONCRETE);
                        v.set(o, L, far - room.dir(), Block.of("lightning_rod[facing=up,powered=false,waterlogged=false]", 0xC57A55));
                    } else {
                        v.set(o, L, far, BLACK_CONCRETE);
                        v.set(o, L + 1, far, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
                        Furniture.chair(F, o, L, far - room.dir(), room.back(), "dark_oak");
                    }
                    v.set((room.s() + room.e()) / 2, top, (room.r0() + room.r1()) / 2, Interior.LIGHT);
                });
                put(A, Math.min(A.a1, A.ei + 2), A.b1 - 1, Furniture.COUNTER);
            }
            case "photo" -> {
                roomGrid(A, A.ei, A.b1 - 3, 2, 3, "birch", room -> {
                    for (int i = room.s(); i <= room.e(); i++) {
                        v.set(i, L, room.far(), Blocks.wool(r.nextBoolean() ? "pink" : "white"));
                        v.set(i, L + 1, room.far(), Blocks.wool("white"));
                    }
                    Furniture.chair(F, room.other(), L, room.far() - room.dir(), room.south() ? "south" : "north", "birch");
                    v.set(room.door(), top, room.near(), Block.of("pearlescent_froglight[axis=y]", 0xEDDBDC));
                });
                for (int i = A.a0; i <= A.a1; i += 2) {
                    if (free(A, i, A.b1 - 1)) {
                        v.set(i, L, A.b1 - 1, SMOOTH_QUARTZ);
                        v.set(i, L + 1, A.b1 - 1, (i & 2) == 0 ? MIRROR : candle("pink"));
                    }
                }
            }
            case "salon", "nail", "tattoo" -> {
                // 거울 앞 의자 (오른쪽 벽), 뒤 샴푸대, 앞 대기 소파
                for (int j = A.b0 + 1; j <= A.b1 - 2; j += 2) {
                    if (free(A, A.a1, j) && free(A, A.a1 - 1, j)) {
                        v.set(A.a1, L, j, Furniture.DESK_TOP);
                        v.set(A.a1, L + 1, j, k.equals("salon") ? MIRROR : LANTERN);
                        Furniture.chair(F, A.a1 - 1, L, j, "west", k.equals("tattoo") ? "dark_oak" : "birch");
                    }
                }
                if (k.equals("nail") || k.equals("tattoo")) {
                    for (int j = A.b0 + 2; j <= A.b1 - 2; j += 3) {
                        for (int i = A.a0 + 1; i <= A.a1 - 4; i += 3) {
                            if (free(A, i, j) && free(A, i, j - 1) && free(A, i, j + 1)) {
                                v.set(i, L, j, k.equals("tattoo") ? Blocks.wool("black") : Furniture.DESK_TOP);
                                v.set(i, L + 1, j, k.equals("tattoo") ? null : LANTERN);
                                Furniture.chair(F, i, L, j - 1, "north", "birch");
                                Furniture.chair(F, i, L, j + 1, "south", "birch");
                            }
                        }
                    }
                } else {
                    for (int i = A.a0; i <= A.a1 - 2; i += 2) {
                        if (free(A, i, A.b0)) {
                            v.set(i, L, A.b0, CAULDRON);
                            Furniture.chair(F, i, L, A.b0 + 1, "north", "dark_oak");
                        }
                    }
                }
                put(A, Math.min(A.a1 - 1, A.ei + 2), A.b1, Furniture.COUNTER);
            }
            default -> {
                // 운동 공간: 한쪽 벽 거울, 기구·매트
                for (int j = A.b0; j <= A.b1; j++) {
                    if (free(A, A.a1, j)) {
                        for (int y = L; y <= top; y++) {
                            v.set(A.a1, y, j, MIRROR);
                        }
                    }
                }
                for (int j = A.b0 + 1; j <= A.b1 - 2; j += 3) {
                    for (int i = A.a0 + 1; i <= A.a1 - 2; i += 3) {
                        switch (k) {
                            case "pilates" -> {
                                if (free(A, i, j) && free(A, i, j + 1)) {
                                    v.set(i, L, j, IRON_BARS);
                                    v.set(i, L, j + 1, Block.of("smooth_quartz_slab[type=bottom,waterlogged=false]", 0xECE6DF));
                                }
                            }
                            case "gym" -> {
                                if (free(A, i, j) && free(A, i, j + 1)) {
                                    v.set(i, L, j, (i + j) % 2 == 0 ? Block.of("anvil[facing=east]", 0x444444) : IRON_BLOCK);
                                    v.set(i, L, j + 1, Block.of("polished_blackstone_slab[type=bottom,waterlogged=false]", 0x353038));
                                }
                            }
                            case "taekwondo" -> {
                                put(A, i, j, carpet((i + j) % 2 == 0 ? "blue" : "red"));
                                put(A, i + 1, j, carpet((i + j) % 2 == 0 ? "red" : "blue"));
                            }
                            default -> {
                                if (free(A, i, j) && free(A, i, j + 1)) {
                                    String c = new String[]{"purple", "blue", "green", "pink"}[Math.floorMod(i + j, 4)];
                                    v.set(i, L, j, carpet(c));
                                    v.set(i, L, j + 1, carpet(c));
                                }
                            }
                        }
                    }
                }
                if (free(A, A.a0, A.b1)) {
                    Furniture.plant(F, r, A.a0, L, A.b1);
                }
            }
        }
    }

    /** 을지로 위층 작업장: 인쇄·제본기, 종이 더미, 금속 가공 기계, 작업대 */
    private void workshop(Tenant t, Area A) {
        int L = A.L;
        boolean metal = t.kind().equals("metal");
        for (int j = A.b0 + 1; j + 1 <= A.b1 - 2; j += 4) {
            for (int i = A.a0 + 1; i + 2 <= A.a1 - 1; i += 4) {
                if (!freeBox(A, i, j, i + 2, j + 1)) {
                    continue;
                }
                if (metal) {
                    v.set(i, L, j, Block.of("anvil[facing=east]", 0x444444));
                    v.set(i + 1, L, j, Block.of("smithing_table", 0x3F3F4A));
                    v.set(i + 2, L, j, Block.of("grindstone[face=floor,facing=south]", 0x8E8E8E));
                    v.set(i, L, j + 1, SPRUCE_TOP);
                    v.set(i + 1, L, j + 1, SPRUCE_TOP);
                    v.set(i + 2, L, j + 1, Block.of("blast_furnace[facing=south,lit=false]", 0x505050));
                } else {
                    v.fill(i, L, j, i + 2, L, j, (j & 4) == 0 ? GRAY_CONCRETE : BLUE_CONCRETE);
                    v.fill(i, L + 1, j, i + 1, L + 1, j, IRON_BLOCK);
                    v.set(i, L, j + 1, WHITE_SHEET);
                    v.set(i + 1, L, j + 1, WHITE_SHEET);
                    v.set(i + 2, L, j + 1, Block.of("stonecutter[facing=south]", 0x7B7774));
                }
            }
        }
        for (int i = A.a0; i <= A.a1; i++) {
            if (free(A, i, A.b0)) {
                v.set(i, L, A.b0, metal ? IRON_BARS : WHITE_SHEET);
                v.set(i, L + 1, A.b0, metal ? CHAIN : Block.of("light_gray_wool", 0x8E8E86));
            }
        }
        if (free(A, A.a1, A.b1 - 1)) {
            Furniture.desk(F, A.a1, L, A.b1 - 1, "west");
        }
    }

    /** 당구장: 녹색 당구대와 위 등, 벽 의자, 카운터 */
    private void billiard(Area A) {
        int L = A.L, top = A.top();
        for (int j = A.b0 + 1; j + 2 <= A.b1 - 1; j += 5) {
            for (int i = A.a0 + 1; i + 1 <= A.a1 - 1; i += 4) {
                if (freeBox(A, i, j, i + 1, j + 2) && A.has(i - 1, j + 1) && A.has(i + 2, j + 1)) {
                    v.fill(i, L, j, i + 1, L, j + 2, Block.of("green_wool", 0x546D1B));
                    v.set(i, top, j + 1, LANTERN_HANGING);
                    v.set(i + 1, top, j + 1, LANTERN_HANGING);
                }
            }
        }
        for (int j = A.b0; j <= A.b1 - 2; j += 3) {
            if (free(A, A.a0, j)) {
                Furniture.chair(F, A.a0, L, j, "west", "dark_oak");
            }
        }
        put(A, Math.min(A.a1, A.ei + 2), A.b1 - 1, Furniture.COUNTER);
    }

    // ------------------------------------------------------------------ 옥상

    private void roofDeck() {
        int R = roof;
        Block finish = terrace ? (look == Look.ITAEWON ? DARK_PLANKS : SPRUCE_PLANKS) : switch (look) {
            case EULJIRO, OLDTOWN, MANGWON, CAMPUS, MUKJA -> GREEN_CONCRETE;
            default -> SMOOTH_STONE;
        };
        for (int j = ja; j <= jb; j++) {
            for (int i = ia; i <= ib; i++) {
                if (!inStrip(i, j)) {
                    v.set(i, R - 1, j, finish);
                }
            }
        }
        // 난간 (테라스는 낮은 벽 위 유리·철 난간, 등산로 식당은 기와 처마)
        boolean eaves = look == Look.TRAIL && !terrace;
        Block rail = look == Look.ITAEWON ? IRON_BARS : GLASS_PANE;
        for (int j = 0; j <= jf; j++) {
            for (int i = i0; i <= i1; i++) {
                boolean edge = i == i0 || i == i1 || j == 0 || j == jf;
                if (!edge || inStrip(i, j)) {
                    continue;
                }
                if (eaves) {
                    String facing = j == jf ? "north" : j == 0 ? "south" : i == i0 ? "east" : "west";
                    v.set(i, R, j, Blocks.stairs("deepslate_tile", facing, 0x363637));
                } else {
                    v.set(i, R, j, wall);
                    v.set(i, R + 1, j, terrace ? rail : SMOOTH_STONE_SLAB);
                }
            }
        }
        if (terrace) {
            terraceFurniture();
            return;
        }
        boolean shed = (look == Look.OLDTOWN || look == Look.CAMPUS || look == Look.MANGWON || look == Look.EULJIRO)
                && r.nextInt(10) < 4 && ib - 4 > hc + 1 && jsb > 6;
        if (shed) {
            rooftopRoom();
        } else if (ib - hc >= 4 && jb - ja >= 5) {
            tank(ib - 1, ja, R);
        }
        // 실외기
        for (int n = 0; n < 2 + r.nextInt(3); n++) {
            int ai = hc + 2 + r.nextInt(Math.max(1, ib - hc - 2)), aj = ja + 1 + r.nextInt(Math.max(1, jb - ja - 2));
            if (!inStrip(ai, aj) && v.get(ai, R, aj) == null && aj < jb - 1) {
                v.set(ai, R, aj, SMOOTH_QUARTZ);
            }
        }
        billboard();
    }

    /** 옥상 물탱크: 을지로는 녹슨 사각 탱크, 동네는 파란 원통, 나머지는 스테인리스 사각 */
    private void tank(int ti, int tj, int y) {
        if (look == Look.OLDTOWN || look == Look.CAMPUS || look == Look.MANGWON || look == Look.MUKJA) {
            v.cylinder(ti + 0.5, tj + 1.0, 1.0, y, y + 1, LIGHT_BLUE_CONCRETE);
            return;
        }
        Block body = look == Look.EULJIRO ? Block.of("exposed_cut_copper", 0xA17D67) : IRON_BLOCK;
        for (int i = ti - 1; i <= ti; i++) {
            for (int j = tj; j <= tj + 1; j++) {
                v.set(i, y, j, IRON_BARS);
                v.set(i, y + 1, j, body);
                v.set(i, y + 2, j, body);
            }
        }
    }

    /** 옥탑방: 뒤 구석 작은 방 (초록 지붕, 문, 창, 침대·책상), 지붕 위 물탱크 */
    private void rooftopRoom() {
        int R = roof, x0 = ib - 4, x1 = i1, z0 = 0, z1 = 4;
        v.walls(x0, R, z0, x1, R + 2, z1, look == Look.OLDTOWN || look == Look.MANGWON ? BRICKS : WHITE_CONCRETE);
        v.fill(x0 + 1, R, z0 + 1, x1 - 1, R + 2, z1 - 1, AIR);
        v.fill(x0 + 1, R - 1, z0 + 1, x1 - 1, R - 1, z1 - 1, Rooms.MARU_LIGHT);
        v.fill(x0, R + 3, z0, x1, R + 3, z1, Blocks.terracotta("green"));
        int di = x0 + 2;
        v.set(di, R, z1, Blocks.door("spruce", "south", false));
        v.set(di, R + 1, z1, Blocks.door("spruce", "south", true));
        v.set(x1 - 1, R + 1, z1, GLASS_PANE);
        Furniture.bed(F, r, x1 - 1, R, z0 + 1, "south");
        v.set(x0 + 1, R, z0 + 1, Furniture.DESK_TOP);
        v.set(di, R + 2, z0 + 2, Interior.LIGHT);
        v.cylinder(x0 + 2.0, z0 + 2.0, 1.0, R + 4, R + 5, LIGHT_BLUE_CONCRETE);
    }

    /** 옥상 테라스: 나무 바닥, 파라솔 탁자, 줄 조명, 화분, 바 카운터 */
    private void terraceFurniture() {
        int R = roof;
        Area A = new Area(ia, ib, ja, jb, R, 4, hc, jb, this::plate, true);
        String canopy = new String[]{"white", "light_gray", "brown"}[r.nextInt(3)];
        for (int j = ja + 2; j + 1 <= jb - 1; j += 4) {
            for (int i = A.a0 + 2; i + 1 <= A.a1 - 1; i += 4) {
                if (!freeBox(A, i - 1, j - 1, i + 1, j + 1)) {
                    continue;
                }
                v.set(i, R, j, FENCE);
                v.set(i, R + 1, j, FENCE);
                v.fill(i - 1, R + 2, j - 1, i + 1, R + 2, j + 1, Blocks.wool(canopy));
                Furniture.chair(F, i - 1, R, j, "west", "dark_oak");
                Furniture.chair(F, i + 1, R, j, "east", "dark_oak");
            }
        }
        // 앞 난간의 줄 조명 기둥
        for (int i = hc + 1; i <= ib; i += 3) {
            v.set(i, R + 1, jf, FENCE);
            v.set(i, R + 2, jf, FENCE);
            v.set(i, R + 3, jf, LANTERN);
        }
        // 화분 (모서리)
        for (int[] p : new int[][]{{ib, ja}, {ib, jb}, {ia, ja}}) {
            if (free(A, p[0], p[1])) {
                v.set(p[0], R, p[1], MUD_BRICKS);
                v.set(p[0], R + 1, p[1], Block.of("flowering_azalea_leaves[distance=7,persistent=true,waterlogged=false]", 0x63753A));
            }
        }
        // 바 카운터 (오른쪽 벽)
        if (tenants[floors - 1].use() == Use.BAR || look == Look.ITAEWON) {
            for (int j = ja + 1; j <= Math.min(jb - 2, ja + 3); j++) {
                if (free(A, ib, j)) {
                    v.set(ib, R, j, DARK_PLANKS);
                    v.set(ib, R + 1, j, j % 2 == 0 ? BARREL : GLASS_BLOCK);
                }
            }
            v.set(ib, R + 1, ja + 1, null);
            v.set(ib - 1, R, ja + 2, null);
        }
    }

    /** 옥상 광고판 (간판 많은 동네): 앞 난간 위 판과 글씨, 뒤 철골 */
    private void billboard() {
        if (density < 3 || floors < 3 || r.nextInt(10) >= 4) {
            return;
        }
        int bs = hc + 1, be = ib, R = roof;
        if (be - bs < 3) {
            return;
        }
        Tenant t = tenants[floors - 1].use() == Use.HOME ? tenants[0] : tenants[floors - 1];
        Object[] b = board(t);
        v.fill(bs, R + 1, jf, be, R + 2, jf, (Block) b[0]);
        for (int i = bs; i <= be; i += 3) {
            v.set(i, R, jf - 1, IRON_BARS);
            v.set(i, R + 1, jf - 1, IRON_BARS);
        }
        int c = (bs + be) / 2;
        v.set(c, R + 2, d - 1, sign((String) b[1], "south", (String) b[2], true, "", t.name()));
        v.set(c, R + 1, d - 1, sign((String) b[1], "south", (String) b[2], true, t.sub(), ""));
        if (be - bs >= 6) {
            v.set(bs + 1, R + 2, d - 1, sign((String) b[1], "south", (String) b[2], true, "", extra()));
            v.set(be - 1, R + 2, d - 1, sign((String) b[1], "south", (String) b[2], true, "", t.name()));
        }
    }

    // ------------------------------------------------------------------ 돌출 간판·입간판

    private void signs() {
        boolean perFloor = floors >= 3 && switch (look) {
            case MYEONGDONG, MUKJA, BANGI, HONGDAE, CAMPUS -> true;
            case EULJIRO -> r.nextInt(10) < 6;
            default -> false;
        };
        if (perFloor) {
            column(i1 - 1, null);
            if (core && (look == Look.MYEONGDONG || (density >= 3 && w >= 14))) {
                column(cx1, null);
            }
        } else if (core && floors >= 3 && (look == Look.GANGNAM || look == Look.YEOUIDO || look == Look.PLAIN)) {
            column(cx1, look == Look.YEOUIDO ? new Object[]{BROWN_CONCRETE, "spruce", "white"} : new Object[]{GRAY_CONCRETE, "dark_oak", "white"});
        }
        // 1층 돌출 간판 (밥집·카페·술집)
        Tenant t0 = tenants[0];
        boolean blade = switch (look) {
            case ITAEWON, SANGSU, HONGDAE, MUKJA, BANGI, TRAIL, CAMPUS, EULJIRO -> true;
            default -> false;
        };
        if (blade && v.get(i1, 2, d - 1) == null) {
            Object[] b = board(t0);
            v.set(i1, 2, d - 1, blade((String) b[1], "east", (String) b[2], true, t0.name(), t0.sub()));
        }
        // 위층 돌출 간판 (이태원 술집·식당, 돌출 기둥이 없는 동네)
        if (!perFloor && (look == Look.ITAEWON || look == Look.HONGDAE || look == Look.SANGSU || look == Look.TRAIL)) {
            for (int k = 1; k < floors; k++) {
                Tenant t = tenants[k];
                int y = levels[k] + 1;
                if (t.use() != Use.HOME && v.get(i1, y, d - 1) == null) {
                    Object[] b = board(t);
                    v.set(i1, y, d - 1, blade((String) b[1], "east", (String) b[2], true, (k + 1) + "F", t.name(), t.sub()));
                }
            }
        }
        // 옥탑 앞 건물 이름
        if (core && floors >= 3 && density >= 2 && look != Look.TRAIL && look != Look.MANGWON && r.nextBoolean()) {
            v.set(i0 + 2, roof + 1, d - 1, sign("dark_oak", "south", "white", true, "", buildingName()));
        }
    }

    /** 세로 돌출 간판 기둥 (층마다 가게 이름, 양옆 표지판). colors 가 null 이면 층마다 그 가게 간판 색 */
    private void column(int iv, Object[] colors) {
        for (int k = 1; k < floors; k++) {
            Tenant t = tenants[k];
            if (t.use() == Use.HOME) {
                continue;
            }
            Object[] b = colors != null ? colors : board(t);
            int L = levels[k];
            for (int y = L; y <= L + 2; y++) {
                v.set(iv, y, d - 1, (Block) b[0]);
            }
            v.set(iv - 1, L + 1, d - 1, sign((String) b[1], "west", (String) b[2], true, (k + 1) + "F", t.name(), t.sub()));
            v.set(iv + 1, L + 1, d - 1, sign((String) b[1], "east", (String) b[2], true, (k + 1) + "F", t.name(), t.sub()));
        }
    }

    // ------------------------------------------------------------------ 생활 디테일

    private void details() {
        boolean old = look == Look.EULJIRO || look == Look.OLDTOWN || look == Look.MANGWON || look == Look.CAMPUS || look == Look.MUKJA;
        if (old && core && look != Look.MUKJA && v.get(i1 - 1, levels[1] + 1, d - 1) == null) {
            // 노란 가스관 (앞 모서리를 따라 위로, 돌출 간판 기둥이 없을 때)
            Block gas = Block.of("yellow_stained_glass_pane", 0xE5E533);
            for (int y = 2; y <= roof - 2; y++) {
                if (v.get(i1, y, d - 1) == null) {
                    v.set(i1, y, d - 1, gas);
                }
            }
        }
        boolean ac = look == Look.CAMPUS || look == Look.OLDTOWN || look == Look.MANGWON || look == Look.EULJIRO || look == Look.MUKJA;
        for (int k = 1; k < floors; k++) {
            int L = levels[k];
            for (int i = hc; i <= ib; i++) {
                if (v.get(i, L + 1, jf) == null || !v.get(i, L + 1, jf).id().contains("glass")) {
                    continue;
                }
                // 창 밖 실외기 (원룸·옛 건물), 2층 방범창 (동네)
                if (ac && r.nextInt(10) < 2 && v.get(i, L, d - 1) == null) {
                    v.set(i, L, d - 1, SMOOTH_QUARTZ);
                } else if (k == 1 && (look == Look.OLDTOWN || look == Look.MANGWON || look == Look.CAMPUS)
                        && v.get(i, L + 1, d - 1) == null && v.get(i, L + 2, d - 1) == null) {
                    v.set(i, L + 1, d - 1, IRON_BARS);
                    v.set(i, L + 2, d - 1, IRON_BARS);
                }
            }
        }
        // 골목 건물 옆벽 실외기와 가스 계량기
        if (!party) {
            for (int k = 0; k < floors; k++) {
                if (r.nextInt(10) < 5) {
                    int side = r.nextBoolean() ? i0 - 1 : i1 + 1;
                    int j = ja + 1 + r.nextInt(Math.max(1, jb - ja - 2));
                    if (v.get(side, levels[k], j) == null && side >= 0 && side < w) {
                        v.set(side, levels[k], j, SMOOTH_QUARTZ);
                    }
                }
            }
        }
        // 가게 앞 화분 (동네·카페 거리·등산로)
        if (look == Look.OLDTOWN || look == Look.SANGSU || look == Look.TRAIL) {
            for (int i : new int[]{i0 + 1 + (core ? width + 1 : 0), ib}) {
                if (v.get(i, 0, d - 1) == null && v.get(i, 0, jf) != null && !v.get(i, 0, jf).isAir()) {
                    Furniture.plant(F, r, i, 0, d - 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 좌우 뒤집기

    /** 좌우(동서)를 뒤집은 상자: 블록 방향·경첩·계단 모양·표지판 각도도 같이 */
    static Voxels mirrored(Voxels s) {
        Voxels m = new Voxels(s.w, s.d, s.y0, s.y0 + s.h - 1);
        for (int y = s.y0; y < s.y0 + s.h; y++) {
            for (int j = 0; j < s.d; j++) {
                for (int i = 0; i < s.w; i++) {
                    Block b = s.get(i, y, j);
                    if (b != null) {
                        m.set(s.w - 1 - i, y, j, mirror(b));
                    }
                }
            }
        }
        for (double[] c : s.carSpots()) {
            m.carSpot(s.w - c[0], (int) c[1], c[2], (int) -c[3], (int) c[4]);
        }
        return m;
    }

    static Block mirror(Block b) {
        String data = b.data();
        int k = data.indexOf('[');
        if (k < 0) {
            return b;
        }
        String[] parts = data.substring(k + 1, data.length() - 1).split(",");
        StringBuilder sb = new StringBuilder(data.length()).append(data, 0, k + 1);
        for (int n = 0; n < parts.length; n++) {
            int eq = parts[n].indexOf('=');
            String key = parts[n].substring(0, eq), val = parts[n].substring(eq + 1);
            switch (key) {
                case "east" -> key = "west";
                case "west" -> key = "east";
                case "facing" -> val = val.equals("east") ? "west" : val.equals("west") ? "east" : val;
                case "hinge" -> val = val.equals("left") ? "right" : "left";
                case "shape" -> val = val.replace("left", "#").replace("right", "left").replace("#", "right");
                case "rotation" -> val = Integer.toString((16 - Integer.parseInt(val)) % 16);
                default -> {
                }
            }
            if (n > 0) {
                sb.append(',');
            }
            sb.append(key).append('=').append(val);
        }
        return new Block(sb.append(']').toString(), b.rgb(), b.text());
    }
}
