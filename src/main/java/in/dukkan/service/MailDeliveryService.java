package in.dukkan.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Delivers transactional mail when SMTP is configured; always logs clearly in development.
 *
 * <p>When {@code spring.mail.host} is unset, messages are logged only — callers must not claim
 * an email was delivered beyond the generic user-facing copy.
 */
@Service
public class MailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MailDeliveryService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;
    private final boolean mailConfigured;

    public MailDeliveryService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.from:noreply@pinkcarrot.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.mailConfigured = mailHost != null && !mailHost.isBlank();
    }

    public boolean isMailConfigured() {
        return mailConfigured && mailSender.getIfAvailable() != null;
    }

    /**
     * @return true if an SMTP send was attempted successfully; false if only logged
     */
    public boolean sendText(String to, String subject, String body) {
        log.info(
                "MAIL to={} subject={} mailConfigured={} body=\n{}",
                to,
                subject,
                isMailConfigured(),
                body);

        if (!isMailConfigured()) {
            log.warn(
                    "mail not configured, link logged "
                            + "(set DUKKAN_MAIL_HOST / DUKKAN_MAIL_USERNAME / DUKKAN_MAIL_PASSWORD "
                            + "for free Gmail SMTP in .env)");
            return false;
        }

        return deliver(to, subject, body, null);
    }

    /** Plain text plus an HTML part, so a long reset URL stays a visible link. */
    public boolean sendHtml(String to, String subject, String plainBody, String htmlBody) {
        log.info(
                "MAIL to={} subject={} mailConfigured={} body=\n{}",
                to,
                subject,
                isMailConfigured(),
                plainBody);
        if (!isMailConfigured()) {
            log.warn(
                    "mail not configured, link logged "
                            + "(set DUKKAN_MAIL_HOST / DUKKAN_MAIL_USERNAME / DUKKAN_MAIL_PASSWORD "
                            + "for free Gmail SMTP in .env)");
            return false;
        }
        return deliver(to, subject, plainBody, htmlBody);
    }

    private boolean deliver(String to, String subject, String plainBody, String htmlBody) {
        try {
            JavaMailSender sender = mailSender.getObject();
            MimeMessage message = sender.createMimeMessage();
            boolean html = htmlBody != null && !htmlBody.isBlank();
            MimeMessageHelper helper = new MimeMessageHelper(message, html, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            if (html) {
                helper.setText(plainBody, htmlBody);
            } else {
                helper.setText(plainBody, false);
            }
            sender.send(message);
            return true;
        } catch (Exception ex) {
            log.error("Failed to send mail to {}: {}", to, ex.getMessage());
            return false;
        }
    }
}
