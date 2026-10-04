package com.junseo.city.logic;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** 캐릭터 이름 규칙과 주민번호 만들기. */
public final class Identity {
    private static final Pattern KOREAN = Pattern.compile("^[가-힣]{2,6}$");
    private static final Pattern ENGLISH = Pattern.compile("^[A-Za-z]{2,12}$");
    private static final DateTimeFormatter BIRTH = DateTimeFormatter.ofPattern("yyMMdd");

    private Identity() {
    }

    /**
     * 이름이 규칙에 맞으면 null, 아니면 이유를 돌려줍니다.
     * 규칙: 한글 2~6자 또는 영문 2~12자 (띄어쓰기·숫자·특수문자 없음).
     */
    public static String nameProblem(String name) {
        if (name == null || name.isBlank()) {
            return "이름을 적어 주세요.";
        }
        String n = name.strip();
        if (KOREAN.matcher(n).matches() || ENGLISH.matcher(n).matches()) {
            return null;
        }
        return "이름은 한글 2~6자 또는 영문 2~12자로만 지어 주세요. (띄어쓰기·숫자·특수문자 불가)";
    }

    public static String normalizeName(String name) {
        return name == null ? "" : name.strip();
    }

    /**
     * 주민번호: 앞 6자리는 이 도시에 처음 온 날(yyMMdd), 뒤 7자리는 1로 시작하는 고유 순번.
     * 예: 2026년 10월 4일에 1번째로 만든 캐릭터 → 261004-1000001
     */
    public static String citizenId(LocalDate firstDay, long sequence) {
        if (sequence <= 0 || sequence > 8_999_999) {
            throw new IllegalArgumentException("sequence out of range: " + sequence);
        }
        return firstDay.format(BIRTH) + "-" + (1_000_000 + sequence);
    }
}
