package com.junseo.city;

import com.junseo.city.command.AdminCommand;
import com.junseo.city.command.PlayerCommands;
import com.junseo.city.crime.CrimeListener;
import com.junseo.city.crime.JailService;
import com.junseo.city.crime.PoliceForce;
import com.junseo.city.crime.RobberyService;
import com.junseo.city.crime.WantedService;
import com.junseo.city.data.PlayerDataStore;
import com.junseo.city.economy.Economy;
import com.junseo.city.economy.EconomyCommands;
import com.junseo.city.hud.HudService;
import com.junseo.city.job.DeliveryService;
import com.junseo.city.job.JobService;
import com.junseo.city.menu.MenuListener;
import com.junseo.city.npc.NpcService;
import com.junseo.city.place.PlaceRegistry;
import com.junseo.city.place.PlaceType;
import com.junseo.city.util.Keys;
import com.junseo.city.vehicle.CarService;
import com.junseo.city.weapon.GunService;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** 준서 시티 플러그인 시작점. 모든 기능(서비스)을 만들고 연결합니다. */
public final class JunseoCity extends JavaPlugin {
    private Settings settings;
    private PlayerDataStore data;
    private PlaceRegistry places;
    private Economy economy;
    private HudService hud;
    private NpcService npcs;
    private JobService jobs;
    private DeliveryService delivery;
    private WantedService wanted;
    private PoliceForce police;
    private JailService jail;
    private RobberyService robbery;
    private GunService guns;
    private CarService cars;
    private PlayerListener playerListener;
    private BukkitTask paydayTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);
        settings = new Settings(getConfig());
        data = new PlayerDataStore(this);
        places = new PlaceRegistry(this);
        economy = new Economy(this);
        hud = new HudService(this);
        npcs = new NpcService(this);
        jobs = new JobService(this);
        delivery = new DeliveryService(this);
        wanted = new WantedService(this);
        police = new PoliceForce(this);
        jail = new JailService(this);
        robbery = new RobberyService(this);
        guns = new GunService(this);
        cars = new CarService(this);
        playerListener = new PlayerListener(this);

        register(new MenuListener(), economy, npcs, police, robbery, guns, cars, playerListener, new CrimeListener(this));

        EconomyCommands economyCommands = new EconomyCommands(this);
        command("money", economyCommands);
        command("pay", economyCommands);
        command("bank", economyCommands);
        PlayerCommands playerCommands = new PlayerCommands(this);
        for (String name : new String[]{"job", "delivery", "wanted", "call112", "car", "cityhelp"}) {
            command(name, playerCommands);
        }
        command("cityadmin", new AdminCommand(this));

        var scheduler = getServer().getScheduler();
        scheduler.runTaskTimer(this, () -> cars.tick(), 1L, 1L);
        scheduler.runTaskTimer(this, () -> delivery.tick(), 10L, 10L);
        scheduler.runTaskTimer(this, () -> {
            wanted.tickSecond();
            jail.tickSecond();
            robbery.tickSecond();
            hud.updateAll();
        }, 20L, 20L);
        scheduler.runTaskTimer(this, () -> data.saveDirty(), 6000L, 6000L);
        schedulePayday();

        // /reload 등으로 다시 켜졌을 때 이미 접속한 사람들 처리
        for (Player player : getServer().getOnlinePlayers()) {
            playerListener.handleJoin(player);
        }
        getLogger().info(settings.serverName + " 준비 완료! 장소 " + places.all().size() + "곳");
    }

    @Override
    public void onDisable() {
        if (guns != null) {
            guns.writeBackAll();
        }
        if (cars != null) {
            cars.removeAll();
        }
        if (police != null) {
            police.removeAll();
        }
        if (delivery != null) {
            delivery.cancelAll();
        }
        if (robbery != null) {
            robbery.cancelAll();
        }
        if (data != null) {
            data.saveAll();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = new Settings(getConfig());
        schedulePayday();
    }

    private void schedulePayday() {
        if (paydayTask != null) {
            paydayTask.cancel();
        }
        long period = settings.paycheckMinutes * 60L * 20L;
        paydayTask = getServer().getScheduler().runTaskTimer(this, () -> jobs.payday(), period, period);
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void command(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("plugin.yml 에 명령어가 없어요: " + name);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** 시티 스폰 → 월드 스폰 순서로 찾은 기본 위치. */
    public Location spawnLocation(Player player) {
        Location spawn = places.location(PlaceType.SPAWN);
        return spawn != null ? spawn : player.getWorld().getSpawnLocation();
    }

    public Settings settings() {
        return settings;
    }

    public PlayerDataStore data() {
        return data;
    }

    public PlaceRegistry places() {
        return places;
    }

    public Economy economy() {
        return economy;
    }

    public HudService hud() {
        return hud;
    }

    public NpcService npcs() {
        return npcs;
    }

    public JobService jobs() {
        return jobs;
    }

    public DeliveryService delivery() {
        return delivery;
    }

    public WantedService wanted() {
        return wanted;
    }

    public PoliceForce police() {
        return police;
    }

    public JailService jail() {
        return jail;
    }

    public RobberyService robbery() {
        return robbery;
    }

    public GunService guns() {
        return guns;
    }

    public CarService cars() {
        return cars;
    }
}
