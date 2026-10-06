package com.junseo.city.data;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.CharacterData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 접속한 사람들의 캐릭터를 메모리에 들고 있고, DB 와 비동기로 주고받습니다.
 * - 접속 직전(비동기 단계)에 DB 에서 읽어 둡니다 → 게임 스레드는 기다리지 않음
 * - 바뀐 데이터는 주기적으로, 그리고 나갈 때 DB 에 저장합니다
 */
public final class CharacterService implements Listener {
    private final JunseoCity plugin;
    private final Database db;
    private final CharacterRepository repo;
    private final Map<UUID, CharacterData> online = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<?>> pendingSaves = new ConcurrentHashMap<>();

    public CharacterService(JunseoCity plugin, Database db, CharacterRepository repo) {
        this.plugin = plugin;
        this.db = db;
        this.repo = repo;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        try {
            CompletableFuture<?> saving = pendingSaves.get(uuid);
            if (saving != null) {
                saving.join(); // 방금 나간 사람의 저장이 끝난 뒤에 읽기
            }
            Optional<CharacterData> data = db.submit(c -> repo.findByUuid(c, uuid)).join();
            data.ifPresentOrElse(d -> online.put(uuid, d), () -> online.remove(uuid));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "캐릭터를 불러오지 못했어요: " + uuid, e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    net.kyori.adventure.text.Component.text("서버 데이터를 불러오지 못했어요. 잠시 뒤 다시 접속해 주세요."));
        }
    }

    /**
     * 접속했을 때 호출: 미리 읽어 둔 게 없으면(재접속 경합 등) DB 에서 다시 읽습니다.
     * 결과가 비어 있으면 아직 캐릭터를 만들지 않은 사람입니다.
     */
    public CompletableFuture<Optional<CharacterData>> ensureLoaded(Player player) {
        UUID uuid = player.getUniqueId();
        CharacterData cached = online.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        CompletableFuture<?> saving = pendingSaves.getOrDefault(uuid, CompletableFuture.completedFuture(null));
        return saving.exceptionally(e -> null)
                .thenCompose(ignored -> db.submit(c -> repo.findByUuid(c, uuid)))
                .thenApply(found -> {
                    found.ifPresent(d -> online.putIfAbsent(uuid, d));
                    return found.map(d -> online.get(uuid));
                });
    }

    /** 캐릭터가 없으면(아직 만들지 않음) null. */
    public CharacterData get(Player player) {
        return online.get(player.getUniqueId());
    }

    public CharacterData get(UUID uuid) {
        return online.get(uuid);
    }

    public boolean has(Player player) {
        return online.containsKey(player.getUniqueId());
    }

    public Collection<CharacterData> online() {
        return online.values();
    }

    /** 이름이 이미 쓰이는지 (DB 전체에서). */
    public CompletableFuture<Boolean> nameTaken(String name) {
        return db.submit(c -> repo.nameTaken(c, name));
    }

    /** 새 캐릭터 만들기. 이름이 겹치면 실패(예외)합니다. */
    public CompletableFuture<CharacterData> create(Player player, String name) {
        long now = System.currentTimeMillis();
        long cash = plugin.settings().startingCash;
        long bank = plugin.settings().startingBank;
        return db.submit(c -> repo.create(c, player.getUniqueId(), name, cash, bank, now))
                .thenApply(data -> {
                    online.put(player.getUniqueId(), data);
                    return data;
                });
    }

    /** 돈 기록 남기기 (송금, 관리자 조정 등). */
    public void logMoney(CharacterData who, long amount, String kind, String reason, String other) {
        long now = System.currentTimeMillis();
        db.execute(c -> {
            repo.logMoney(c, who.citizenId(), amount, kind, reason, other, now);
            return null;
        });
    }

    public CompletableFuture<List<CharacterRepository.MoneyEntry>> recentMoney(CharacterData who, int limit) {
        return db.submit(c -> repo.recentMoney(c, who.citizenId(), limit));
    }

    /** 바뀐 캐릭터만 비동기 저장 (주기적으로 호출). */
    public void saveDirty() {
        for (CharacterData data : online.values()) {
            if (data.isDirty()) {
                save(data);
            }
        }
    }

    private CompletableFuture<?> save(CharacterData data) {
        CharacterData.Snapshot snapshot = data.snapshot();
        data.markClean();
        long now = System.currentTimeMillis();
        return db.submit(c -> {
            repo.save(c, snapshot, now);
            return null;
        });
    }

    /** 나갈 때: 저장하고 메모리에서 뺍니다. */
    public void unload(UUID uuid) {
        CharacterData data = online.remove(uuid);
        if (data == null) {
            return;
        }
        CompletableFuture<?> future = save(data);
        pendingSaves.put(uuid, future);
        future.whenComplete((r, e) -> pendingSaves.remove(uuid, future));
    }

    /** 서버 종료: 전부 저장하고 끝날 때까지 기다립니다. */
    public void saveAllBlocking() {
        CompletableFuture<?>[] all = online.values().stream().map(this::save).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(all).join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "종료 중 저장 실패", e);
        }
    }

    /** 관리자 조회용: 접속 안 한 사람도 이름으로 찾기. */
    public CompletableFuture<Optional<CharacterData>> findByName(String name) {
        return db.submit(c -> repo.findByName(c, name));
    }

    /** 접속 안 한 사람도 uuid 로 찾기 (접속 중이면 그 데이터). */
    public CompletableFuture<Optional<CharacterData>> findByUuid(UUID uuid) {
        CharacterData on = online.get(uuid);
        if (on != null) {
            return CompletableFuture.completedFuture(Optional.of(on));
        }
        return db.submit(c -> repo.findByUuid(c, uuid));
    }
}
