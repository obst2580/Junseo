package com.junseo.city.logic;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RulesTest {

    @Test
    void characterNameRules() {
        assertNull(Identity.nameProblem("김준서"));
        assertNull(Identity.nameProblem("준서"));
        assertNull(Identity.nameProblem("Junseo"));
        assertNotNull(Identity.nameProblem("김"));
        assertNotNull(Identity.nameProblem("김 준서"));
        assertNotNull(Identity.nameProblem("준서123"));
        assertNotNull(Identity.nameProblem("가나다라마바사"));
        assertNotNull(Identity.nameProblem("Jun<b>"));
        assertNotNull(Identity.nameProblem(""));
        assertNotNull(Identity.nameProblem(null));
    }

    @Test
    void citizenIdFormat() {
        assertEquals("261004-1000001", Identity.citizenId(LocalDate.of(2026, 10, 4), 1));
        assertEquals("270101-1012345", Identity.citizenId(LocalDate.of(2027, 1, 1), 12_345));
        assertThrows(IllegalArgumentException.class, () -> Identity.citizenId(LocalDate.of(2026, 1, 1), 0));
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
        assertEquals(Job.EMS, Job.parse("의료국"));
        assertTrue(Job.EMS.government());
        assertFalse(Job.DELIVERY.government());
        assertEquals(Job.DELIVERY, Job.parse("DELIVERY"));
        assertNull(Job.parse("astronaut"));
    }
}
