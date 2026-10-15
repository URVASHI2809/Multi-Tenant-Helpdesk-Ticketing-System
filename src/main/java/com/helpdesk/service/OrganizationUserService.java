package com.helpdesk.service;

import com.helpdesk.api.CreateUserRequest;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;
import com.helpdesk.repository.UserAccountRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationUserService {
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public OrganizationUserService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> listUsers(UserAccount actor) {
        return userRepository.findAllByOrganization_IdOrderByDisplayNameAsc(actor.getOrganization().getId());
    }

    @Transactional
    public UserAccount createUser(UserAccount actor, CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        UserAccount user = new UserAccount(request.email(), request.displayName(),
                passwordEncoder.encode(request.password()), request.role(), actor.getOrganization());
        return userRepository.save(user);
    }

    public List<UserRole> staffRoles() {
        return List.of(UserRole.ORG_ADMIN, UserRole.SUPPORT_AGENT);
    }
}