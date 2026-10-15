package com.helpdesk.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.helpdesk.repository.OrganizationRepository;
import com.helpdesk.repository.TicketCommentRepository;
import com.helpdesk.repository.TicketHistoryRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoDataConfigurationTest {
    @Mock
    private OrganizationRepository organizations;
    @Mock
    private UserAccountRepository users;
    @Mock
    private TicketRepository tickets;
    @Mock
    private TicketCommentRepository comments;
    @Mock
    private TicketHistoryRepository history;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void skipsDemoSeedingWhenAnOrganizationAlreadyExists() throws Exception {
        when(organizations.count()).thenReturn(1L);
        ApplicationRunner runner = new DemoDataConfiguration().seedDemoData(
                organizations, users, tickets, comments, history, passwordEncoder);
        ApplicationArguments arguments = new DefaultApplicationArguments(new String[0]);

        runner.run(arguments);

        verify(organizations).count();
        verifyNoInteractions(users, tickets, comments, history, passwordEncoder);
    }
}