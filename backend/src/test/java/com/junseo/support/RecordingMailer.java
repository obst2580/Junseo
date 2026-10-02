package com.junseo.support;

import com.junseo.mail.Mailer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Captures mails instead of sending them. */
public class RecordingMailer implements Mailer {

    public record Mail(String to, String subject, String text) {}

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String text) {
        sent.add(new Mail(to, subject, text));
    }

    public List<Mail> to(String address) {
        return sent.stream().filter(m -> m.to().equals(address)).toList();
    }

    /** The 6-digit code in the newest mail to this address. */
    public String lastCode(String address) {
        List<Mail> mine = to(address);
        Matcher m = CODE.matcher(mine.getLast().text());
        if (!m.find()) {
            throw new IllegalStateException("no code in mail");
        }
        return m.group(1);
    }

    public void clear() {
        sent.clear();
    }
}
