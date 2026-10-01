package com.junseo.push;

/** Delivers one push to one device token. Implementations must not throw for delivery failures. */
public interface PushSender {

    PushOutcome send(PushMessage message);

    enum PushType {
        ALERT,
        WIDGETS
    }

    enum PushOutcome {
        SENT,
        /** The token is dead (APNs 410, BadDeviceToken, Unregistered) and should be deleted. */
        INVALID_TOKEN,
        FAILED
    }

    record PushMessage(long userId, String token, String environment, PushType type, String payload) {}
}
