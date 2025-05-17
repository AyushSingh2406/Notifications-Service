package com.example.notificationservice.dto;

import com.example.notificationservice.model.Notification.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Message cannot be empty")
    private String message;

    @NotNull(message = "Notification type is required")
    private NotificationType notificationType;
}