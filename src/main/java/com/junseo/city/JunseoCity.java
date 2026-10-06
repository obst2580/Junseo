package com.junseo.city;

import com.junseo.city.character.CreationService;
import com.junseo.city.command.AdminCommand;
import com.junseo.city.crime.JailService;
import com.junseo.city.crime.RobberyService;
import com.junseo.city.data.CharacterRepository;
import com.junseo.city.data.CharacterService;
import com.junseo.city.data.Database;
import com.junseo.city.economy.Economy;
import com.junseo.city.hud.ActionBarHud;
import com.junseo.city.hud.HudService;
import com.junseo.city.job.DeliveryService;
import com.junseo.city.job.JobService;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.map.MapService;
import com.junseo.city.menu.MenuListener;
import com.junseo.city.npc.NpcService;
import com.junseo.city.pack.ResourcePackService;
import com.junseo.city.phone.DispatchService;
import com.junseo.city.phone.GpsService;
import com.junseo.city.phone.InteractionService;
import com.junseo.city.phone.PhoneService;
import com.junseo.city.place.PlaceRegistry;
import com.junseo.city.place.PlaceType;
import com.junseo.city.ui.UiService;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import com.junseo.city.vehicle.CarService;
import com.junseo.city.vehicle.GarageService;
import com.junseo.city.vehicle.VehicleService;
import com.junseo.city.weapon.GunService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 준서 시티 플러그인 시작점. 모든 기능(서비스)을 만들고 연결합니다.
 * Folia 호환: 메인 스레드를 가정하지 않고, 플레이어 일은 그 플레이어의 스레드에서 처리합니다.
 */
public final class JunseoCity extends JavaPlugin {
    private Settings settings;
    private Database database;
    private CharacterService characters;
    private PlaceRegistry places;
    private UiService ui;
    private Economy economy;
    private HudService hud;
    private NpcService npcs;
    private JobService jobs;
    private DeliveryService delivery;
    private DispatchService dispatch;
    private GpsService gps;
    private JailService jail;
    private RobberyService robbery;
    private GunService guns;
    private CarService cars;
    private VehicleService vehicles;
    private GarageService garages;
    private com.junseo.city.vehicle.GarageApp garageApp;
    private com.junseo.city.vehicle.DealerApp dealer;
    private com.junseo.city.vehicle.LicenseOffice licenseOffice;
    private PhoneService phone;
    private CreationService creation;
    private ActionBarHud actionBar;
    private ResourcePackService pack;
    private MapService map;
    private PlayerListener playerListener;
    private ScheduledTask paydayTask;
    private final Map<UUID, ScheduledTask> tickers = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);
        Sched.init(this);
        settings = new Settings(getConfig());

        database = openDatabase();
        database.migrate();
        characters = new CharacterService(this, database, new CharacterRepository(ZoneId.of(settings.timezone)));
        places = new PlaceRegistry(this);
        ui = new UiService(this);
        economy = new Economy(this);
        hud = new HudService(this);
        actionBar = new ActionBarHud();
        npcs = new NpcService(this);
        jobs = new JobService(this);
        gps = new GpsService(actionBar);
        dispatch = new DispatchService(this);
        delivery = new DeliveryService(this);
        jail = new JailService(this);
        robbery = new RobberyService(this);
        guns = new GunService(this);
        vehicles = new VehicleService(this, database);
        vehicles.load();
        garages = new GarageService(this);
        garageApp = new com.junseo.city.vehicle.GarageApp(this);
        dealer = new com.junseo.city.vehicle.DealerApp(this);
        licenseOffice = new com.junseo.city.vehicle.LicenseOffice(this);
        cars = new CarService(this);
        phone = new PhoneService(this);
        creation = new CreationService(this);
        pack = new ResourcePackService(this);
        map = new MapService(this, actionBar);
        playerListener = new PlayerListener(this);

        register(new MenuListener(), characters, ui, economy, npcs, robbery, guns, cars, phone,
                new InteractionService(this), creation, playerListener, pack, map);
        pack.start();
        map.start();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("cityadmin", "시티 관리자 명령어", List.of("시티관리"), new AdminCommand(this)));

        Sched.globalRepeat(600, 600, t -> characters.saveDirty());
        schedulePayday();

        // /reload 등으로 다시 켜졌을 때 이미 접속한 사람들 처리
        for (Player player : getServer().getOnlinePlayers()) {
            Sched.entity(player, () -> playerListener.handleJoin(player));
        }
        getLogger().info(settings.serverName + " 준비 완료! (DB: " + database.dialect() + ", 장소 " + places.all().size() + "곳)");
    }

    private Database openDatabase() {
        Settings.DatabaseSettings db = settings.database;
        if (db.mysql()) {
            getLogger().info("MySQL/MariaDB 에 연결합니다: " + db.host() + ":" + db.port() + "/" + db.database());
            return Database.mysql(db.host(), db.port(), db.database(), db.user(), db.password(), db.poolSize(), getLogger());
        }
        File file = new File(getDataFolder(), "city.db");
        return Database.sqlite(file.getAbsolutePath(), getLogger());
    }

    @Override
    public void onDisable() {
        if (pack != null) {
            pack.stop();
        }
        if (guns != null) {
            guns.writeBackAll();
        }
        if (cars != null) {
            cars.removeAll();
        }
        if (delivery != null) {
            delivery.cancelAll();
        }
        if (characters != null) {
            characters.saveAllBlocking();
        }
        if (places != null) {
            places.save();
        }
        if (database != null) {
            database.close();
        }
    }

    /** 캐릭터가 준비됐을 때 (접속 또는 방금 생성): 화면 표시와 개인 타이머 시작. 플레이어 스레드에서 호출. */
    public void onCharacterReady(Player player, CharacterData data) {
        player.displayName(Text.mm("<white>" + Text.esc(data.name())));
        player.playerListName(Text.mm("<white>" + Text.esc(data.name())));
        hud.show(player);
        jail.onJoin(player);
        map.startHud(player);
        pack.greet(player);
        int[] count = {0};
        ScheduledTask task = Sched.entityRepeat(player, 10, 10, t -> {
            delivery.tick(player);
            gps.tick(player);
            if (++count[0] % 2 == 0) {
                jail.tickSecond(player);
                robbery.tickSecond(player);
                hud.update(player);
            }
        });
        ScheduledTask old = task == null ? null : tickers.put(player.getUniqueId(), task);
        if (old != null) {
            old.cancel();
        }
    }

    /** 나갈 때: 개인 타이머 정리. */
    public void forgetTicker(UUID uuid) {
        ScheduledTask task = tickers.remove(uuid);
        if (task != null) {
            task.cancel();
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
        paydayTask = Sched.globalRepeat(period, period, t -> jobs.payday());
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    /** 시티 스폰 → 월드 스폰 순서로 찾은 기본 위치. */
    public Location spawnLocation(Player player) {
        Location spawn = places.location(PlaceType.SPAWN);
        return spawn != null ? spawn : player.getWorld().getSpawnLocation();
    }

    public Settings settings() {
        return settings;
    }

    public CharacterService characters() {
        return characters;
    }

    public PlaceRegistry places() {
        return places;
    }

    public UiService ui() {
        return ui;
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

    public DispatchService dispatch() {
        return dispatch;
    }

    public GpsService gps() {
        return gps;
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

    public VehicleService vehicles() {
        return vehicles;
    }

    public GarageService garages() {
        return garages;
    }

    public com.junseo.city.vehicle.GarageApp garageApp() {
        return garageApp;
    }

    public com.junseo.city.vehicle.DealerApp dealer() {
        return dealer;
    }

    public com.junseo.city.vehicle.LicenseOffice licenseOffice() {
        return licenseOffice;
    }

    public CarService cars() {
        return cars;
    }

    public PhoneService phone() {
        return phone;
    }

    /** 화면 아래 한 줄 (미니맵 + 알림). player.sendActionBar 대신 이것을 씁니다 */
    public ActionBarHud actionBar() {
        return actionBar;
    }

    public ResourcePackService pack() {
        return pack;
    }

    public MapService map() {
        return map;
    }

    public CreationService creation() {
        return creation;
    }
}
