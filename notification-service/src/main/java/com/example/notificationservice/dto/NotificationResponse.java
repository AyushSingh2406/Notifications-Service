package com.example.notificationservice.dto;

import com.example.notificationservice.model.Notification.NotificationStatus;
import com.example.notificationservice.model.Notification.NotificationType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long id;
    private Long userId;
    private String message;
    private NotificationType notificationType;
    private NotificationStatus status;
    private int retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}