package com.junseo.citymap.buildings;

import java.util.Random;

/** 간판·문패에 쓰는 실제 같은 한국 상호 (실제 회사 상표는 피함) */
final class KoreanNames {
    private static final String[] PREFIX = {"행복", "대성", "한빛", "우리", "제일", "현대", "신라", "동방", "새한", "금성", "삼성",
            "대한", "중앙", "평화", "희망", "소망", "은혜", "다온", "미소", "하나", "청솔", "푸른", "온누리", "백두", "한결", "보람"};

    /** 1층 가게: {이름, 업종} */
    private static final String[][] SHOPS = {
            {"%s약국", "약국"}, {"%s부동산", "공인중개사"}, {"%s슈퍼", "슈퍼"}, {"%s세탁소", "세탁"}, {"%s철물", "철물점"},
            {"할매국밥", "식당"}, {"원조순대국", "식당"}, {"%s분식", "분식"}, {"엄마손김밥", "분식"}, {"%s반점", "중국집"},
            {"%s치킨", "치킨"}, {"맥주한잔", "호프"}, {"%s정육점", "정육점"}, {"%s미용실", "미용실"}, {"헤어%s", "미용실"},
            {"%s안경", "안경점"}, {"%s모바일", "휴대폰"}, {"%s사진관", "사진관"}, {"%s꽃집", "꽃집"}, {"카페 %s", "카페"},
            {"%s떡집", "떡집"}, {"%s반찬", "반찬가게"}, {"%s인쇄", "인쇄소"}, {"%s열쇠", "열쇠"}, {"%s카센터", "카센터"},
            {"24시 편의점", "편의점"}, {"%s순대", "순대"}, {"%s곱창", "곱창"}, {"%s통닭", "통닭"}, {"%s수선", "옷수선"},
    };

    /** 2층 이상: {이름, 종류} */
    private static final String[][] UPPER = {
            {"%s수학학원", "학원"}, {"%s영어학원", "학원"}, {"%s태권도", "태권도"}, {"%s내과의원", "의원"}, {"%s치과", "치과"},
            {"%s한의원", "한의원"}, {"스타노래방", "노래방"}, {"%s PC방", "PC방"}, {"%s당구장", "당구장"}, {"%s세무회계", "사무실"},
            {"%s법무사", "사무실"}, {"%s교회", "교회"}, {"%s필라테스", "운동"}, {"%s독서실", "독서실"}, {"%s피아노", "학원"},
    };

    private static final String[] VILLA = {"%s빌라", "%s맨션", "%s하이츠", "%s빌", "%s타운", "%s연립", "%s주택"};
    private static final String[] FACTORY = {"%s정밀", "%s금속", "%s산업", "%s기계", "%s화학", "%s전기", "%s테크", "%s물산", "%s공업사"};

    static String prefix(Random r) {
        return PREFIX[r.nextInt(PREFIX.length)];
    }

    /** {상호, 업종} */
    static String[] shop(Random r) {
        String[] s = SHOPS[r.nextInt(SHOPS.length)];
        return new String[]{s[0].formatted(prefix(r)), s[1]};
    }

    static String[] upper(Random r) {
        String[] s = UPPER[r.nextInt(UPPER.length)];
        return new String[]{s[0].formatted(prefix(r)), s[1]};
    }

    static String villa(Random r) {
        return VILLA[r.nextInt(VILLA.length)].formatted(prefix(r));
    }

    static String factory(Random r) {
        return FACTORY[r.nextInt(FACTORY.length)].formatted(prefix(r)) + "(주)";
    }

    /** 번지 "123-4" */
    static String address(Random r) {
        return (100 + r.nextInt(800)) + "-" + (1 + r.nextInt(30));
    }

    private KoreanNames() {
    }
}
