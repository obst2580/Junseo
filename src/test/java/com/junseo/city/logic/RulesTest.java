package com.junseo.city.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {

    @Test
    void wantedStarsAddAndCap() {
        assertEquals(2, WantedRules.add(0, 2));
        assertEquals(5, WantedRules.add(4, 3));
        assertEquals("★★☆☆☆", WantedRules.stars(2));
        assertEquals("☆☆☆☆☆", WantedRules.stars(0));
    }

    @Test
    void wantedDecaysOnlyAfterDelay() {
        assertFalse(WantedRules.shouldDecay(3, 59_000, 60_000));
        assertTrue(WantedRules.shouldDecay(3, 60_000, 60_000));
        assertFalse(WantedRules.shouldDecay(0, 999_999, 60_000));
    }

    @Test
    void policeCountFollowsConfigList() {
        int[] perStar = {0, 0, 2, 3, 4, 6};
        assertEquals(0, WantedRules.policeCount(1, perStar));
        assertEquals(2, WantedRules.policeCount(2, perStar));
        assertEquals(6, WantedRules.policeCount(5, perStar));
        assertEquals(3, WantedRules.policeCount(5, new int[]{0, 1, 3}));
        assertEquals(0, WantedRules.policeCount(3, new int[0]));
    }

    @Test
    void jailTimeScalesWithStars() {
        assertEquals(90, WantedRules.jailSeconds(3, 30));
        assertEquals(30, WantedRules.jailSeconds(0, 30));
    }

    @Test
    void moneyParsesKoreanUnits() {
        assertEquals(1000, Money.parse("1000"));
        assertEquals(1000, Money.parse("1,000"));
        assertEquals(10_000, Money.parse("1만"));
        assertEquals(25_000, Money.parse("2.5만"));
        assertEquals(3000, Money.parse("3천"));
        assertEquals(500, Money.parse("500원"));
        assertEquals(-1, Money.parse("abc"));
        assertEquals(-1, Money.parse("0"));
        assertEquals(-1, Money.parse("-5"));
    }

    @Test
    void moneyFormat() {
        assertEquals("1,234,567원", Money.format(1_234_567, "원"));
    }

    @Test
    void jobParseAcceptsKoreanAndEnglish() {
        assertEquals(Job.POLICE, Job.parse("police"));
        assertEquals(Job.POLICE, Job.parse("경찰"));
        assertEquals(Job.DELIVERY, Job.parse("DELIVERY"));
        assertNull(Job.parse("astronaut"));
    }
}
