package com.junseo.city.vehicle;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CarPhysics;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Job;
import com.junseo.city.phone.DispatchService;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Keys;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import com.junseo.city.vehicle.model.CarDesign;
import com.junseo.city.vehicle.model.CarModels;
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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * 자동차 소환/운전/내리기.
 * 운전: W/S 가속·후진, A/D 핸들, 스페이스 브레이크, Ctrl(달리기) 경적/사이렌, Shift 내리기.
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
    /** 번호판 글자 크기 */
    private static final float PLATE_TEXT_SCALE = 0.42f;
    /** 앞바퀴 최대 꺾임 (도) */
    private static final float MAX_STEER = 28f;
    private static final String PLATE_LETTERS = "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주";

    /** 차 부품 하나 (차 중심 바닥 기준, +z 가 앞쪽). */
    private record Part(Material material, float x1, float y1, float z1, float x2, float y2, float z2, boolean glow) {
    }

    private final JunseoCity plugin;
    private final Map<UUID, Car> byEntity = new ConcurrentHashMap<>();
    private final Map<UUID, Car> byKey = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRunOver = new ConcurrentHashMap<>();

    public CarService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    private static List<Part> model(CarType type) {
        Material body = type.bodyColor();
        List<Part> parts = new ArrayList<>(List.of(
                new Part(body, -0.9f, 0.2f, -1.6f, 0.9f, 0.85f, 1.6f, false),            // 차체
                new Part(Material.GLASS, -0.8f, 0.85f, -1.0f, 0.8f, 1.6f, 0.5f, false),   // 창문
                new Part(body, -0.8f, 1.6f, -1.0f, 0.8f, 1.72f, 0.5f, false),              // 지붕
                new Part(Material.BLACK_CONCRETE, 0.7f, 0f, 0.8f, 1.0f, 0.5f, 1.3f, false),   // 바퀴
                new Part(Material.BLACK_CONCRETE, -1.0f, 0f, 0.8f, -0.7f, 0.5f, 1.3f, false),
                new Part(Material.BLACK_CONCRETE, 0.7f, 0f, -1.3f, 1.0f, 0.5f, -0.8f, false),
                new Part(Material.BLACK_CONCRETE, -1.0f, 0f, -1.3f, -0.7f, 0.5f, -0.8f, false),
                new Part(Material.SEA_LANTERN, 0.4f, 0.5f, 1.6f, 0.8f, 0.7f, 1.66f, true),   // 전조등
                new Part(Material.SEA_LANTERN, -0.8f, 0.5f, 1.6f, -0.4f, 0.7f, 1.66f, true),
                new Part(Material.RED_CONCRETE, 0.4f, 0.5f, -1.66f, 0.8f, 0.7f, -1.6f, true), // 후미등
                new Part(Material.RED_CONCRETE, -0.8f, 0.5f, -1.66f, -0.4f, 0.7f, -1.6f, true)));
        if (type == CarType.POLICE) {
            parts.add(new Part(Material.RED_CONCRETE, -0.6f, 1.72f, -0.4f, 0f, 1.88f, -0.1f, true));
            parts.add(new Part(Material.BLUE_CONCRETE, 0f, 1.72f, -0.4f, 0.6f, 1.88f, -0.1f, true));
            parts.add(new Part(Material.WHITE_CONCRETE, -0.91f, 0.45f, -1.2f, 0.91f, 0.6f, 1.2f, false)); // 흰 줄무늬
        }
        return parts;
    }

    // ------------------------------------------------------------------ 소환 / 제거

    public Car spawn(CarType type, UUID owner, UUID keyId, Location at) {
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
        List<Display> displays = new ArrayList<>();
        List<ItemDisplay> wheels = new ArrayList<>();
        String plate = plateNumber(keyId);
        CarDesign design = type.design();
        if (design != null) {
            // 리소스팩 3D 모델: 차체 하나 + 바퀴 넷 + 앞뒤 번호판 글자
            displays.add(itemDisplay(world, loc, yaw, CarModels.bodyModel(design), bodyTransform(attachY), true));
            for (int i = 0; i < 4; i++) {
                ItemDisplay w = itemDisplay(world, loc, yaw, CarModels.wheelModel(design), wheelTransform(design, i, attachY, 0, 0), false);
                wheels.add(w);
                displays.add(w);
            }
            displays.add(plateDisplay(world, loc, yaw, design, plate, attachY, true));
            displays.add(plateDisplay(world, loc, yaw, design, plate, attachY, false));
        } else {
            for (Part part : model(type)) {
                displays.add(world.spawn(loc, BlockDisplay.class, d -> {
                    d.setBlock(part.material().createBlockData());
                    d.setTransformation(new Transformation(
                            new Vector3f(part.x1(), part.y1() - attachY, part.z1()),
                            new Quaternionf(),
                            new Vector3f(part.x2() - part.x1(), part.y2() - part.y1(), part.z2() - part.z1()),
                            new Quaternionf()));
                    d.setTeleportDuration(2);
                    d.setShadowRadius(0f);
                    d.setPersistent(false);
                    if (part.glow()) {
                        d.setBrightness(new Display.Brightness(15, 15));
                    }
                    d.setRotation(yaw, 0);
                    tag(d);
                }));
            }
        }
        for (Display d : displays) {
            base.addPassenger(d);
        }
        // 클릭 판정도 차에 태워서 매 틱 따로 옮기지 않아도 되게 (Folia 에서 순간이동은 비싸요)
        Interaction hitbox = world.spawn(loc, Interaction.class, i -> {
            i.setInteractionWidth(design != null ? 2.4f : 2.2f);
            i.setInteractionHeight(design != null ? 1.7f : 1.8f);
            i.setResponsive(true);
            i.setPersistent(false);
            tag(i);
        });
        base.addPassenger(hitbox);
        Car car = new Car(type, owner, keyId, base, hitbox, displays, wheels, plate, yaw);
        car.attachY = attachY;
        Sched.entityLater(base, 3, () -> calibrate(car));
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

    /** 차체: 모델 (8, 0, 8) 이 차 바닥 가운데. 모델 1 칸 = 1/8 m 라서 2배 × MODEL_SCALE */
    private static Transformation bodyTransform(float attachY) {
        return new Transformation(new Vector3f(0, MODEL_SCALE - attachY, 0), new Quaternionf(),
                new Vector3f(2 * MODEL_SCALE), ITEM_FLIP);
    }

    /** 바퀴 i (0 앞왼, 1 앞오, 2 뒤왼, 3 뒤오): 모델 가운데가 바퀴 중심. 굴림(spin, 라디안)·꺾임(steer, 도) */
    private static Transformation wheelTransform(CarDesign d, int i, float attachY, double spin, float steer) {
        boolean front = i < 2;
        float x = (i % 2 == 0 ? 1 : -1) * (float) d.track() / 2 * MODEL_SCALE;
        float y = (float) d.wheelRadius() * MODEL_SCALE - attachY;
        float z = (float) (front ? d.frontAxle() : d.rearAxle()) * MODEL_SCALE;
        Quaternionf rot = new Quaternionf().rotationY((float) Math.toRadians(front ? steer : 0)).rotateX((float) spin);
        return new Transformation(new Vector3f(x, y, z), rot, new Vector3f(2 * MODEL_SCALE), ITEM_FLIP);
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

    /** 열쇠마다 정해지는 번호판 (예: 12가 3456) */
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

    /** 차 스레드에서: 엔티티 제거. */
    private void removeEntities(Car car) {
        Player driver = car.driver();
        if (driver != null) {
            driver.leaveVehicle();
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
        for (Display part : car.parts) {
            byEntity.remove(part.getUniqueId());
        }
        if (car.task != null) {
            car.task.cancel();
        }
    }

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

    /** 서버 종료 때. Paper 에서는 바로 지우고, Folia 에서는 저장되지 않는 엔티티라 그냥 사라집니다. */
    public void removeAll() {
        for (Car car : new ArrayList<>(byKey.values())) {
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
        if (driver != null) {
            var input = driver.getCurrentInput();
            steerTarget = (input.isLeft() ? MAX_STEER : 0) - (input.isRight() ? MAX_STEER : 0);
            speed = CarPhysics.nextSpeed(speed, input.isForward(), input.isBackward(), input.isJump(),
                    type.maxSpeed(), type.acceleration());
            yaw = CarPhysics.nextYaw(yaw, input.isLeft(), input.isRight(), speed, type.maxSpeed(), type.turnDegrees());
            if (input.isSprint() && !car.prevSprint) {
                honk(car);
            }
            car.prevSprint = input.isSprint();
            if (now % 4 == 0) {
                String help = now - car.enteredTick < 100
                        ? "  <dark_gray>|</dark_gray>  <gray>W/S 가속·후진  A/D 핸들  Space 브레이크  Ctrl " + (type == CarType.POLICE ? "사이렌" : "경적") + "  Shift 내리기"
                        : "";
                plugin.actionBar().status(driver, Text.mm("<white><bold>" + CarPhysics.kmh(speed) + "</bold> km/h" + help));
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
                loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 0.7f);
                loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.8, 0), 12, 0.5, 0.3, 0.5, 0.02);
                speed = -car.speed * 0.2;
                car.crashCooldown = 10;
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

        if (driver != null && Math.abs(speed) > 0.3) {
            runOver(car, driver, loc, f, speed, now);
        }
        if (car.siren && now % 10 == 0) {
            siren(car, loc, now);
        }
    }

    /** 바퀴 굴리기·앞바퀴 꺾기 (움직이거나 꺾임이 바뀔 때만 보냄) */
    private static void turnWheels(Car car, double speed, float steerTarget) {
        CarDesign d = car.type.design();
        if (d == null || car.wheels.isEmpty()) {
            return;
        }
        float steer = car.steer + Math.max(-6f, Math.min(6f, steerTarget - car.steer));
        if (speed == 0 && steer == car.steer) {
            return;
        }
        car.spin = (car.spin + speed / (d.wheelRadius() * MODEL_SCALE)) % (Math.PI * 2);
        car.steer = steer;
        float attachY = car.attachY;
        for (int i = 0; i < car.wheels.size(); i++) {
            ItemDisplay w = car.wheels.get(i);
            w.setInterpolationDelay(0);
            w.setInterpolationDuration(1);
            w.setTransformation(wheelTransform(d, i, attachY, car.spin, steer));
        }
    }

    private void honk(Car car) {
        Location loc = car.base.getLocation();
        if (car.type == CarType.POLICE) {
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
        Color color = high ? Color.RED : Color.BLUE;
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 2.0, 0), 6, 0.4, 0.1, 0.4, 0,
                new Particle.DustOptions(color, 1.6f));
    }

    /** 달리는 차에 부딪힌 생명체는 다치고 날아갑니다. */
    private void runOver(Car car, Player driver, Location loc, double[] f, double speed, long now) {
        Location front = loc.clone().add(f[0] * 1.2 * Math.signum(speed), 0.8, f[1] * 1.2 * Math.signum(speed));
        for (Entity entity : loc.getWorld().getNearbyEntities(front, 1.3, 1.0, 1.3)) {
            if (!(entity instanceof LivingEntity victim) || entity instanceof ArmorStand || entity == driver || isCarPart(entity)
                    || plugin.npcs().typeOf(entity) != null || driver.getPassengers().contains(entity)) {
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

    public void enter(Player player, Car car) {
        if (car.driver() != null) {
            Text.send(player, "<yellow>이미 누가 운전하고 있어요.");
            return;
        }
        CharacterData data = plugin.characters().get(player);
        if (data == null || data.isJailed()) {
            return;
        }
        boolean police = data.job() == Job.POLICE;
        if (!hasKey(player, car.keyId) && !(car.type == CarType.POLICE && police)) {
            plugin.dispatch().automatic(DispatchService.Line.POLICE, "car_theft",
                    car.type == CarType.POLICE ? "경찰차 도난 신고" : "차량 도난 신고", player.getLocation());
            Text.send(player, car.type == CarType.POLICE ? "<red>경찰차를 훔쳤어요!" : "<red>남의 차를 훔쳤어요!");
        }
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
        if (event.getHand() == EquipmentSlot.HAND) {
            enter(event.getPlayer(), car);
        }
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
        resetCamera(player);
        car.siren = false;
        Location loc = car.base.getLocation();
        double[] f = CarPhysics.forward(car.yaw);
        // 운전석 왼쪽 문으로 내리기
        Location exit = loc.clone().add(f[1] * 1.6, 0.1, -f[0] * 1.6);
        exit.setYaw(player.getLocation().getYaw());
        exit.setPitch(player.getLocation().getPitch());
        Block feet = exit.getBlock();
        if (!feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
            exit = loc.clone().add(0, 2.0, 0);
        }
        Location target = exit;
        Sched.entityLater(player, 1, () -> {
            if (!player.isInsideVehicle()) {
                player.teleportAsync(target);
            }
        });
    }

    /** 열쇠로 땅 우클릭 = 차 꺼내기, 웅크리고 우클릭 = 차 넣기. */
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
        if (player.isSneaking()) {
            Car car = byKey.get(keyId);
            if (car == null) {
                Text.send(player, "<gray>꺼내 놓은 차가 없어요.");
            } else {
                despawn(car);
                Text.send(player, "<green>차고에 넣었어요: " + type.displayName());
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
        if (type == CarType.POLICE && data.job() != Job.POLICE) {
            Text.send(player, "<red>경찰차는 경찰만 꺼낼 수 있어요.");
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
        spawn(type, owner == null ? player.getUniqueId() : owner, keyId, at);
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
