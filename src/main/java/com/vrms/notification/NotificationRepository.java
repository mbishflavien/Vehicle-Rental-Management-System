package com.vrms.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Notification> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
    boolean existsByEventIdAndChannelAndRecipientAndStatusNot(UUID eventId, Notification.Channel channel,
                                                              String recipient, Notification.Status status);
}
