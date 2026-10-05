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

    public record RobberySettings(int durationSeconds, long minReward, long maxReward, int cooldownMinutes, double radius,
                                  int minPolice) {
    }

    /** 리소스팩(미니맵·지도 그림) 나눠 주기 */
    public record PackSettings(boolean enabled, int port, String bind, String publicHost, String url, boolean required,
                               String prompt) {
    }

    /** 화면 아래 미니맵 */
    public record RadarSettings(String side, double walkScale, double driveScale, int updateMs, List<String> worlds) {
        /** 이 월드에서 미니맵을 보여 주는지 (목록이 비어 있으면 모든 월드) */
        public boolean shows(String world) {
            return worlds.isEmpty() || worlds.contains(world);
        }
    }

    public record DatabaseSettings(String type, String host, int port, String database, String user, String password,
                                   int poolSize) {
        public boolean mysql() {
            return "mysql".equalsIgnoreCase(type) || "mariadb".equalsIgnoreCase(type);
        }
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

    public final DatabaseSettings database;
    public final String timezone;

    public final RobberySettings storeRobbery;
    public final RobberySettings bankRobbery;

    public final long deliveryBaseReward;
    public final double deliveryRewardPerBlock;
    public final int deliveryRandomMin;
    public final int deliveryRandomMax;
    public final int deliveryFastBonusPercent;

    public final double minerSellBonus;

    public final PackSettings resourcePack;
    public final RadarSettings radar;

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

        database = new DatabaseSettings(
                c.getString("database.type", "sqlite"),
                c.getString("database.mysql.host", "localhost"),
                c.getInt("database.mysql.port", 3306),
                c.getString("database.mysql.database", "junseocity"),
                c.getString("database.mysql.user", "root"),
                c.getString("database.mysql.password", ""),
                Math.max(1, c.getInt("database.mysql.pool-size", 4)));
        timezone = c.getString("timezone", "Asia/Seoul");

        storeRobbery = robbery(c, "robbery.store", 20, 1000, 3000, 5, 6, 2);
        bankRobbery = robbery(c, "robbery.bank", 45, 8000, 15000, 15, 8, 4);

        deliveryBaseReward = c.getLong("delivery.base-reward", 80);
        deliveryRewardPerBlock = c.getDouble("delivery.reward-per-block", 1.2);
        deliveryRandomMin = c.getInt("delivery.random-min-distance", 80);
        deliveryRandomMax = Math.max(deliveryRandomMin + 1, c.getInt("delivery.random-max-distance", 250));
        deliveryFastBonusPercent = c.getInt("delivery.fast-bonus-percent", 30);

        minerSellBonus = c.getDouble("miner-sell-bonus", 1.5);
        readLongs(c.getConfigurationSection("prices"), prices);
        readLongs(c.getConfigurationSection("sell-prices"), sellPrices);

        resourcePack = new PackSettings(
                c.getBoolean("resource-pack.enabled", true),
                c.getInt("resource-pack.port", 8163),
                c.getString("resource-pack.bind", ""),
                c.getString("resource-pack.public-host", ""),
                c.getString("resource-pack.url", ""),
                c.getBoolean("resource-pack.required", false),
                c.getString("resource-pack.prompt", "준서 시티 미니맵·지도 그림이에요. 받아야 미니맵이 보여요."));
        radar = new RadarSettings(
                c.getString("minimap.side", "left"),
                Math.max(1, c.getDouble("minimap.walk-scale", 6)),
                Math.max(1, c.getDouble("minimap.drive-scale", 10)),
                Math.max(50, c.getInt("minimap.update-ms", 150)),
                List.copyOf(c.getStringList("minimap.worlds")));
    }

    private static RobberySettings robbery(FileConfiguration c, String path, int duration, long min, long max,
                                           int cooldown, double radius, int minPolice) {
        long minReward = c.getLong(path + ".min-reward", min);
        return new RobberySettings(
                Math.max(3, c.getInt(path + ".duration-seconds", duration)),
                minReward,
                Math.max(minReward, c.getLong(path + ".max-reward", max)),
                c.getInt(path + ".cooldown-minutes", cooldown),
                c.getDouble(path + ".radius", radius),
                Math.max(0, c.getInt(path + ".min-police", minPolice)));
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
