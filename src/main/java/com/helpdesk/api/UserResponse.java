package com.helpdesk.api;

import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;

public record UserResponse(Long id, String displayName, String email, UserRole role) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole());
    }
}