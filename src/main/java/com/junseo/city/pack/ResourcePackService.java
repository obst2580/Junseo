package com.junseo.city.pack;

import com.junseo.city.JunseoCity;
import com.junseo.city.Settings;
import com.junseo.city.util.Text;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * 리소스팩(미니맵·지도 그림)을 만들어서 접속하는 플레이어에게 보냅니다.
 * 접속 준비 단계(설정 단계)에서 보내기 때문에, 게임 화면에 들어가기 전에 다 받아집니다.
 * 받았는지는 {@link #ready(Player)} 로 알 수 있고, 받지 않은 사람에게는 미니맵을 그리지 않습니다
 * (그리면 네모 글자가 잔뜩 보임).
 */
public final class ResourcePackService implements Listener {
    /** 이 리소스팩의 고정 id (클라이언트가 같은 팩인지 알아봄) */
    static final UUID PACK_ID = UUID.nameUUIDFromBytes("junseocity:map".getBytes(StandardCharsets.UTF_8));
    private static final long WAIT_SECONDS = 120;

    private enum State {
        LOADED, DECLINED, FAILED
    }

    private final JunseoCity plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private volatile PackBuilder.Pack pack;
    private volatile PackServer server;

    public ResourcePackService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** 리소스팩을 만들고(파일로도 저장), 내장 웹 서버를 켭니다 */
    public void start() {
        Settings.PackSettings cfg = plugin.settings().resourcePack;
        if (!cfg.enabled()) {
            plugin.getLogger().info("리소스팩이 꺼져 있어요 (config: resource-pack.enabled). 미니맵·큰 지도가 안 보여요.");
            return;
        }
        long started = System.currentTimeMillis();
        PackBuilder.Pack p = PackBuilder.build();
        File dir = new File(plugin.getDataFolder(), "resourcepack");
        try {
            Files.createDirectories(dir.toPath());
            Files.write(new File(dir, "JunseoCity-pack.zip").toPath(), p.zip());
            Files.writeString(new File(dir, "JunseoCity-pack.sha1").toPath(), p.sha1() + "\n");
        } catch (IOException e) {
            plugin.getLogger().warning("리소스팩 파일 저장 실패: " + e.getMessage());
        }
        if (cfg.url().isBlank()) {
            try {
                PackServer s = new PackServer(cfg.bind(), cfg.port(), p.zip(), p.sha1());
                s.start();
                server = s;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "리소스팩 웹 서버를 " + cfg.port()
                        + "번 포트로 열지 못했어요. 다른 프로그램이 쓰고 있으면 config 의 resource-pack.port 를 바꿔 주세요.", e);
                return;
            }
        }
        pack = p;
        plugin.getLogger().info("리소스팩 준비 완료 (" + p.zip().length / 1024 + "KB, " + (System.currentTimeMillis() - started)
                + "ms, SHA-1 " + p.sha1() + ")" + (cfg.url().isBlank() ? " - 내장 웹 서버 포트 " + cfg.port() : " - 주소 " + cfg.url()));

        // /reload 등으로 이미 들어와 있는 사람에게도 보냄
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, host(player.getVirtualHost()));
        }
    }

    public void stop() {
        PackServer s = server;
        if (s != null) {
            s.stop();
            server = null;
        }
    }

    /** 이 플레이어가 리소스팩을 다 받았는지 */
    public boolean ready(Player player) {
        return states.get(player.getUniqueId()) == State.LOADED;
    }

    public void forget(UUID uuid) {
        states.remove(uuid);
    }

    /** 접속 준비 단계: 리소스팩을 보내고 받을 때까지 기다립니다 (이 이벤트는 따로 스레드에서 불려서 기다려도 됨) */
    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PackBuilder.Pack p = pack;
        if (p == null) {
            return;
        }
        UUID id = event.getConnection().getProfile().getId();
        if (id == null) {
            return;
        }
        Settings.PackSettings cfg = plugin.settings().resourcePack;
        CompletableFuture<ResourcePackStatus> done = new CompletableFuture<>();
        Audience audience = event.getConnection().getAudience();
        audience.sendResourcePacks(request(p, cfg, url(p, cfg, host(event.getConnection().getVirtualHost())))
                .callback((packId, status, who) -> {
                    if (!status.intermediate()) {
                        done.complete(status);
                    }
                }));
        ResourcePackStatus status = null;
        try {
            // 1초마다 접속이 끊겼는지 보면서 기다림
            for (long waited = 0; status == null && waited < WAIT_SECONDS; waited++) {
                if (!event.getConnection().isConnected()) {
                    return;
                }
                try {
                    status = done.get(1, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // 아직 받는 중
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException e) {
            status = ResourcePackStatus.FAILED_DOWNLOAD;
        }
        if (status == null) {
            status = ResourcePackStatus.FAILED_DOWNLOAD;
        }
        State state = status == ResourcePackStatus.SUCCESSFULLY_LOADED ? State.LOADED
                : status == ResourcePackStatus.DECLINED ? State.DECLINED : State.FAILED;
        states.put(id, state);
        if (state != State.LOADED) {
            plugin.getLogger().info(event.getConnection().getProfile().getName() + " 리소스팩: " + status);
            if (cfg.required()) {
                event.getConnection().disconnect(Text.mm("<yellow>준서 시티는 리소스팩(미니맵·지도 그림)이 꼭 필요해요.\n"
                        + "<gray>서버 목록 → 이 서버 선택 → 편집 → 서버 리소스팩: <white>사용</white> 으로 바꾼 뒤 다시 들어와 주세요."));
            }
        }
    }

    /** 게임 중에 다시 보낸 경우(/reload 등)의 결과 */
    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> states.put(event.getPlayer().getUniqueId(), State.LOADED);
            case DECLINED -> states.put(event.getPlayer().getUniqueId(), State.DECLINED);
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    states.put(event.getPlayer().getUniqueId(), State.FAILED);
            default -> {
            }
        }
    }

    /** 들어온 뒤 안내 (리소스팩이 없으면). 플레이어 스레드에서 호출 */
    public void greet(Player player) {
        State state = states.get(player.getUniqueId());
        if (pack == null || state == State.LOADED) {
            return;
        }
        if (state == null) {
            // 설정 단계를 거치지 않은 경우: 지금 보냄
            send(player, host(player.getVirtualHost()));
        } else if (state == State.DECLINED) {
            Text.send(player, "<yellow>리소스팩을 받지 않아서 미니맵·큰 지도가 안 보여요.");
            Text.send(player, "<gray>서버 목록 → 이 서버 선택 → 편집 → 서버 리소스팩: <white>사용</white> 으로 바꾼 뒤 다시 들어와 주세요.");
        } else {
            Text.send(player, "<red>리소스팩을 받지 못했어요. 미니맵·큰 지도가 안 보여요.");
            Text.send(player, "<gray>관리자에게 알려 주세요 (리소스팩 포트 " + plugin.settings().resourcePack.port() + " 이 열려 있는지).");
        }
    }

    private void send(Player player, String host) {
        PackBuilder.Pack p = pack;
        if (p == null) {
            return;
        }
        Settings.PackSettings cfg = plugin.settings().resourcePack;
        player.sendResourcePacks(request(p, cfg, url(p, cfg, host)));
    }

    private static ResourcePackRequest.Builder request(PackBuilder.Pack p, Settings.PackSettings cfg, URI url) {
        return ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, url, p.sha1()))
                .required(cfg.required())
                .replace(false)
                .prompt(Text.mm("<yellow>" + Text.esc(cfg.prompt())));
    }

    /** 리소스팩 주소. 설정에 주소가 있으면 그것, 아니면 내장 웹 서버 */
    static URI url(PackBuilder.Pack p, Settings.PackSettings cfg, String connectedHost) {
        if (!cfg.url().isBlank()) {
            return URI.create(cfg.url());
        }
        String host = !cfg.publicHost().isBlank() ? cfg.publicHost() : connectedHost;
        if (host == null || host.isBlank()) {
            host = Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]"; // IPv6
        }
        return URI.create("http://" + host + ":" + cfg.port() + "/" + p.sha1() + ".zip");
    }

    /** 플레이어가 접속할 때 입력한 서버 주소 (끝의 점 제거) */
    private static String host(InetSocketAddress virtualHost) {
        if (virtualHost == null) {
            return null;
        }
        String host = virtualHost.getHostString();
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }
}
