package com.junseo.city.character;

import com.junseo.city.JunseoCity;
import com.junseo.city.economy.Economy;
import com.junseo.city.logic.CharacterData;
import com.junseo.city.logic.Identity;
import com.junseo.city.place.PlaceType;
import com.junseo.city.ui.Screen;
import com.junseo.city.util.CustomItems;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 처음 온 사람의 캐릭터 만들기.
 * 규칙 동의 → 이름 입력(딱 한 번) → 확인 → 주민번호 발급 → 스마트폰·신분증·정착금 지급.
 * 캐릭터를 만들기 전에는 움직이거나 말하거나 다른 걸 할 수 없어요.
 */
public final class CreationService implements Listener {
    private final JunseoCity plugin;
    /** 캐릭터를 아직 만들지 않은 사람 → 화면을 다시 띄우는 반복 작업. */
    private final Map<UUID, ScheduledTask> creating = new ConcurrentHashMap<>();
    private final Set<UUID> submitting = ConcurrentHashMap.newKeySet();

    public CreationService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    public boolean isCreating(Player player) {
        return creating.containsKey(player.getUniqueId());
    }

    public void start(Player player) {
        Location spawn = plugin.places().location(PlaceType.SPAWN);
        if (spawn != null) {
            player.teleportAsync(spawn);
        }
        player.showTitle(Title.title(Text.mm("<gold><bold>" + Text.esc(plugin.settings().serverName)),
                Text.mm("<white>새로운 인생을 시작해요"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));
        showRules(player);
        // 화면이 닫혔으면 몇 초 뒤 다시 보여줘요. (이름을 쓰는 중에는 방해하지 않고, 아주 오래 그대로면 다시 띄움)
        ScheduledTask task = Sched.entityRepeat(player, 100, 100, t -> {
            if (plugin.characters().has(player) || submitting.contains(player.getUniqueId())) {
                return;
            }
            long age = plugin.ui().activeAgeMs(player);
            if (age < 0 || age > 120_000) {
                showRules(player);
            }
        });
        if (task != null) {
            ScheduledTask old = creating.put(player.getUniqueId(), task);
            if (old != null) {
                old.cancel();
            }
        }
    }

    /** 만드는 도중 다른 화면(G키 등)을 눌렀을 때 다시 캐릭터 만들기로. */
    public void resume(Player player) {
        showRules(player);
    }

    public void stop(UUID uuid) {
        ScheduledTask task = creating.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        submitting.remove(uuid);
    }

    private void showRules(Player player) {
        String name = Text.esc(plugin.settings().serverName);
        Screen s = new Screen("<gold><bold>" + name + " 입국 심사").noEscape().columns(2);
        s.line("<white>이 도시는 <gold>역할극(RP)</gold> 서버예요. 아래 규칙을 꼭 지켜 주세요.");
        s.line("<gray>1. 이유 없이 사람을 죽이거나 차로 치지 않기");
        s.line("<gray>2. 죽었다 깨어나면 그 사건은 잊기 (뉴라이프)");
        s.line("<gray>3. 게임 밖 정보(방송·디스코드)를 게임에서 쓰지 않기");
        s.line("<gray>4. 불리할 때 접속 끊고 도망가지 않기");
        s.line("<gray>5. 총이 겨눠지면 목숨을 아끼기");
        s.line("<gray>6. 뉴비 괴롭히기, 의료국 공격, 버그·핵 금지");
        s.button("<green><bold>동의합니다", () -> showName(player, null));
        s.button("<red>동의하지 않음", () -> player.kick(Component.text("규칙에 동의해야 들어올 수 있어요.")));
        s.exit("나중에", () -> plugin.ui().close(player));
        plugin.ui().show(player, s);
    }

    private void showName(Player player, String error) {
        Screen s = new Screen("<gold><bold>캐릭터 이름 정하기").noEscape().columns(1).buttonWidth(200);
        s.line("<white>이 도시에서 쓸 이름을 정해 주세요. <red>한 번 정하면 바꿀 수 없어요!");
        s.line("<gray>한글 2~6자 또는 영문 2~12자 (띄어쓰기·숫자·특수문자 안 돼요)");
        if (error != null) {
            s.line("<red>" + error);
        }
        s.textInput("name", "<yellow>이름", 12);
        s.submit("<aqua><bold>이 이름으로 하기", response -> {
            String name = Identity.normalizeName(response == null ? null : response.getText("name"));
            String problem = Identity.nameProblem(name);
            if (problem != null) {
                showName(player, problem);
                return;
            }
            showConfirm(player, name);
        });
        s.exit("◀ 규칙 다시 보기", () -> showRules(player));
        plugin.ui().show(player, s);
    }

    private void showConfirm(Player player, String name) {
        Screen s = new Screen("<gold><bold>이름 확인").noEscape().columns(2);
        s.line("<white>이름: <gold><bold>" + Text.esc(name));
        s.line("<gray>이 이름으로 도시 생활을 시작할까요? 나중에 바꿀 수 없어요.");
        s.button("<green><bold>확정", () -> create(player, name));
        s.button("<yellow>다시 정하기", () -> showName(player, null));
        s.exit("◀ 뒤로", () -> showName(player, null));
        plugin.ui().show(player, s);
    }

    private void create(Player player, String name) {
        if (!submitting.add(player.getUniqueId())) {
            return;
        }
        Screen waiting = new Screen("<gold>주민등록 중...").noEscape();
        waiting.line("<gray>잠시만 기다려 주세요.");
        waiting.exit("기다리기", () -> plugin.ui().show(player, waiting));
        plugin.ui().show(player, waiting);
        plugin.characters().nameTaken(name).thenCompose(taken -> {
            if (taken) {
                return java.util.concurrent.CompletableFuture.<CharacterData>completedFuture(null);
            }
            return plugin.characters().create(player, name);
        }).whenComplete((data, error) -> Sched.entity(player, () -> {
            submitting.remove(player.getUniqueId());
            if (error != null) {
                // 거의 동시에 같은 이름을 만든 경우 등
                showName(player, "그 이름은 쓸 수 없어요. 다른 이름을 골라 주세요.");
                return;
            }
            if (data == null) {
                showName(player, "이미 누가 쓰는 이름이에요. 다른 이름을 골라 주세요.");
                return;
            }
            finish(player, data);
        }));
    }

    private void finish(Player player, CharacterData data) {
        stop(player.getUniqueId());
        plugin.ui().close(player);
        Economy.give(player, CustomItems.phone());
        Economy.give(player, CustomItems.idCard(data));
        Economy.give(player, new ItemStack(Material.BREAD, 5));
        plugin.onCharacterReady(player, data);
        player.showTitle(Title.title(Text.mm("<green><bold>주민등록 완료!"),
                Text.mm("<white>" + Text.esc(data.name()) + " <gray>(" + data.citizenId() + ")"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        Text.send(player, "<green>환영해요, <white>" + Text.esc(data.name()) + "</white> 님! 주민번호는 <white>"
                + data.citizenId() + "</white> 이에요.");
        Text.send(player, "<yellow>G키</yellow><white>를 누르면 스마트폰이 열려요. 정착금 현금 "
                + plugin.settings().money(data.cash()) + " · 은행 " + plugin.settings().money(data.bank()) + "을 받았어요.");
    }

    // ---- 캐릭터가 없는 동안 막기 ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isCreating(event.getPlayer()) && event.hasChangedBlock()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isCreating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isCreating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (isCreating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isCreating(player)) {
            event.setCancelled(true);
        }
    }
}
