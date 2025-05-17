package com.example.notificationservice.service;
import com.example.notificationservice.config.RabbitMQConfig;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.Notification.NotificationStatus;
import com.example.notificationservice.repository.NotificationRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessor {
private final DummyUserService userService;

    private final NotificationRepository notificationRepository;

    @RabbitListener(queues = RabbitMQConfig.QUEUE, ackMode = "MANUAL")
    @Transactional
    public void processNotification(Long notificationId,
                                 Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        Notification notification = null;
        try {
            // 1. Fetch notification (throws if not found)
            notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> {
                    log.error("Notification not found: {}", notificationId);
                    safeReject(channel, tag, false);
                    return new AmqpRejectAndDontRequeueException("Notification not found");
                });

            // 2. Skip if already processed
            if (notification.getStatus() == NotificationStatus.SENT) {
                safeAck(channel, tag);
                return;
            }

            log.info("Processing notification ID: {}", notificationId);
            notification.setLastAttempt(LocalDateTime.now());

            // 3. Process notification
            boolean success = processNotificationByType(notification);
            
            if (success) {
                // 4a. Handle success
                markAsSent(notification, channel, tag);
            } else {
                // 4b. Handle temporary failure
                throw new RuntimeException("Processing failed - will retry");
            }
        } catch (IllegalArgumentException e) {
            // 5. Handle invalid notification type
            if (notification != null) {
                markAsFailed(notification, "Invalid type: " + e.getMessage());
            }
            safeReject(channel, tag, false);
        } catch (DataAccessException e) {
            // 6. Handle database failures
            log.error("Database error processing notification {}: {}", notificationId, e.getMessage());
            safeReject(channel, tag, true); // Requeue for retry
        } catch (Exception e) {
            // 7. Handle other failures
            log.error("Error processing notification {}: {}", notificationId, e.getMessage());
            if (notification != null) {
                handleRetryOrFailure(notification, channel, tag);
            } else {
                safeReject(channel, tag, false);
            }
        }
    }

    // ========== CORE METHODS ========== //
    private boolean processNotificationByType(Notification notification) {
        try {
            switch (notification.getNotificationType()) {
                case EMAIL: return sendEmail(notification);
                case SMS: return sendSms(notification);
                case IN_APP: return sendInApp(notification);
                default: 
                    throw new IllegalArgumentException(notification.getNotificationType().name());
            }
        } catch (Exception e) {
            log.error("Processing error: {}", e.getMessage());
            return false;
        }
    }

    private void handleRetryOrFailure(Notification notification, Channel channel, long tag) {
        int newRetryCount = notification.getRetryCount() + 1;
        notification.setRetryCount(newRetryCount);
        
        if (newRetryCount >= 3) { // After max retries
            markAsFailed(notification, "Max retries exceeded");
            safeReject(channel, tag, false); // Don't requeue
        } else {
            notification.setStatus(NotificationStatus.PENDING);
            notificationRepository.save(notification);
            safeReject(channel, tag, true); // Requeue for retry
            log.warn("RETRYING: Notification {} (attempt {})", notification.getId(), newRetryCount);
        }
    }

    // ========== STATUS UPDATES ========== //
    private void markAsSent(Notification notification, Channel channel, long tag) {
        try {
            notification.setStatus(NotificationStatus.SENT);
            notification.setRetryCount(0);
            notificationRepository.saveAndFlush(notification); // Force immediate write
            safeAck(channel, tag);
            log.info("SUCCESS: Notification {} marked as SENT", notification.getId());
        } catch (DataAccessException e) {
            log.error("Failed to update status to SENT: {}", e.getMessage());
            safeReject(channel, tag, true); // Requeue on DB failure
        }
    }

    private void markAsFailed(Notification notification, String reason) {
        notification.setStatus(NotificationStatus.FAILED);
        notificationRepository.save(notification);
        log.error("PERMANENT FAILURE: Notification {} - {}", notification.getId(), reason);
    }

    // ========== RABBITMQ UTILS ========== //
    private void safeAck(Channel channel, long tag) {
        try {
            if (channel.isOpen()) {
                channel.basicAck(tag, false);
            } else {
                log.error("Channel closed - message {} lost", tag);
            }
        } catch (IOException e) {
            log.error("Failed to ACK message: {}", e.getMessage());
        }
    }

    private void safeReject(Channel channel, long tag, boolean requeue) {
        try {
            if (channel.isOpen()) {
                channel.basicReject(tag, requeue);
            } else {
                log.error("Channel closed - message {} lost", tag);
            }
        } catch (IOException e) {
            log.error("Failed to REJECT message: {}", e.getMessage());
        }
    }

    // ========== SENDER METHODS ========== //
    // private boolean sendEmail(Notification notification) {
    //     log.info("Sending EMAIL to user {}", notification.getUserId());
    //     return true; // Implement actual logic
    // }

    // private boolean sendSms(Notification notification) {
    //     log.info("Sending SMS to user {}", notification.getUserId());
    //     return true; // Implement actual logic
    // }
    // DEMO FOR REPRESENTATION
    private boolean sendEmail(Notification notification) {
    String email = userService.getUserEmail(notification.getUserId());
    if (email == null) {
        log.error("No email found for user {}", notification.getUserId());
        return false;
    }

    log.info("Sending EMAIL to {}: {}", email, notification.getMessage());
    return true;
}

private boolean sendSms(Notification notification) {
    String phone = userService.getUserPhone(notification.getUserId());
    if (phone == null) {
        log.error("No phone number found for user {}", notification.getUserId());
        return false;
    }

    log.info("Sending SMS to {}: {}", phone, notification.getMessage());
    return true;
}


    // private boolean sendInApp(Notification notification) {
    //     log.info("Sending IN-APP to user {}", notification.getUserId());
    //     return true; // Implement actual logic
    // }
    //DEMO FOR REPRESENTATION
    private boolean sendInApp(Notification notification) {
    String name = userService.getUserName(notification.getUserId());
    if (name == null) {
        log.error("No user found for ID {}", notification.getUserId());
        return false;
    }

    log.info("Sending IN-APP notification to {}: {}", name, notification.getMessage());
    return true;
}

}