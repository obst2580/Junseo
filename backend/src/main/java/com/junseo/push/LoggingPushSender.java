package com.junseo.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when APNs is not configured (local development): logs what would have been sent. */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public PushOutcome send(PushMessage m) {
        String shortToken = m.token().length() > 8 ? m.token().substring(0, 8) + "…" : m.token();
        log.info("[push:{}] user={} token={} ({}) payload={}", m.type(), m.userId(), shortToken, m.environment(), m.payload());
        return PushOutcome.SENT;
    }
}
