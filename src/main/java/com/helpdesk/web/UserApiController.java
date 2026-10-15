package com.helpdesk.web;

import com.helpdesk.api.CreateUserRequest;
import com.helpdesk.api.UserResponse;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.service.OrganizationUserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserApiController {
    private final OrganizationUserService userService;

    public UserApiController(OrganizationUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPPORT_AGENT')")
    public List<UserResponse> list(@AuthenticationPrincipal UserAccount actor) {
        return userService.listUsers(actor).stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<UserResponse> create(@AuthenticationPrincipal UserAccount actor,
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = UserResponse.from(userService.createUser(actor, request));
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }
}