package com.vrms.controller;

import com.vrms.exception.ApiException;
import com.vrms.notification.Notification;
import com.vrms.notification.NotificationRepository;
import com.vrms.repository.CustomerRepository;
import com.vrms.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Emails and text messages sent by the RabbitMQ consumers (stored in MongoDB). */
@RestController
@Tag(name = "Notifications", description = "Emails and SMS sent through RabbitMQ")
public class NotificationController {

    private final NotificationRepository notifications;
    private final CustomerRepository customers;

    public NotificationController(NotificationRepository notifications, CustomerRepository customers) {
        this.notifications = notifications;
        this.customers = customers;
    }

    @GetMapping("/api/notifications")
    @PreAuthorize("hasAuthority('NOTIFICATION_READ')")
    public List<Notification> latest(@RequestParam(defaultValue = "200") int limit) {
        return notifications.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 1000))));
    }

    /** The signed-in customer's own messages. */
    @GetMapping("/api/me/notifications")
    @PreAuthorize("hasAuthority('BOOKING_OWN')")
    public List<Notification> mine() {
        var customer = CurrentUser.get().flatMap(customers::findByUser)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Complete your profile first"));
        return notifications.findByCustomerIdOrderByCreatedAtDesc(customer.getCustomerId(), PageRequest.of(0, 50));
    }
}
