package com.junseo.city.logic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterDataTest {

    private static long nextId = 1;

    private static CharacterData data(long cash, long bank) {
        CharacterData d = new CharacterData(nextId++, UUID.randomUUID(), "261004-100000" + nextId, "준서", 0L);
        d.setCash(cash);
        d.setBank(bank);
        return d;
    }

    @Test
    void payUsesCashFirst() {
        CharacterData d = data(500, 1000);
        assertEquals(CharacterData.PayResult.CASH, d.pay(300));
        assertEquals(200, d.cash());
        assertEquals(1000, d.bank());
    }

    @Test
    void payFallsBackToBankWhenCashShort() {
        CharacterData d = data(100, 1000);
        assertEquals(CharacterData.PayResult.BANK, d.pay(300));
        assertEquals(100, d.cash());
        assertEquals(700, d.bank());
    }

    @Test
    void payFailsWithoutChangingBalances() {
        CharacterData d = data(100, 100);
        assertEquals(CharacterData.PayResult.INSUFFICIENT, d.pay(150));
        assertEquals(100, d.cash());
        assertEquals(100, d.bank());
    }

    @Test
    void depositAndWithdrawMoveMoney() {
        CharacterData d = data(1000, 0);
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
        CharacterData d = data(1000, 5000);
        assertEquals(200, d.loseCashPercent(20));
        assertEquals(800, d.cash());
        assertEquals(5000, d.bank());
    }

    @Test
    void jailFlagAndJob() {
        CharacterData d = data(0, 0);
        assertFalse(d.isJailed());
        d.setJailSeconds(30);
        assertTrue(d.isJailed());
        d.setJob(Job.POLICE, 2);
        assertEquals(Job.POLICE, d.job());
        assertEquals(2, d.jobGrade());
    }

    @Test
    void bankTransferMovesMoneyBetweenCharacters() {
        CharacterData a = data(0, 10_000);
        CharacterData b = data(0, 0);
        assertTrue(CharacterData.transferBank(a, b, 4_000));
        assertEquals(6_000, a.bank());
        assertEquals(4_000, b.bank());
        assertFalse(CharacterData.transferBank(a, b, 10_000));
        assertFalse(CharacterData.transferBank(a, a, 100));
        assertFalse(CharacterData.transferBank(a, b, 0));
        assertEquals(6_000, a.bank());
    }

    @Test
    void cashHandoverMovesCash() {
        CharacterData a = data(500, 0);
        CharacterData b = data(0, 0);
        assertTrue(CharacterData.transferCash(a, b, 300));
        assertEquals(200, a.cash());
        assertEquals(300, b.cash());
        assertFalse(CharacterData.transferCash(b, a, 301));
    }

    @Test
    void concurrentTransfersKeepTotal() throws InterruptedException {
        CharacterData a = data(0, 1_000_000);
        CharacterData b = data(0, 1_000_000);
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10_000; i++) {
                CharacterData.transferBank(a, b, 7);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10_000; i++) {
                CharacterData.transferBank(b, a, 5);
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertEquals(2_000_000, a.bank() + b.bank());
    }

    @Test
    void snapshotCarriesState() {
        CharacterData d = data(123, 456);
        d.setJob(Job.EMS, 1);
        d.setJailSeconds(9);
        CharacterData.Snapshot s = d.snapshot();
        assertEquals(123, s.cash());
        assertEquals(456, s.bank());
        assertEquals("ems", s.job());
        assertEquals(1, s.jobGrade());
        assertEquals(9, s.jailSeconds());
    }

    @Test
    void changesMarkDirty() {
        CharacterData d = data(0, 0);
        d.markClean();
        assertFalse(d.isDirty());
        d.addCash(10);
        assertTrue(d.isDirty());
    }
}
