package com.junseo.push;

/** A device's recorded platform determines the provider, never the shape of its token. */
public record PlatformPushSender(PushSender ios, PushSender android) implements PushSender {
    @Override
    public PushOutcome send(PushMessage message) {
        return switch (message.platform()) {
            case "ios" -> ios.send(message);
            case "android" -> android.send(message);
            default -> PushOutcome.FAILED;
        };
    }
}
