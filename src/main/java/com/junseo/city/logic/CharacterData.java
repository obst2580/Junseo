package com.junseo.city.logic;

import java.util.UUID;

/**
 * 캐릭터 한 명의 데이터 (이름, 주민번호, 돈, 직업, 감옥).
 * 여러 스레드(Folia 지역 스레드, DB 스레드)에서 동시에 읽고 쓰므로 모든 메서드가 동기화되어 있습니다.
 */
public final class CharacterData {

    public enum PayResult {
        /** 현금으로 냈어요. */
        CASH,
        /** 현금이 모자라서 은행 카드로 냈어요. */
        BANK,
        /** 돈이 모자라요. */
        INSUFFICIENT
    }

    private final long id;
    private final UUID uuid;
    private final String citizenId;
    private final String name;
    private final long createdAt;
    private long cash;
    private long bank;
    private Job job = Job.CITIZEN;
    private int jobGrade;
    private int jailSeconds;
    private boolean license;
    private boolean dirty;

    public CharacterData(long id, UUID uuid, String citizenId, String name, long createdAt) {
        this.id = id;
        this.uuid = uuid;
        this.citizenId = citizenId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public UUID uuid() {
        return uuid;
    }

    /** 주민번호 (예: 261004-1000001). */
    public String citizenId() {
        return citizenId;
    }

    /** 캐릭터 이름 (만들 때 한 번 정하고 바꿀 수 없음). */
    public String name() {
        return name;
    }

    public long createdAt() {
        return createdAt;
    }

    public synchronized long cash() {
        return cash;
    }

    public synchronized long bank() {
        return bank;
    }

    public synchronized void setCash(long cash) {
        this.cash = Math.max(0, cash);
        dirty = true;
    }

    public synchronized void setBank(long bank) {
        this.bank = Math.max(0, bank);
        dirty = true;
    }

    public synchronized void addCash(long amount) {
        setCash(cash + amount);
    }

    public synchronized void addBank(long amount) {
        setBank(bank + amount);
    }

    public synchronized boolean takeCash(long amount) {
        if (amount < 0 || cash < amount) {
            return false;
        }
        setCash(cash - amount);
        return true;
    }

    public synchronized boolean takeBank(long amount) {
        if (amount < 0 || bank < amount) {
            return false;
        }
        setBank(bank - amount);
        return true;
    }

    /** 현금 → 은행. */
    public synchronized boolean deposit(long amount) {
        if (amount <= 0 || cash < amount) {
            return false;
        }
        cash -= amount;
        bank += amount;
        dirty = true;
        return true;
    }

    /** 은행 → 현금. */
    public synchronized boolean withdraw(long amount) {
        if (amount <= 0 || bank < amount) {
            return false;
        }
        bank -= amount;
        cash += amount;
        dirty = true;
        return true;
    }

    /** 물건값을 냅니다. 현금을 먼저 쓰고, 모자라면 은행 카드로 냅니다. */
    public synchronized PayResult pay(long amount) {
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
    public synchronized long loseCashPercent(int percent) {
        long lost = cash * Math.max(0, Math.min(100, percent)) / 100;
        setCash(cash - lost);
        return lost;
    }

    /**
     * 두 캐릭터 사이 은행 송금. 교착을 피하려고 항상 id 순서대로 잠급니다.
     * @return 성공하면 true
     */
    public static boolean transferBank(CharacterData from, CharacterData to, long amount) {
        if (amount <= 0 || from == to) {
            return false;
        }
        CharacterData first = from.id < to.id ? from : to;
        CharacterData second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (from.bank < amount) {
                    return false;
                }
                from.setBank(from.bank - amount);
                to.setBank(to.bank + amount);
                return true;
            }
        }
    }

    /** 현금 직접 건네기 (같은 규칙으로 잠금). */
    public static boolean transferCash(CharacterData from, CharacterData to, long amount) {
        if (amount <= 0 || from == to) {
            return false;
        }
        CharacterData first = from.id < to.id ? from : to;
        CharacterData second = first == from ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (from.cash < amount) {
                    return false;
                }
                from.setCash(from.cash - amount);
                to.setCash(to.cash + amount);
                return true;
            }
        }
    }

    public synchronized Job job() {
        return job;
    }

    public synchronized int jobGrade() {
        return jobGrade;
    }

    public synchronized void setJob(Job job, int grade) {
        this.job = job;
        this.jobGrade = Math.max(0, grade);
        dirty = true;
    }

    public synchronized int jailSeconds() {
        return jailSeconds;
    }

    public synchronized void setJailSeconds(int jailSeconds) {
        this.jailSeconds = Math.max(0, jailSeconds);
        dirty = true;
    }

    public synchronized boolean isJailed() {
        return jailSeconds > 0;
    }

    /** 운전면허가 있나 */
    public synchronized boolean hasLicense() {
        return license;
    }

    public synchronized void setLicense(boolean license) {
        this.license = license;
        dirty = true;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void markClean() {
        dirty = false;
    }

    /** DB 저장용 스냅샷 (잠금 밖에서 안전하게 쓰기 위해). */
    public synchronized Snapshot snapshot() {
        return new Snapshot(id, cash, bank, job.key(), jobGrade, jailSeconds, license);
    }

    public record Snapshot(long id, long cash, long bank, String job, int jobGrade, int jailSeconds, boolean license) {
    }
}
