package com.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helpdesk.api.CreateUserRequest;
import com.helpdesk.domain.Organization;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;
import com.helpdesk.repository.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrganizationUserServiceTest {
    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private OrganizationUserService userService;

    private Organization organization;
    private UserAccount admin;

    @BeforeEach
    void setUp() {
        organization = new Organization("Acme Support", "acme");
        organization.setId(10L);
        admin = new UserAccount("admin@acme.test", "Admin", "encoded", UserRole.ORG_ADMIN, organization);
        admin.setId(1L);
    }

    @Test
    void listUsersOnlyQueriesTheActorsOrganization() {
        UserAccount agent = new UserAccount("agent@acme.test", "Agent", "encoded",
                UserRole.SUPPORT_AGENT, organization);
        when(userRepository.findAllByOrganization_IdOrderByDisplayNameAsc(10L)).thenReturn(List.of(admin, agent));

        assertThat(userService.listUsers(admin)).containsExactly(admin, agent);
        verify(userRepository).findAllByOrganization_IdOrderByDisplayNameAsc(10L);
    }

    @Test
    void createUserHashesPasswordAndUsesActorsOrganization() {
        when(userRepository.existsByEmailIgnoreCase("new@acme.test")).thenReturn(false);
        when(passwordEncoder.encode("LongPassword123!")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount created = userService.createUser(admin,
                new CreateUserRequest("New Agent", "new@acme.test", "LongPassword123!", UserRole.SUPPORT_AGENT));

        assertThat(created.getEmail()).isEqualTo("new@acme.test");
        assertThat(created.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(created.getOrganization()).isSameAs(organization);
        assertThat(created.getRole()).isEqualTo(UserRole.SUPPORT_AGENT);
        verify(passwordEncoder).encode("LongPassword123!");
    }

    @Test
    void duplicateEmailReturnsConflictWithoutSavingOrEncoding() {
        when(userRepository.existsByEmailIgnoreCase("existing@acme.test")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(admin,
                new CreateUserRequest("Existing", "existing@acme.test", "LongPassword123!", UserRole.CUSTOMER)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any(CharSequence.class));
    }

    @Test
    void staffRolesIncludesAdministratorsAndAgents() {
        assertThat(userService.staffRoles()).containsExactly(UserRole.ORG_ADMIN, UserRole.SUPPORT_AGENT);
    }
}