package com.helpdesk.config;

import com.helpdesk.domain.Organization;
import com.helpdesk.domain.Ticket;
import com.helpdesk.domain.TicketComment;
import com.helpdesk.domain.TicketHistory;
import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;
import com.helpdesk.repository.OrganizationRepository;
import com.helpdesk.repository.TicketCommentRepository;
import com.helpdesk.repository.TicketHistoryRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.repository.UserAccountRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("!prod")
public class DemoDataConfiguration {
    @Bean
    ApplicationRunner seedDemoData(OrganizationRepository organizations, UserAccountRepository users,
            TicketRepository tickets, TicketCommentRepository comments, TicketHistoryRepository history,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (organizations.count() > 0) {
                return;
            }

            Organization acme = organizations.save(new Organization("Acme Support", "acme"));
            Organization northstar = organizations.save(new Organization("Northstar Labs", "northstar"));
            // Development credentials: all accounts use Welcome1234!; admin@acme.test, agent@acme.test,
            // customer@acme.test, admin@northstar.test, agent@northstar.test, customer@northstar.test.
            String password = passwordEncoder.encode("Welcome1234!");

            UserAccount acmeAdmin = users.save(new UserAccount("admin@acme.test", "Avery Admin", password,
                    UserRole.ORG_ADMIN, acme));
            UserAccount acmeAgent = users.save(new UserAccount("agent@acme.test", "Jordan Agent", password,
                    UserRole.SUPPORT_AGENT, acme));
            UserAccount acmeCustomer = users.save(new UserAccount("customer@acme.test", "Casey Customer", password,
                    UserRole.CUSTOMER, acme));
            users.save(new UserAccount("admin@northstar.test", "Morgan Admin", password,
                    UserRole.ORG_ADMIN, northstar));
            users.save(new UserAccount("agent@northstar.test", "Riley Agent", password,
                    UserRole.SUPPORT_AGENT, northstar));
            users.save(new UserAccount("customer@northstar.test", "Taylor Customer", password,
                    UserRole.CUSTOMER, northstar));

            Ticket ticket = tickets.save(new Ticket(acme, acmeCustomer, "Cannot access billing portal",
                    "The billing portal returns an error after sign-in. This sample ticket belongs to Acme Support.",
                    TicketPriority.HIGH));
            ticket.setAssignedAgent(acmeAgent);
            tickets.save(ticket);
            history.save(new TicketHistory(ticket, acmeCustomer, "status", null, "OPEN"));
            history.save(new TicketHistory(ticket, acmeAdmin, "assignment", null, acmeAgent.getDisplayName()));
            comments.save(new TicketComment(ticket, acmeCustomer,
                    "It started happening this morning. Please let me know what you find.", false));
            comments.save(new TicketComment(ticket, acmeAgent,
                    "Check the identity provider token expiry before escalating.", true));
        };
    }
}