package com.junseo.mail;

import com.junseo.common.JunseoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP when spring.mail.host is set (any provider: SES, SendGrid, Gmail …), otherwise the mail only goes to the log
 * (local development). Production must set spring.mail.* or password reset mails never arrive.
 * application.yml maps the host to JUNSEO_SMTP_HOST, so it is present but blank when unset: check the value, not presence.
 */
@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    Mailer mailer(ObjectProvider<JavaMailSender> smtp, JunseoProperties props, @Value("${spring.mail.host:}") String host) {
        JavaMailSender sender = host.isBlank() ? null : smtp.getIfAvailable();
        if (sender == null) {
            log.warn("No SMTP configured (spring.mail.host): mails are written to the log instead of being sent");
            return (to, subject, text) -> log.info("Mail (not sent, no SMTP) to={} subject={}\n{}", to, subject, text);
        }
        String from = props.mail().from();
        return (to, subject, text) -> Thread.ofVirtual().start(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(text);
                sender.send(message);
            } catch (RuntimeException e) {
                log.warn("Mail to {} failed: {}", to, e.getMessage());
            }
        });
    }
}
