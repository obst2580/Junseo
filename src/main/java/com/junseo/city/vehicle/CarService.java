package com.junseo.city.vehicle;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CarPhysics;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.ui.Screen;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import com.junseo.city.vehicle.model.CarDesign;
import com.junseo.city.vehicle.model.CarModels;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자동차 소환·운전·내리기·손상·조수석·경찰 단속.
 * 운전: W/S 가속·후진, A/D 핸들, 스페이스 브레이크, Ctrl(달리기) 경적/사이렌, Shift 내리기.
 * <ul>
 *   <li>플레이어 차 ({@link VehicleRecord}): 공영 차고에서 폰으로 꺼내고 넣음. 주인만 운전 (열쇠 필요 없음)</li>
 *   <li>직업 차 (경찰차·구급차·택배 트럭 등): 출근하면 받는 열쇠로 땅을 우클릭해 꺼냄</li>
 *   <li>손상: 부딪치면 차 상태가 줄고, 0 이 되면 시동이 꺼짐 (폰 차고 앱에서 견인·수리)</li>
 *   <li>조수석: 운전자가 있거나 운전할 수 있는 차에 한 명 더 탐 (택시 손님)</li>
 *   <li>경찰: 웅크리고 차를 우클릭하면 차량 조회·견인(압류)</li>
 *   <li>아무도 안 탄 채 20분이 지나면 차고로 돌아감 (직업 차는 사라짐)</li>
 * </ul>
 */
public final class CarService implements Listener {
    /** 운전석 높이를 낮추려고 갑옷 거치대를 이만큼 줄입니다. */
    private static final double BASE_SCALE = 0.25;
    /** 갑옷 거치대 원래 키. 크기 속성은 다음 틱에야 몸 크기에 반영돼서 getHeight() 를 바로 믿을 수 없음 */
    private static final double ARMOR_STAND_HEIGHT = 1.975;
    private static final double DRIVER_CAMERA_DISTANCE = 6.0;
    /** 3D 모델 차를 실제 크기의 몇 배로 그릴지 (마인크래프트 사람 키에 맞춰 조금 키움) */
    static final float MODEL_SCALE = 1.2f;
    /** 아이템 표시 엔티티는 모델을 y 축으로 180° 돌려 그리므로 되돌림 */
    private static final Quaternionf ITEM_FLIP = new Quaternionf().rotationY((float) Math.PI);
    private static final float PLATE_TEXT_SCALE = 0.42f;
    private static final float MAX_STEER = 28f;
    private static final String PLATE_LETTERS = "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주";
    /** 아무도 안 탄 채 이만큼 지나면 차고로 (20분) */
    private static final long IDLE_LIMIT = 20L * 60 * 20;
    /** 조수석: 운전석 오른쪽 뒤 (차 좌표, 미터) */
    private static final double SEAT_X = -0.42, SEAT_Z = -0.55;

    private final JunseoCity plugin;
    private final Map<UUID, Car> byEntity = new ConcurrentHashMap<>();
    private final Map<UUID, Car> byKey = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRunOver = new ConcurrentHashMap<>();

    public CarService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ 소환 / 제거

    /** 플레이어 차를 꺼냄 (차고 주차 칸에서) */
    public Car spawnOwned(VehicleRecord r, Location at) {
        Car car = spawn(r.type(), r.paint(), r.owner(), r.id(), r, r.plate(), at);
        r.setState(VehicleRecord.State.OUT, null);
        plugin.vehicles().save(r);
        return car;
    }

    /** 직업 차 (열쇠로 꺼냄) */
    public Car spawnJob(CarType type, UUID owner, UUID keyId, Location at) {
        return spawn(type, CarModels.Paint.WHITE, owner, keyId, null, plateNumber(keyId), at);
    }

    private Car spawn(CarType type, CarModels.Paint paint, UUID owner, UUID keyId, VehicleRecord record, String plate, Location at) {
        Car old = byKey.remove(keyId);
        if (old != null) {
            despawn(old);
        }
        World world = at.getWorld();
        float yaw = at.getYaw();
        Location loc = at.clone();
        loc.setPitch(0);
        ArmorStand base = world.spawn(loc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setGravity(true);
            stand.setCanMove(true);
            stand.setCanTick(true);
            stand.setPersistent(false);
            stand.setDisabledSlots(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
            setAttribute(stand.getAttribute(Attribute.SCALE), BASE_SCALE);
            setAttribute(stand.getAttribute(Attribute.STEP_HEIGHT), 1.05);
            tag(stand);
        });
        // 부품이 타는 자리(받침 머리 위) 높이. 다음 틱에 실제 위치를 재서 다르면 고침 (calibrate)
        float attachY = (float) (ARMOR_STAND_HEIGHT * BASE_SCALE);
        CarDesign design = type.design();
        List<Display> displays = new ArrayList<>();
        List<ItemDisplay> wheels = new ArrayList<>();
        // 리소스팩 3D 모델: 차체 하나 + 바퀴 넷 + 앞뒤 번호판 글자
        displays.add(itemDisplay(world, loc, yaw, CarModels.bodyModel(type.modelId(), paint), bodyTransform(design, attachY), true));
        for (int i = 0; i < 4; i++) {
            ItemDisplay w = itemDisplay(world, loc, yaw, CarModels.wheelModel(type.modelId()), wheelTransform(design, i, attachY, 0, 0), false);
            wheels.add(w);
            displays.add(w);
        }
        displays.add(plateDisplay(world, loc, yaw, design, plate, attachY, true));
        displays.add(plateDisplay(world, loc, yaw, design, plate, attachY, false));
        for (Display d : displays) {
            base.addPassenger(d);
        }
        // 클릭 판정도 차에 태워서 매 틱 따로 옮기지 않아도 되게 (Folia 에서 순간이동은 비싸요)
        Interaction hitbox = world.spawn(loc, Interaction.class, i -> {
            i.setInteractionWidth((float) (Math.max(design.width(), 1.6) * MODEL_SCALE + 0.3));
            i.setInteractionHeight((float) (design.height() * MODEL_SCALE));
            i.setResponsive(true);
            i.setPersistent(false);
            tag(i);
        });
        base.addPassenger(hitbox);
        Car car = new Car(type, paint, owner, keyId, record, base, hitbox, displays, wheels, plate, yaw);
        car.attachY = attachY;
        byKey.put(keyId, car);
        byEntity.put(base.getUniqueId(), car);
        byEntity.put(hitbox.getUniqueId(), car);
        displays.forEach(d -> byEntity.put(d.getUniqueId(), car));
        car.task = Sched.entityRepeat(base, 1, 1, t -> {
            if (car.removed || !car.base.isValid()) {
                t.cancel();
                cleanup(car);
                return;
            }
            drive(car);
        });
        Sched.entityLater(base, 3, () -> calibrate(car));
        world.playSound(loc, Sound.BLOCK_PISTON_EXTEND, 1f, 0.6f);
        return car;
    }

    /**
     * 부품이 실제로 탄 높이를 재서, 처음에 잡은 값과 다르면 모든 부품을 그만큼 올리거나 내림
     * (차가 땅에 묻히거나 뜨지 않게). 서버가 계산한 탑승 위치는 클라이언트와 같습니다.
     */
    private void calibrate(Car car) {
        if (car.removed || car.parts.isEmpty() || !car.base.isValid()) {
            return;
        }
        double measured = car.parts.get(0).getLocation().getY() - car.base.getLocation().getY();
        float delta = (float) measured - car.attachY;
        if (Math.abs(delta) < 0.01 || measured < -0.5 || measured > 3) {
            return;
        }
        plugin.getLogger().info(String.format("차 부품 높이 보정: %.3f → %.3f", car.attachY, measured));
        car.attachY = (float) measured;
        for (Display d : car.parts) {
            Transformation t = d.getTransformation();
            Vector3f tr = new Vector3f(t.getTranslation()).sub(0, delta, 0);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(0);
            d.setTransformation(new Transformation(tr, t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        }
    }

    /** 3D 모델 한 조각 (아이템 표시 엔티티) */
    private static ItemDisplay itemDisplay(World world, Location loc, float yaw, String model, Transformation tf, boolean shadow) {
        return world.spawn(loc, ItemDisplay.class, d -> {
            ItemStack stack = new ItemStack(Material.PAPER);
            ItemMeta meta = stack.getItemMeta();
            meta.setItemModel(NamespacedKey.fromString(model));
            stack.setItemMeta(meta);
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(tf);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(1);
            d.setShadowRadius(shadow ? 1.4f : 0f);
            d.setShadowStrength(0.75f);
            d.setPersistent(false);
            d.setRotation(yaw, 0);
            tag(d);
        });
    }

    /** 모델 1 칸 = 1/unit m 라서 16/unit 배 × MODEL_SCALE */
    private static float modelScale(CarDesign d) {
        return (float) (16 / d.unit()) * MODEL_SCALE;
    }

    /** 차체: 모델 (8, 0, 8) 이 차 바닥 가운데 */
    private static Transformation bodyTransform(CarDesign d, float attachY) {
        float k = modelScale(d);
        return new Transformation(new Vector3f(0, k / 2 - attachY, 0), new Quaternionf(), new Vector3f(k), ITEM_FLIP);
    }

    /** 바퀴 i (0 앞왼, 1 앞오, 2 뒤왼, 3 뒤오): 모델 가운데가 바퀴 중심. 굴림(spin, 라디안)·꺾임(steer, 도) */
    private static Transformation wheelTransform(CarDesign d, int i, float attachY, double spin, float steer) {
        boolean front = i < 2;
        float x = (i % 2 == 0 ? 1 : -1) * (float) d.track() / 2 * MODEL_SCALE;
        float y = (float) d.wheelRadius() * MODEL_SCALE - attachY;
        float z = (float) (front ? d.frontAxle() : d.rearAxle()) * MODEL_SCALE;
        Quaternionf rot = new Quaternionf().rotationY((float) Math.toRadians(front ? steer : 0)).rotateX((float) spin);
        return new Transformation(new Vector3f(x, y, z), rot, new Vector3f(modelScale(d)), ITEM_FLIP);
    }

    /** 번호판 글자 (검은 글씨, 배경 없음). 뒤 번호판은 뒤를 보게 돌림 */
    private static TextDisplay plateDisplay(World world, Location loc, float yaw, CarDesign d, String plate, float attachY, boolean front) {
        double[] p = d.plates();
        float y = (float) ((front ? p[0] : p[2]) * MODEL_SCALE) - attachY - 0.05f;
        float z = (float) ((front ? p[1] + 0.008 : p[3] - 0.008) * MODEL_SCALE);
        return world.spawn(loc, TextDisplay.class, t -> {
            t.text(Component.text(plate, NamedTextColor.BLACK));
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(false);
            t.setSeeThrough(false);
            t.setLineWidth(400);
            t.setTeleportDuration(2);
            t.setPersistent(false);
            t.setRotation(yaw, 0);
            Quaternionf rot = front ? new Quaternionf() : new Quaternionf().rotationY((float) Math.PI);
            t.setTransformation(new Transformation(new Vector3f(0, y, z), rot, new Vector3f(PLATE_TEXT_SCALE), new Quaternionf()));
            tag(t);
        });
    }

    /** 직업 차 번호판: 열쇠마다 정해짐 (예: 12가 3456) */
    static String plateNumber(UUID keyId) {
        long h = (keyId.getMostSignificantBits() * 31 ^ keyId.getLeastSignificantBits()) & Long.MAX_VALUE;
        int a = (int) (h % 90) + 10;
        h /= 90;
        char c = PLATE_LETTERS.charAt((int) (h % PLATE_LETTERS.length()));
        h /= PLATE_LETTERS.length();
        int b = (int) (h % 9000) + 1000;
        return a + "" + c + " " + b;
    }

    private static void setAttribute(AttributeInstance attribute, double value) {
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }

    private static void tag(Entity entity) {
        entity.getPersistentDataContainer().set(Keys.CAR, PersistentDataType.BYTE, (byte) 1);
    }

    /** 어느 스레드에서 불러도 됩니다: 실제 제거는 차가 있는 지역 스레드에서. */
    public void despawn(Car car) {
        car.removed = true;
        byKey.remove(car.keyId, car);
        Sched.entity(car.base, () -> removeEntities(car));
    }

    /** 플레이어 차를 차고에 넣음 (상태와 차 상태를 저장) */
    public void store(Car car, String garage) {
        if (car.record != null) {
            car.record.setHealth(car.health());
            car.record.setState(VehicleRecord.State.GARAGE, garage);
            plugin.vehicles().save(car.record);
        }
        despawn(car);
    }

    /** 견인: 길에 있는 차를 마지막 차고로 (차 상태는 그대로) */
    public void tow(VehicleRecord r) {
        Car car = byKey.get(r.id());
        if (car != null) {
            store(car, null);
        } else {
            r.setState(VehicleRecord.State.GARAGE, null);
            plugin.vehicles().save(r);
        }
    }

    /** 경찰 견인(압류): 견인소로 */
    public void impound(Car car) {
        if (car.record != null) {
            car.record.setHealth(car.health());
            car.record.setState(VehicleRecord.State.IMPOUND, null);
            plugin.vehicles().save(car.record);
        }
        despawn(car);
    }

    public Car byRecord(VehicleRecord r) {
        Car car = byKey.get(r.id());
        return car == null || car.removed ? null : car;
    }

    /** 길에 나와 있는 모든 차의 최근 위치 (빈 주차 칸 찾기용) */
    public List<Location> carLocations() {
        List<Location> out = new ArrayList<>();
        for (Car car : byKey.values()) {
            Location l = car.lastKnownLocation();
            if (l != null) {
                out.add(l);
            }
        }
        return out;
    }

    /** 차 스레드에서: 엔티티 제거. */
    private void removeEntities(Car car) {
        Player driver = car.driver();
        if (driver != null) {
            driver.leaveVehicle();
        }
        if (car.seat != null) {
            Player p = car.passenger();
            if (p != null) {
                p.leaveVehicle();
            }
            car.seat.remove();
        }
        for (Display part : car.parts) {
            part.remove();
        }
        car.hitbox.remove();
        car.base.remove();
        cleanup(car);
    }

    private void cleanup(Car car) {
        car.removed = true;
        byKey.remove(car.keyId, car);
        byEntity.remove(car.base.getUniqueId());
        byEntity.remove(car.hitbox.getUniqueId());
        if (car.seat != null) {
            byEntity.remove(car.seat.getUniqueId());
        }
        for (Display part : car.parts) {
            byEntity.remove(part.getUniqueId());
        }
        if (car.task != null) {
            car.task.cancel();
        }
    }

    /** 길에 나와 있는 이 사람의 직업 차 (열쇠로 꺼낸 차) */
    public List<Car> ownedBy(UUID owner) {
        List<Car> out = new ArrayList<>();
        for (Car car : byKey.values()) {
            if (!car.removed && car.owner.equals(owner)) {
                out.add(car);
            }
        }
        return out;
    }

    public void despawnOwned(UUID owner, CarType type) {
        for (Car car : ownedBy(owner)) {
            if (type == null || car.type == type) {
                despawn(car);
            }
        }
    }

    public int countOwned(UUID owner) {
        return ownedBy(owner).size();
    }

    /** 서버 종료 때. 플레이어 차는 차고로 돌려놓습니다. */
    public void removeAll() {
        for (Car car : new ArrayList<>(byKey.values())) {
            if (car.record != null) {
                car.record.setHealth(car.health());
                car.record.setState(VehicleRecord.State.GARAGE, null);
                plugin.vehicles().save(car.record);
            }
            try {
                removeEntities(car);
            } catch (RuntimeException e) {
                cleanup(car);
            }
        }
    }

    public boolean isCarPart(Entity entity) {
        return byEntity.containsKey(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(Keys.CAR, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------------ 운전 (매 틱)

    private void drive(Car car) {
        long now = ++car.ticks;
        CarType type = car.type;
        Player driver = car.driver();
        double speed = car.speed;
        float yaw = car.yaw;
        float steerTarget = 0;
        boolean dead = car.health <= 0;
        if (driver != null) {
            var input = driver.getCurrentInput();
            steerTarget = (input.isLeft() ? MAX_STEER : 0) - (input.isRight() ? MAX_STEER : 0);
            speed = CarPhysics.nextSpeed(speed, input.isForward() && !dead, input.isBackward() && !dead, input.isJump(),
                    type.maxSpeed(), type.acceleration());
            yaw = CarPhysics.nextYaw(yaw, input.isLeft(), input.isRight(), speed, type.maxSpeed(), type.turnDegrees());
            if (input.isSprint() && !car.prevSprint) {
                honk(car);
            }
            car.prevSprint = input.isSprint();
            if (now % 4 == 0) {
                hud(car, driver, speed, now);
            }
        } else {
            speed = CarPhysics.nextSpeed(speed, false, false, true, type.maxSpeed(), type.acceleration());
        }

        Location loc = car.base.getLocation();
        if (car.crashCooldown > 0) {
            car.crashCooldown--;
        }
        if (car.lastLocation != null && car.lastLocation.getWorld() == loc.getWorld() && Math.abs(car.speed) > 0.25
                && car.crashCooldown == 0) {
            double dx = loc.getX() - car.lastLocation.getX();
            double dz = loc.getZ() - car.lastLocation.getZ();
            if (Math.sqrt(dx * dx + dz * dz) < Math.abs(car.speed) * 0.3) {
                crash(car, loc, driver);
                speed = -car.speed * 0.2;
            }
        }
        car.lastLocation = loc;

        double[] f = CarPhysics.forward(yaw);
        if (speed != 0 || car.speed != 0) {
            Vector velocity = car.base.getVelocity();
            car.base.setVelocity(new Vector(f[0] * speed, velocity.getY(), f[1] * speed));
        }
        if (Math.abs(yaw - car.yaw) > 0.001) {
            car.base.setRotation(yaw, 0);
            for (Display part : car.parts) {
                part.setRotation(yaw, 0);
            }
        }
        turnWheels(car, speed, steerTarget);
        car.speed = speed;
        car.yaw = yaw;
        car.lastKnown = loc;
        followSeat(car, loc, yaw);

        if (car.health < 35 && now % 6 == 0) {
            double len = car.type.design().length() * MODEL_SCALE / 2;
            loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(f[0] * len * 0.8, 1.0, f[1] * len * 0.8), 3, 0.2, 0.1, 0.2, 0.01);
        }
        if (driver != null && Math.abs(speed) > 0.3) {
            runOver(car, driver, loc, f, speed);
        }
        if (car.siren && now % 10 == 0) {
            siren(car, loc, now);
        }
        // 아무도 안 탄 채 오래 두면 차고로 (직업 차는 사라짐)
        if (driver == null && car.passenger() == null) {
            if (++car.idleTicks > IDLE_LIMIT) {
                store(car, null);
            }
        } else {
            car.idleTicks = 0;
        }
    }

    private void hud(Car car, Player driver, double speed, long now) {
        int hp = car.health();
        String color = hp > 60 ? "<green>" : hp > 25 ? "<yellow>" : "<red>";
        String help = now - car.enteredTick < 100
                ? "  <dark_gray>|</dark_gray>  <gray>W/S 가속·후진  A/D 핸들  Space 브레이크  Ctrl " + (car.type == CarType.POLICE || car.type == CarType.AMBULANCE ? "사이렌" : "경적") + "  Shift 내리기"
                : "";
        String engine = hp <= 0 ? "  <red>시동 꺼짐 (폰 → 차고 → 견인)" : "";
        plugin.actionBar().status(driver, Text.mm("<white><bold>" + CarPhysics.kmh(speed) + "</bold> km/h  <dark_gray>|</dark_gray>  <gray>차 상태 "
                + color + hp + "%" + engine + help));
    }

    /** 벽에 부딪힘: 소리·연기, 속도만큼 차 상태가 줄어듦 */
    private void crash(Car car, Location loc, Player driver) {
        loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.7f);
        loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.8, 0), 12, 0.5, 0.3, 0.5, 0.02);
        car.crashCooldown = 10;
        double before = car.health;
        car.health = Math.max(0, car.health - Math.min(40, 4 + Math.abs(car.speed) * 40));
        if (car.record != null) {
            car.record.setHealth(car.health());
        }
        if (driver != null && before > 0 && car.health <= 0) {
            Text.send(driver, "<red>차가 크게 부서져 시동이 꺼졌어요. <gray>폰 → 차고에서 견인·수리를 불러 주세요.");
            driver.playSound(driver.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.6f);
        }
    }

    /** 바퀴 굴리기·앞바퀴 꺾기 (움직이거나 꺾임이 바뀔 때만 보냄) */
    private static void turnWheels(Car car, double speed, float steerTarget) {
        CarDesign d = car.type.design();
        float steer = car.steer + Math.max(-6f, Math.min(6f, steerTarget - car.steer));
        if (speed == 0 && steer == car.steer) {
            return;
        }
        car.spin = (car.spin + speed / (d.wheelRadius() * MODEL_SCALE)) % (Math.PI * 2);
        car.steer = steer;
        for (int i = 0; i < car.wheels.size(); i++) {
            ItemDisplay w = car.wheels.get(i);
            w.setInterpolationDelay(0);
            w.setInterpolationDuration(1);
            w.setTransformation(wheelTransform(d, i, car.attachY, car.spin, steer));
        }
    }

    /**
     * 조수석 받침을 차 옆자리로 옮김 (손님과 함께). RETAIN_PASSENGERS 는 1.21.10 부터 없어질 예정이지만
     * (그 뒤로는 기본으로 손님을 데리고 감) 예전 동작에서도 손님이 내리지 않게 남겨 둠
     */
    @SuppressWarnings("removal")
    private static void followSeat(Car car, Location loc, float yaw) {
        if (car.seat == null || !car.seat.isValid()) {
            return;
        }
        double yr = Math.toRadians(yaw), c = Math.cos(yr), s = Math.sin(yr);
        double x = SEAT_X * MODEL_SCALE, z = SEAT_Z * MODEL_SCALE;
        // 차 좌표 (x 왼쪽, z 앞) → 월드 (마인크래프트 yaw: 앞 = (-sin, cos), 왼쪽 = (cos, sin))
        Location at = loc.clone().add(x * c - z * s, 0, x * s + z * c);
        at.setYaw(yaw);
        Location now = car.seat.getLocation();
        if (now.getWorld() == at.getWorld() && now.distanceSquared(at) < 0.0004 && Math.abs(now.getYaw() - yaw) < 0.5) {
            return;     // 차가 서 있으면 옮기지 않음
        }
        car.seat.teleportAsync(at, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    private void honk(Car car) {
        Location loc = car.base.getLocation();
        if (car.type == CarType.POLICE || car.type == CarType.AMBULANCE) {
            car.siren = !car.siren;
            Player driver = car.driver();
            if (driver != null) {
                Text.send(driver, car.siren ? "<red>사이렌 켜짐" : "<gray>사이렌 꺼짐");
            }
            return;
        }
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BIT, 2f, 0.7f);
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BIT, 2f, 0.9f);
    }

    private void siren(Car car, Location loc, long now) {
        boolean high = (now / 10) % 2 == 0;
        World world = loc.getWorld();
        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 2.5f, high ? 1.6f : 1.2f);
        Color color = car.type == CarType.AMBULANCE ? (high ? Color.RED : Color.WHITE) : (high ? Color.RED : Color.BLUE);
        double top = car.type.design().height() * MODEL_SCALE + 0.2;
        world.spawnParticle(Particle.DUST, loc.clone().add(0, top, 0), 6, 0.4, 0.1, 0.4, 0, new Particle.DustOptions(color, 1.6f));
    }

    /** 달리는 차에 부딪힌 생명체는 다치고 날아갑니다. */
    private void runOver(Car car, Player driver, Location loc, double[] f, double speed) {
        double reach = car.type.design().length() * MODEL_SCALE / 2;
        Location front = loc.clone().add(f[0] * reach * Math.signum(speed), 0.8, f[1] * reach * Math.signum(speed));
        for (Entity entity : loc.getWorld().getNearbyEntities(front, 1.3, 1.0, 1.3)) {
            if (!(entity instanceof LivingEntity victim) || entity instanceof ArmorStand || entity == driver || isCarPart(entity)
                    || plugin.npcs().typeOf(entity) != null || entity.getVehicle() != null) {
                continue;
            }
            long ms = System.currentTimeMillis();
            if (ms - lastRunOver.getOrDefault(victim.getUniqueId(), 0L) < 750) {
                continue;
            }
            lastRunOver.put(victim.getUniqueId(), ms);
            double damage = Math.min(16, Math.abs(speed) * 12);
            victim.damage(damage, driver);
            victim.setVelocity(new Vector(f[0] * speed * 1.4, 0.45, f[1] * speed * 1.4));
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
        }
    }

    // ------------------------------------------------------------------ 타기 / 내리기

    private Car carOf(Entity entity) {
        return byEntity.get(entity.getUniqueId());
    }

    /** 이 사람이 이 차를 운전할 수 있나: 주인, 열쇠를 가진 사람, 그 직업 차를 쓰는 직업 */
    boolean canDrive(Player player, Car car) {
        if (car.owner.equals(player.getUniqueId()) || hasKey(player, car.keyId)) {
            return true;
        }
        CharacterData data = plugin.characters().get(player);
        return data != null && car.type.job() != null && data.job() == car.type.job();
    }

    public void enter(Player player, Car car) {
        CharacterData data = plugin.characters().get(player);
        if (data == null || data.isJailed()) {
            return;
        }
        boolean mayDrive = canDrive(player, car);
        if (car.driver() == null && mayDrive) {
            drive(player, car, data);
        } else if (mayDrive || car.driver() != null) {
            sit(player, car);
        } else {
            Text.send(player, "<yellow>잠긴 차예요. <gray>(주인만 운전할 수 있어요)");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1.2f);
        }
    }

    private void drive(Player player, Car car, CharacterData data) {
        player.leaveVehicle();
        if (!car.base.addPassenger(player)) {
            Text.send(player, "<red>차에 탈 수 없어요.");
            return;
        }
        car.enteredTick = car.ticks;
        car.prevSprint = true;
        setAttribute(player.getAttribute(Attribute.CAMERA_DISTANCE), DRIVER_CAMERA_DISTANCE);
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.4f);
        player.showTitle(Title.title(Component.empty(), Text.mm("<gray>F5 키를 누르면 GTA처럼 뒤에서 볼 수 있어요")));
        if (!data.hasLicense() && car.type.job() == null) {
            Text.send(player, "<yellow>운전면허가 없어요. <gray>무면허 운전은 경찰 단속 대상이에요. (면허: 시청 민원실)");
        }
    }

    /** 조수석에 앉기 */
    private void sit(Player player, Car car) {
        if (car.passenger() != null) {
            Text.send(player, "<yellow>자리가 없어요.");
            return;
        }
        if (car.seat == null || !car.seat.isValid()) {
            Location loc = car.base.getLocation();
            car.seat = loc.getWorld().spawn(loc, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setGravity(false);
                stand.setBasePlate(false);
                stand.setPersistent(false);
                setAttribute(stand.getAttribute(Attribute.SCALE), BASE_SCALE);
                tag(stand);
            });
            byEntity.put(car.seat.getUniqueId(), car);
            followSeat(car, loc, car.yaw);
        }
        player.leaveVehicle();
        if (!car.seat.addPassenger(player)) {
            Text.send(player, "<red>차에 탈 수 없어요.");
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.5f);
        Text.send(player, "<gray>조수석에 탔어요. Shift 로 내려요.");
    }

    private static boolean hasKey(Player player, UUID keyId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (keyId.equals(CustomItems.uuid(stack, Keys.KEY_ID))) {
                return true;
            }
        }
        return false;
    }

    private static void resetCamera(Player player) {
        AttributeInstance camera = player.getAttribute(Attribute.CAMERA_DISTANCE);
        if (camera != null) {
            camera.setBaseValue(Attribute.CAMERA_DISTANCE.getDefaultValue());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Car car = carOf(event.getRightClicked());
        if (car == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        CharacterData data = plugin.characters().get(player);
        if (player.isSneaking() && data != null && data.job() == com.junseo.city.logic.Job.POLICE) {
            policeMenu(player, car);
            return;
        }
        enter(player, car);
    }

    // ------------------------------------------------------------------ 경찰: 차량 조회·견인

    private void policeMenu(Player police, Car car) {
        Screen s = new Screen("<blue><bold>차량 조회").columns(1);
        s.line("<gray>번호판: <white><bold>" + car.plate);
        s.line("<gray>차종: <white>" + (car.record != null ? car.record.label() : car.type.displayName()));
        s.line("<gray>차 상태: <white>" + car.health() + "%");
        if (car.record == null) {
            s.line("<gray>관용·회사 차량이에요.");
            s.exit("닫기", null);
            plugin.ui().show(police, s);
            return;
        }
        s.line("<gray>주인: <white>조회 중...");
        s.exit("닫기", null);
        plugin.ui().show(police, s);
        plugin.characters().findByUuid(car.owner).thenAccept(found -> Sched.entity(police, () -> {
            Screen r = new Screen("<blue><bold>차량 조회").columns(1);
            r.line("<gray>번호판: <white><bold>" + car.plate);
            r.line("<gray>차종: <white>" + car.record.label() + " <gray>· 차 상태 " + car.health() + "%");
            if (found.isPresent()) {
                CharacterData o = found.get();
                r.line("<gray>주인: <white>" + Text.esc(o.name()) + " <gray>(" + o.citizenId() + ")");
                r.line("<gray>주인 운전면허: " + (o.hasLicense() ? "<green>있음" : "<red>없음"));
            } else {
                r.line("<gray>주인: <white>알 수 없음");
            }
            Player driver = car.driver();
            if (driver != null) {
                CharacterData dd = plugin.characters().get(driver);
                if (dd != null) {
                    r.line("<gray>운전자: <white>" + Text.esc(dd.name()) + " <gray>· 면허 " + (dd.hasLicense() ? "<green>있음" : "<red>없음"));
                }
            }
            r.button("<red>견인 (압류)", "견인소로 보냅니다. 주인이 찾으려면 견인비를 내야 해요", () -> {
                if (car.driver() != null) {
                    Text.send(police, "<yellow>사람이 탄 차는 견인할 수 없어요.");
                    return;
                }
                impound(car);
                Text.send(police, "<green>" + car.plate + " 견인했어요.");
                Player owner = plugin.getServer().getPlayer(car.owner);
                if (owner != null) {
                    Sched.entity(owner, () -> Text.send(owner, "<red>내 차 " + car.plate + " 이(가) 경찰에 견인됐어요. <gray>폰 → 차고에서 찾을 수 있어요."));
                }
                plugin.ui().close(police);
            });
            r.exit("닫기", null);
            plugin.ui().show(police, r);
        }));
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (isCarPart(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isCarPart(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Car car = carOf(event.getDismounted());
        if (car == null) {
            return;
        }
        boolean fromSeat = event.getDismounted() == car.seat;
        resetCamera(player);
        if (!fromSeat) {
            car.siren = false;
        } else {
            // 손님이 내리면 조수석 받침은 치움 (매 틱 옮기지 않게)
            ArmorStand seat = car.seat;
            car.seat = null;
            byEntity.remove(seat.getUniqueId());
            Sched.entityLater(seat, 1, seat::remove);
        }
        Location loc = car.base.getLocation();
        double[] f = CarPhysics.forward(car.yaw);
        // 운전석은 왼쪽 문, 조수석은 오른쪽 문으로 내리기
        double side = (car.type.design().width() / 2 * MODEL_SCALE + 0.7) * (fromSeat ? -1 : 1);
        Location exit = loc.clone().add(f[1] * side, 0.1, -f[0] * side);
        exit.setYaw(player.getLocation().getYaw());
        exit.setPitch(player.getLocation().getPitch());
        Block feet = exit.getBlock();
        if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
            exit = loc.clone().add(0, car.type.design().height() * MODEL_SCALE + 0.3, 0);
        }
        Location target = exit;
        Sched.entityLater(player, 1, () -> {
            if (!player.isInsideVehicle()) {
                player.teleportAsync(target);
            }
        });
    }

    /** 직업 차 열쇠: 땅 우클릭 = 차 꺼내기, 웅크리고 우클릭 = 차 넣기. 플레이어 차는 폰 차고 앱으로 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onKey(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack key = player.getInventory().getItemInMainHand();
        if (!CustomItems.is(key, CustomItems.CAR_KEY)) {
            return;
        }
        event.setCancelled(true);
        UUID keyId = CustomItems.uuid(key, Keys.KEY_ID);
        CarType type = CarType.byId(CustomItems.string(key, Keys.CAR_TYPE));
        UUID owner = CustomItems.uuid(key, Keys.OWNER);
        if (keyId == null || type == null) {
            return;
        }
        if (type.forSale()) {
            Text.send(player, "<yellow>내 차는 이제 공영 차고에서 꺼내요. <gray>(폰 → 차고)");
            return;
        }
        if (player.isSneaking()) {
            Car car = byKey.get(keyId);
            if (car == null) {
                Text.send(player, "<gray>꺼내 놓은 차가 없어요.");
            } else {
                despawn(car);
                Text.send(player, "<green>" + type.displayName() + " 넣었어요.");
            }
            return;
        }
        if (player.isInsideVehicle()) {
            return;
        }
        CharacterData data = plugin.characters().get(player);
        if (data == null) {
            return;
        }
        if (type.job() != null && data.job() != type.job() && !player.hasPermission("junseocity.admin")) {
            Text.send(player, "<red>" + type.displayName() + "는 " + type.job().displayName() + "만 꺼낼 수 있어요.");
            return;
        }
        if (data.isJailed()) {
            Text.send(player, "<red>감옥에서는 차를 꺼낼 수 없어요.");
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            Text.send(player, "<yellow>차를 놓을 땅을 바라보고 우클릭하세요.");
            return;
        }
        Block place = clicked.getRelative(event.getBlockFace() == BlockFace.DOWN ? BlockFace.UP : event.getBlockFace());
        if (!place.isPassable() || !place.getRelative(BlockFace.UP).isPassable()) {
            Text.send(player, "<red>여기는 차를 놓을 공간이 없어요.");
            return;
        }
        Location at = place.getLocation().add(0.5, 0, 0.5);
        at.setYaw(player.getLocation().getYaw());
        spawnJob(type, owner == null ? player.getUniqueId() : owner, keyId, at);
        Text.send(player, "<green>" + type.displayName() + " 등장! 차를 우클릭해서 타세요.");
    }

    public void onQuit(Player player) {
        if (player.getVehicle() != null && carOf(player.getVehicle()) != null) {
            player.leaveVehicle();
        }
        resetCamera(player);
        lastRunOver.remove(player.getUniqueId());
    }

    /** 서버가 갑자기 꺼져서 남은 차 부품 정리. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(Keys.CAR, PersistentDataType.BYTE) && carOf(entity) == null) {
                entity.remove();
            }
        }
    }
}
