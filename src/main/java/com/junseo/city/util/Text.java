package com.junseo.city.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** 채팅/아이템 글자 꾸미기. 문자열은 MiniMessage 형식(&lt;red&gt;빨강&lt;/red&gt;)입니다. */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Component PREFIX = MM.deserialize("<gold>[<yellow>시티</yellow>]</gold> ");

    private Text() {
    }

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /** 아이템 이름/설명용: 기울임 없이. */
    public static Component plain(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(plain("<gray>" + line));
        }
        return out;
    }

    public static void send(Audience to, String miniMessage) {
        to.sendMessage(PREFIX.append(mm(miniMessage)));
    }

    public static void send(Audience to, Component message) {
        to.sendMessage(PREFIX.append(message));
    }

    /** MiniMessage 태그를 뺀 순수 글자. */
    public static String plainText(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MM.deserialize(miniMessage));
    }

    /** 플레이어 이름처럼 밖에서 들어온 글자에 &lt;태그&gt; 가 섞여도 안전하게. */
    public static String esc(String raw) {
        return MM.escapeTags(raw);
    }
}
