package com.junseo.city.logic;

import java.util.UUID;

/** 플레이어 한 명의 저장 데이터 (돈, 직업, 수배, 감옥). */
public final class PlayerData {

    public enum PayResult {
        /** 현금으로 냈어요. */
        CASH,
        /** 현금이 모자라서 은행 카드로 냈어요. */
        BANK,
        /** 돈이 모자라요. */
        INSUFFICIENT
    }

    private final UUID uuid;
    private String name;
    private long cash;
    private long bank;
    private Job job = Job.CITIZEN;
    private int wanted;
    private long wantedChangedAt;
    private int jailSeconds;
    private boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long cash() {
        return cash;
    }

    public long bank() {
        return bank;
    }

    public void setCash(long cash) {
        this.cash = Math.max(0, cash);
        dirty = true;
    }

    public void setBank(long bank) {
        this.bank = Math.max(0, bank);
        dirty = true;
    }

    public void addCash(long amount) {
        setCash(cash + amount);
    }

    public void addBank(long amount) {
        setBank(bank + amount);
    }

    public boolean takeCash(long amount) {
        if (amount < 0 || cash < amount) {
            return false;
        }
        setCash(cash - amount);
        return true;
    }

    public boolean takeBank(long amount) {
        if (amount < 0 || bank < amount) {
            return false;
        }
        setBank(bank - amount);
        return true;
    }

    /** 현금 → 은행. */
    public boolean deposit(long amount) {
        if (amount <= 0 || cash < amount) {
            return false;
        }
        cash -= amount;
        bank += amount;
        dirty = true;
        return true;
    }

    /** 은행 → 현금. */
    public boolean withdraw(long amount) {
        if (amount <= 0 || bank < amount) {
            return false;
        }
        bank -= amount;
        cash += amount;
        dirty = true;
        return true;
    }

    /** 물건값을 냅니다. 현금을 먼저 쓰고, 모자라면 은행 카드로 냅니다. */
    public PayResult pay(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount < 0");
        }
        if (cash >= amount) {
            setCash(cash - amount);
            return PayResult.CASH;
        }
        if (bank >= amount) {
            setBank(bank - amount);
            return PayResult.BANK;
        }
        return PayResult.INSUFFICIENT;
    }

    /** 현금의 percent% 를 잃고, 잃은 금액을 돌려줍니다. */
    public long loseCashPercent(int percent) {
        long lost = cash * Math.max(0, Math.min(100, percent)) / 100;
        setCash(cash - lost);
        return lost;
    }

    public Job job() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
        dirty = true;
    }

    public int wanted() {
        return wanted;
    }

    public long wantedChangedAt() {
        return wantedChangedAt;
    }

    public void setWanted(int wanted, long now) {
        this.wanted = WantedRules.clamp(wanted);
        this.wantedChangedAt = now;
        dirty = true;
    }

    public int jailSeconds() {
        return jailSeconds;
    }

    public void setJailSeconds(int jailSeconds) {
        this.jailSeconds = Math.max(0, jailSeconds);
        dirty = true;
    }

    public boolean isJailed() {
        return jailSeconds > 0;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }
}
