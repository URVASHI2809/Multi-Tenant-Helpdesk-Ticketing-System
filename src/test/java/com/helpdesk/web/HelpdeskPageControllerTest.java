package com.helpdesk.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helpdesk.api.CreateTicketRequest;
import com.helpdesk.api.TicketResponse;
import com.helpdesk.api.UpdateTicketRequest;
import com.helpdesk.domain.Organization;
import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.TicketStatus;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;
import com.helpdesk.service.OrganizationUserService;
import com.helpdesk.service.TicketService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class HelpdeskPageControllerTest {
    @Mock
    private TicketService ticketService;
    @Mock
    private OrganizationUserService userService;
    @InjectMocks
    private HelpdeskPageController controller;

    private Organization organization;
    private UserAccount customer;
    private UserAccount agent;
    private TicketResponse ticket;

    @BeforeEach
    void setUp() {
        organization = new Organization("Acme Support", "acme");
        organization.setId(10L);
        customer = user(21L, "customer@acme.test", "Casey Customer", UserRole.CUSTOMER);
        agent = user(22L, "agent@acme.test", "Jordan Agent", UserRole.SUPPORT_AGENT);
        ticket = new TicketResponse(101L, "Billing access", "Cannot sign in", TicketPriority.HIGH,
                TicketStatus.OPEN, "Acme Support", customer.getId(), customer.getDisplayName(),
                agent.getId(), agent.getDisplayName(), Instant.now(), Instant.now(), List.of(), List.of());
    }

    @Test
    void homeAndLoginReturnTheirViewNames() {
        assertThat(controller.home()).isEqualTo("redirect:/app/tickets");
        assertThat(controller.login()).isEqualTo("login");
    }

    @Test
    void ticketQueueTrimsSearchAndPopulatesStaffModel() {
        when(userService.listUsers(agent)).thenReturn(List.of(customer, agent));
        when(ticketService.list(eq(agent), eq(TicketStatus.OPEN), eq(TicketPriority.HIGH), eq(22L),
                eq("billing"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ticket)));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.tickets(agent, " billing ", TicketStatus.OPEN, TicketPriority.HIGH,
                22L, 2, model);

        assertThat(view).isEqualTo("tickets");
        assertThat(model.get("isStaff")).isEqualTo(true);
        assertThat(model.get("q")).isEqualTo(" billing ");
        assertThat(model.get("agents")).isEqualTo(List.of(agent));
        assertThat(model.get("tickets")).isInstanceOf(PageImpl.class);
    }

    @Test
    void blankTicketQueueSearchIsTreatedAsNoQuery() {
        when(userService.listUsers(customer)).thenReturn(List.of(customer, agent));
        when(ticketService.list(eq(customer), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.tickets(customer, "   ", null, null, null, 0, model);

        assertThat(view).isEqualTo("tickets");
        assertThat(model.get("q")).isEqualTo("   ");
        verify(ticketService).list(eq(customer), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void ticketDetailPopulatesActivityAndCustomerModel() {
        when(userService.listUsers(customer)).thenReturn(List.of(customer, agent));
        when(ticketService.get(customer, 101L)).thenReturn(ticket);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.ticket(customer, 101L, model);

        assertThat(view).isEqualTo("ticket-detail");
        assertThat(model.get("ticket")).isSameAs(ticket);
        assertThat(model.get("isStaff")).isEqualTo(false);
        assertThat(model.get("agents")).isEqualTo(List.of(agent));
    }

    @Test
    void createTicketRedirectsToCreatedTicket() {
        when(ticketService.create(eq(customer), any(CreateTicketRequest.class))).thenReturn(ticket);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String destination = controller.createTicket(customer,
                new CreateTicketRequest("Billing access", "Cannot sign in", TicketPriority.HIGH), redirect);

        assertThat(destination).isEqualTo("redirect:/app/tickets/101");
        assertThat(redirect.getFlashAttributes().get("notice")).isEqualTo("Ticket created");
    }

    @Test
    void updateTicketRedirectsAndSetsConfirmation() {
        when(ticketService.update(eq(agent), eq(101L), any(UpdateTicketRequest.class))).thenReturn(ticket);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String destination = controller.update(agent, 101L,
                new UpdateTicketRequest(TicketStatus.RESOLVED, null, null), redirect);

        assertThat(destination).isEqualTo("redirect:/app/tickets/101");
        assertThat(redirect.getFlashAttributes().get("notice")).isEqualTo("Ticket updated");
        verify(ticketService).update(agent, 101L,
                new UpdateTicketRequest(TicketStatus.RESOLVED, null, null));
    }

    private UserAccount user(Long id, String email, String displayName, UserRole role) {
        UserAccount user = new UserAccount(email, displayName, "encoded", role, organization);
        user.setId(id);
        return user;
    }
}