package com.junseo.chat;

public final class ChatEvents {

    private ChatEvents() {}

    public record MessageSent(long messageId, long senderId, long receiverId, String text) {}
}
