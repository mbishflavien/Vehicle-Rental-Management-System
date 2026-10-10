package com.vrms.notification;

import com.vrms.config.RabbitConfig;
import com.vrms.messaging.RentalEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * RabbitMQ consumers that turn business events into emails and text messages. A failure is
 * recorded and rethrown so the broker retries; after the last retry the message is dead-lettered.
 * Already-delivered notifications are skipped, so a redelivered message never sends twice.
 */
@Component
public class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

    private final JavaMailSender mail;
    private final SmsGateway sms;
    private final MessageTemplates templates;
    private final NotificationRepository notifications;
    private final String from;
    private final String staffEmail;

    public NotificationListeners(JavaMailSender mail, SmsGateway sms, MessageTemplates templates,
                                 NotificationRepository notifications,
                                 @Value("${vrms.mail.from}") String from,
                                 @Value("${vrms.mail.staff-alerts}") String staffEmail) {
        this.mail = mail;
        this.sms = sms;
        this.templates = templates;
        this.notifications = notifications;
        this.from = from;
        this.staffEmail = staffEmail;
    }

    @RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
    public void onCustomerEmail(RentalEvent event) {
        if (event.customerEmail() == null) return;
        sendEmail(event, event.customerEmail(), templates.customerEmail(event), true);
    }

    @RabbitListener(queues = RabbitConfig.STAFF_ALERTS_QUEUE)
    public void onStaffAlert(RentalEvent event) {
        // Internal alert: not linked to the customer, so it never shows in their own message list
        sendEmail(event, staffEmail, templates.staffAlert(event), false);
    }

    @RabbitListener(queues = RabbitConfig.SMS_QUEUE)
    public void onSms(RentalEvent event) {
        String text = templates.customerSms(event);
        String phone = toInternational(event.customerPhone());
        if (text == null || phone == null || alreadySent(event, Notification.Channel.SMS, phone)) return;
        Notification n = record(event, Notification.Channel.SMS, phone, null, text);
        n.setProvider(sms.name());
        try {
            n.setStatus(sms.send(phone, text) ? Notification.Status.SENT : Notification.Status.SIMULATED);
            notifications.save(n);
        } catch (RuntimeException e) {
            fail(n, e);
            throw e;
        }
    }

    private void sendEmail(RentalEvent event, String to, MessageTemplates.Email email, boolean toCustomer) {
        if (alreadySent(event, Notification.Channel.EMAIL, to)) return;
        Notification n = record(event, Notification.Channel.EMAIL, to, email.subject(), email.html());
        if (!toCustomer) n.setCustomerId(null);
        n.setProvider("SMTP");
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(email.subject());
            helper.setText(email.html(), true);
            mail.send(message);
            n.setStatus(Notification.Status.SENT);
            notifications.save(n);
            log.info("Email '{}' sent to {}", email.subject(), to);
        } catch (MessagingException | RuntimeException e) {
            fail(n, e);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
    }

    private boolean alreadySent(RentalEvent event, Notification.Channel channel, String recipient) {
        return notifications.existsByEventIdAndChannelAndRecipientAndStatusNot(
                event.eventId(), channel, recipient, Notification.Status.FAILED);
    }

    private Notification record(RentalEvent event, Notification.Channel channel, String to, String subject, String body) {
        Notification n = new Notification();
        n.setCreatedAt(Instant.now());
        n.setEventId(event.eventId());
        n.setEventType(event.type().routingKey());
        n.setChannel(channel);
        n.setRecipient(to);
        n.setSubject(subject);
        n.setBody(body);
        n.setCustomerId(event.customerId());
        n.setContractId(event.contractId());
        return n;
    }

    private void fail(Notification n, Exception e) {
        n.setStatus(Notification.Status.FAILED);
        n.setError(e.getMessage());
        notifications.save(n);
        log.warn("{} to {} failed: {}", n.getChannel(), n.getRecipient(), e.getMessage());
    }

    /** "+250 788 123 456" or "0788123456" -> "+250788123456". */
    static String toInternational(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("07") && digits.length() == 10) return "+250" + digits.substring(1);
        if (digits.startsWith("250")) return "+" + digits;
        return digits;
    }
}
