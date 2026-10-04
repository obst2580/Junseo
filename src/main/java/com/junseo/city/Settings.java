package com.junseo.city;

import com.junseo.city.logic.Job;
import com.junseo.city.logic.Money;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** config.yml 값을 읽어 둔 것. /시티관리 reload 때 새로 만듭니다. */
public final class Settings {

    public record RobberySettings(int durationSeconds, long minReward, long maxReward, int cooldownMinutes, double radius) {
    }

    public final String serverName;
    public final String currency;
    public final long startingCash;
    public final long startingBank;
    public final boolean keepInventory;
    public final int hospitalFeePercent;
    public final boolean bankAnywhere;
    public final double atmRange;
    public final int paycheckMinutes;
    public final boolean jobChangeAnywhere;

    public final int wantedDecaySeconds;
    public final int glowAt;
    public final int[] policePerStar;
    public final int jailSecondsPerStar;
    public final long arrestRewardPerStar;

    public final RobberySettings storeRobbery;
    public final RobberySettings bankRobbery;

    public final long deliveryBaseReward;
    public final double deliveryRewardPerBlock;
    public final int deliveryRandomMin;
    public final int deliveryRandomMax;
    public final int deliveryFastBonusPercent;

    public final double minerSellBonus;

    private final Map<Job, Long> salaries = new EnumMap<>(Job.class);
    private final Map<String, Long> prices = new HashMap<>();
    private final Map<String, Long> sellPrices = new HashMap<>();

    public Settings(FileConfiguration c) {
        serverName = c.getString("server-name", "준서 시티");
        currency = c.getString("currency", "원");
        startingCash = c.getLong("starting-cash", 500);
        startingBank = c.getLong("starting-bank", 2000);
        keepInventory = c.getBoolean("keep-inventory", true);
        hospitalFeePercent = c.getInt("hospital-fee-percent", 20);
        bankAnywhere = c.getBoolean("bank-anywhere", false);
        atmRange = c.getDouble("atm-range", 5);
        paycheckMinutes = Math.max(1, c.getInt("paycheck.interval-minutes", 10));
        jobChangeAnywhere = c.getBoolean("job-change-anywhere", true);
        for (Job job : Job.values()) {
            salaries.put(job, c.getLong("paycheck.salary." + job.key(), 100));
        }

        wantedDecaySeconds = Math.max(5, c.getInt("wanted.decay-seconds", 60));
        glowAt = c.getInt("wanted.glow-at", 3);
        List<Integer> perStar = c.getIntegerList("wanted.police-per-star");
        policePerStar = perStar.isEmpty() ? new int[]{0, 0, 2, 3, 4, 6} : perStar.stream().mapToInt(Integer::intValue).toArray();
        jailSecondsPerStar = Math.max(5, c.getInt("wanted.jail-seconds-per-star", 30));
        arrestRewardPerStar = c.getLong("wanted.arrest-reward-per-star", 300);

        storeRobbery = robbery(c, "robbery.store", 20, 1000, 3000, 5, 6);
        bankRobbery = robbery(c, "robbery.bank", 45, 8000, 15000, 15, 8);

        deliveryBaseReward = c.getLong("delivery.base-reward", 80);
        deliveryRewardPerBlock = c.getDouble("delivery.reward-per-block", 1.2);
        deliveryRandomMin = c.getInt("delivery.random-min-distance", 80);
        deliveryRandomMax = Math.max(deliveryRandomMin + 1, c.getInt("delivery.random-max-distance", 250));
        deliveryFastBonusPercent = c.getInt("delivery.fast-bonus-percent", 30);

        minerSellBonus = c.getDouble("miner-sell-bonus", 1.5);
        readLongs(c.getConfigurationSection("prices"), prices);
        readLongs(c.getConfigurationSection("sell-prices"), sellPrices);
    }

    private static RobberySettings robbery(FileConfiguration c, String path, int duration, long min, long max,
                                           int cooldown, double radius) {
        long minReward = c.getLong(path + ".min-reward", min);
        return new RobberySettings(
                Math.max(3, c.getInt(path + ".duration-seconds", duration)),
                minReward,
                Math.max(minReward, c.getLong(path + ".max-reward", max)),
                c.getInt(path + ".cooldown-minutes", cooldown),
                c.getDouble(path + ".radius", radius));
    }

    private static void readLongs(ConfigurationSection section, Map<String, Long> into) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            into.put(key, section.getLong(key));
        }
    }

    public long salary(Job job) {
        return salaries.getOrDefault(job, 100L);
    }

    /** 상점 구매가. 설정에 없으면 fallback. */
    public long price(String id, long fallback) {
        return prices.getOrDefault(id, fallback);
    }

    /** 광물 판매가. 설정에 없으면 0 (안 삼). */
    public Map<String, Long> sellPrices() {
        return sellPrices;
    }

    public String money(long amount) {
        return Money.format(amount, currency);
    }
}
