package com.junseo.mail;

/** Plain-text mail (password reset codes, report alerts). */
public interface Mailer {

    /** Fire and forget: delivery happens in the background and failures are logged, never thrown. */
    void send(String to, String subject, String text);
}
