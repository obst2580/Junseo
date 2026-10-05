package com.junseo.city.ui;

import com.junseo.city.util.Text;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 버튼만 있는 화면 하나 (스마트폰 앱 화면, 상호작용 메뉴 등).
 * 버튼을 누르면 서버에 저장해 둔 동작이 실행됩니다. 글자 입력칸은 캐릭터 이름 입력에만 씁니다.
 */
public final class Screen {

    record Button(String label, String tooltip, int width, Consumer<DialogResponseView> action) {
    }

    record TextInput(String key, String label, int maxLength) {
    }

    /** 본문 한 덩어리 (width: 글자 줄 폭) */
    record Body(Component text, int width) {
    }

    final String title;
    final List<Body> body = new ArrayList<>();
    final List<Button> buttons = new ArrayList<>();
    final List<TextInput> inputs = new ArrayList<>();
    int columns = 2;
    int width = 150;
    boolean closeWithEscape = true;
    Button exit = new Button("닫기", null, 150, null);
    boolean showExit = true;

    public Screen(String titleMiniMessage) {
        this.title = titleMiniMessage;
    }

    /** 화면 본문 한 줄 (MiniMessage). */
    public Screen line(String miniMessage) {
        body.add(new Body(Text.mm(miniMessage), 300));
        return this;
    }

    /** 화면 본문 한 덩어리 (글자 그림 등). width 는 1~1024 */
    public Screen body(Component text, int width) {
        body.add(new Body(text, Math.max(1, Math.min(1024, width))));
        return this;
    }

    public Screen columns(int columns) {
        this.columns = Math.max(1, columns);
        return this;
    }

    /** 버튼 너비 (1~1024, 기본 150). */
    public Screen buttonWidth(int width) {
        this.width = Math.max(20, Math.min(1024, width));
        return this;
    }

    public Screen button(String label, Runnable action) {
        return button(label, null, action);
    }

    public Screen button(String label, String tooltip, Runnable action) {
        buttons.add(new Button(label, tooltip, -1, r -> action.run()));
        return this;
    }

    /** 폭을 따로 정한 버튼 */
    public Screen button(String label, String tooltip, int width, Runnable action) {
        buttons.add(new Button(label, tooltip, Math.max(1, Math.min(1024, width)), r -> action.run()));
        return this;
    }

    /** 입력칸 값이 필요한 버튼. */
    public Screen submit(String label, Consumer<DialogResponseView> action) {
        buttons.add(new Button(label, null, -1, action));
        return this;
    }

    public Screen textInput(String key, String label, int maxLength) {
        inputs.add(new TextInput(key, label, maxLength));
        return this;
    }

    /** 아래쪽 닫기/뒤로 버튼. action 이 null 이면 그냥 닫습니다. */
    public Screen exit(String label, Runnable action) {
        this.exit = new Button(label, null, -1, action == null ? null : r -> action.run());
        return this;
    }

    /** 아래쪽 닫기 버튼을 따로 두지 않음 (ESC 나 다른 버튼으로 닫음) */
    public Screen noExitButton() {
        this.showExit = false;
        return this;
    }

    /** ESC 로 닫을 수 없게 (캐릭터 만들기 등). */
    public Screen noEscape() {
        this.closeWithEscape = false;
        return this;
    }

    public String title() {
        return title;
    }
}
