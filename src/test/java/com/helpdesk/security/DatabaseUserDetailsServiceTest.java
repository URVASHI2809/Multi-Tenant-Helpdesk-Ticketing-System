package com.helpdesk.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.helpdesk.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class DatabaseUserDetailsServiceTest {
    private final UserAccountRepository userRepository = org.mockito.Mockito.mock(UserAccountRepository.class);
    private final DatabaseUserDetailsService userDetailsService = new DatabaseUserDetailsService(userRepository);

    @Test
    void unknownEmailThrowsUsernameNotFound() {
        when(userRepository.findByEmailIgnoreCase("missing@acme.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing@acme.test"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid email or password");
    }
}