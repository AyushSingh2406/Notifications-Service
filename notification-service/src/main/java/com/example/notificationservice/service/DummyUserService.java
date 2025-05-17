package com.example.notificationservice.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DummyUserService {

    private final Map<Long, DummyUser> users = Map.of(
        1L, new DummyUser("test1@example.com", "+911111111111", "Alice"),
        2L, new DummyUser("test2@example.com", "+922222222222", "Bob"),
        3L, new DummyUser(null, "+933333333333", "Charlie")
    );

    public String getUserEmail(Long userId) {
        DummyUser user = users.get(userId);
        return user != null ? user.getEmail() : null;
    }

    public String getUserPhone(Long userId) {
        DummyUser user = users.get(userId);
        return user != null ? user.getPhone() : null;
    }

    public String getUserName(Long userId) {
        DummyUser user = users.get(userId);
        return user != null ? user.getName() : null;
    }

    @Data
    @AllArgsConstructor
    static class DummyUser {
        private String email;
        private String phone;
        private String name;
    }
}
