package com.junseo.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when APNs is not configured (local development): logs what would have been sent. */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public PushOutcome send(PushMessage m) {
        log.debug("Push disabled: type={}", m.type());
        return PushOutcome.SENT;
    }
}
