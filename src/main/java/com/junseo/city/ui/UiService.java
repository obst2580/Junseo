package com.junseo.city.ui;

import com.junseo.city.JunseoCity;
import com.junseo.city.util.Sched;
import com.junseo.city.util.Text;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 화면(대화창) 띄우기와 버튼 클릭 처리.
 *
 * 버튼에는 "junseocity:s/화면번호/버튼번호" 라는 id 만 실어 보내고, 실제 동작은 서버 메모리에 둡니다.
 * - 예전 화면의 버튼을 누르면(화면번호가 다르면) 무시합니다.
 * - 조작된 클라이언트가 이상한 id 를 보내도 지금 화면에 있는 동작만 실행됩니다.
 * 스마트폰 홈(G키, 데이터팩)의 버튼은 "junseocity:app/이름" 으로 들어오며 {@link #onStaticAction} 으로 넘깁니다.
 */
public final class UiService implements Listener {
    public static final String NAMESPACE = "junseocity";

    private record Active(long token, Screen screen, long shownAt) {
    }

    private final JunseoCity plugin;
    private final AtomicLong tokens = new AtomicLong();
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<Player, String>> staticActions = new ConcurrentHashMap<>();

    public UiService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** 데이터팩 대화창의 고정 버튼(junseocity:접두어/나머지) 처리기 등록. */
    public void onStaticAction(String prefix, BiConsumer<Player, String> handler) {
        staticActions.put(prefix, handler);
    }

    public void show(Player player, Screen screen) {
        long token = tokens.incrementAndGet();
        active.put(player.getUniqueId(), new Active(token, screen, System.currentTimeMillis()));
        player.showDialog(build(screen, token));
    }

    public void close(Player player) {
        active.remove(player.getUniqueId());
        player.closeDialog();
    }

    public void forget(UUID uuid) {
        active.remove(uuid);
    }

    /** 지금 띄워 둔 화면이 몇 ms 전에 열렸는지. 화면이 없으면 -1. */
    public long activeAgeMs(Player player) {
        Active a = active.get(player.getUniqueId());
        return a == null ? -1 : System.currentTimeMillis() - a.shownAt();
    }

    private static Dialog build(Screen screen, long token) {
        List<ActionButton> buttons = new ArrayList<>();
        for (int i = 0; i < screen.buttons.size(); i++) {
            Screen.Button b = screen.buttons.get(i);
            buttons.add(ActionButton.builder(Text.mm(b.label()))
                    .tooltip(b.tooltip() == null ? null : Text.mm(b.tooltip()))
                    .width(b.width() > 0 ? b.width() : screen.width)
                    .action(DialogAction.customClick(Key.key(NAMESPACE, "s/" + token + "/" + i), null))
                    .build());
        }
        ActionButton exit = ActionButton.builder(Text.mm(screen.exit.label()))
                .width(Math.min(screen.width * Math.max(1, Math.min(screen.columns, buttons.size())), 300))
                .action(screen.exit.action() == null ? null
                        : DialogAction.customClick(Key.key(NAMESPACE, "s/" + token + "/exit"), null))
                .build();
        List<DialogBody> body = new ArrayList<>();
        for (Screen.Body part : screen.body) {
            body.add(DialogBody.plainMessage(part.text(), part.width()));
        }
        List<DialogInput> inputs = new ArrayList<>();
        for (Screen.TextInput in : screen.inputs) {
            inputs.add(DialogInput.text(in.key(), Text.mm(in.label())).maxLength(in.maxLength()).width(200).build());
        }
        DialogBase base = DialogBase.builder(Text.mm(screen.title))
                .canCloseWithEscape(screen.closeWithEscape)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(inputs)
                .build();
        if (buttons.isEmpty()) {
            return Dialog.create(f -> f.empty().base(base).type(DialogType.notice(exit)));
        }
        return Dialog.create(f -> f.empty().base(base)
                .type(DialogType.multiAction(buttons).columns(screen.columns).exitAction(screen.showExit ? exit : null).build()));
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!NAMESPACE.equals(event.getIdentifier().namespace())
                || !(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }
        Player player = connection.getPlayer();
        String path = event.getIdentifier().value();
        DialogResponseView response = event.getDialogResponseView();
        Sched.entity(player, () -> handle(player, path, response));
    }

    private void handle(Player player, String path, DialogResponseView response) {
        if (path.startsWith("s/")) {
            String[] parts = path.split("/");
            Active current = active.get(player.getUniqueId());
            if (parts.length != 3 || current == null || !parts[1].equals(String.valueOf(current.token()))) {
                active.remove(player.getUniqueId());
                player.closeDialog(); // 예전 화면의 버튼
                return;
            }
            Screen.Button button;
            if (parts[2].equals("exit")) {
                button = current.screen().exit;
            } else {
                int index;
                try {
                    index = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    player.closeDialog();
                    return;
                }
                if (index < 0 || index >= current.screen().buttons.size()) {
                    active.remove(player.getUniqueId());
                    player.closeDialog();
                    return;
                }
                button = current.screen().buttons.get(index);
            }
            run(player, current.token(), () -> {
                if (button.action() != null) {
                    button.action().accept(response);
                }
            });
            return;
        }
        int slash = path.indexOf('/');
        String prefix = slash < 0 ? path : path.substring(0, slash);
        String rest = slash < 0 ? "" : path.substring(slash + 1);
        BiConsumer<Player, String> handler = staticActions.get(prefix);
        long before = tokens.get();
        if (handler == null) {
            player.closeDialog();
            return;
        }
        run(player, before, () -> handler.accept(player, rest));
    }

    /** 동작을 실행하고, 새 화면을 띄우지 않았으면 대기 화면이 남지 않게 닫습니다. */
    private void run(Player player, long tokenBefore, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "화면 동작 오류", e);
            Text.send(player, "<red>문제가 생겼어요. 다시 시도해 주세요.");
        }
        Active now = active.get(player.getUniqueId());
        if (now == null || now.token() <= tokenBefore) {
            active.remove(player.getUniqueId());
            player.closeDialog();
        }
    }
}
