package com.junseo.city.logic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataTest {

    private static PlayerData data(long cash, long bank) {
        PlayerData d = new PlayerData(UUID.randomUUID(), "junseo");
        d.setCash(cash);
        d.setBank(bank);
        return d;
    }

    @Test
    void payUsesCashFirst() {
        PlayerData d = data(500, 1000);
        assertEquals(PlayerData.PayResult.CASH, d.pay(300));
        assertEquals(200, d.cash());
        assertEquals(1000, d.bank());
    }

    @Test
    void payFallsBackToBankWhenCashShort() {
        PlayerData d = data(100, 1000);
        assertEquals(PlayerData.PayResult.BANK, d.pay(300));
        assertEquals(100, d.cash());
        assertEquals(700, d.bank());
    }

    @Test
    void payFailsWithoutChangingBalances() {
        PlayerData d = data(100, 100);
        assertEquals(PlayerData.PayResult.INSUFFICIENT, d.pay(150));
        assertEquals(100, d.cash());
        assertEquals(100, d.bank());
    }

    @Test
    void depositAndWithdrawMoveMoney() {
        PlayerData d = data(1000, 0);
        assertTrue(d.deposit(400));
        assertEquals(600, d.cash());
        assertEquals(400, d.bank());
        assertTrue(d.withdraw(100));
        assertEquals(700, d.cash());
        assertEquals(300, d.bank());
        assertFalse(d.withdraw(1000));
        assertFalse(d.deposit(0));
        assertFalse(d.deposit(-5));
    }

    @Test
    void hospitalFeeTakesPercentOfCash() {
        PlayerData d = data(1000, 5000);
        assertEquals(200, d.loseCashPercent(20));
        assertEquals(800, d.cash());
        assertEquals(5000, d.bank());
    }

    @Test
    void wantedIsClampedAndJailFlag() {
        PlayerData d = data(0, 0);
        d.setWanted(9, 0);
        assertEquals(5, d.wanted());
        d.setWanted(-1, 0);
        assertEquals(0, d.wanted());
        assertFalse(d.isJailed());
        d.setJailSeconds(30);
        assertTrue(d.isJailed());
    }

    @Test
    void changesMarkDirty() {
        PlayerData d = data(0, 0);
        d.markClean();
        assertFalse(d.isDirty());
        d.addCash(10);
        assertTrue(d.isDirty());
    }
}
