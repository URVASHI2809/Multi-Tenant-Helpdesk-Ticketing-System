package com.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helpdesk.api.CreateCommentRequest;
import com.helpdesk.domain.Organization;
import com.helpdesk.domain.Ticket;
import com.helpdesk.domain.TicketComment;
import com.helpdesk.domain.TicketHistory;
import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.TicketStatus;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;
import com.helpdesk.api.TicketResponse;
import com.helpdesk.repository.TicketCommentRepository;
import com.helpdesk.repository.TicketHistoryRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketCommentRepository commentRepository;
    @Mock
    private TicketHistoryRepository historyRepository;
    @Mock
    private UserAccountRepository userRepository;

    @InjectMocks
    private TicketService ticketService;

    private Organization organization;
    private UserAccount customer;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        organization = new Organization("Acme", "acme");
        organization.setId(10L);
        customer = user(21L, "customer@acme.test", UserRole.CUSTOMER, organization);
        ticket = new Ticket(organization, customer, "Billing access", "Unable to sign in", TicketPriority.HIGH);
        ticket.setId(101L);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());
    }

    @Test
    void customerQueueUsesOrganizationAndRequesterScope() {
        when(ticketRepository.searchRequesterTickets(eq(10L), eq(21L), eq(TicketStatus.OPEN),
                eq(TicketPriority.HIGH), eq(null), eq("billing"), any()))
                .thenReturn(new PageImpl<>(List.of(ticket)));

        var page = ticketService.list(customer, TicketStatus.OPEN, TicketPriority.HIGH, null,
                "billing", PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).subject()).isEqualTo("Billing access");
        verify(ticketRepository).searchRequesterTickets(eq(10L), eq(21L), eq(TicketStatus.OPEN),
                eq(TicketPriority.HIGH), eq(null), eq("billing"), any());
    }

    @Test
    void staffQueueUsesOrganizationScopeAndAllFilters() {
        UserAccount agent = user(22L, "agent@acme.test", UserRole.SUPPORT_AGENT, organization);
        when(userRepository.findById(22L)).thenReturn(Optional.of(agent));
        when(ticketRepository.searchOrganizationTickets(eq(10L), eq(TicketStatus.OPEN), eq(TicketPriority.HIGH),
                eq(22L), eq("billing"), any())).thenReturn(new PageImpl<>(List.of(ticket)));

        var page = ticketService.list(agent, TicketStatus.OPEN, TicketPriority.HIGH, 22L,
                "billing", PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(TicketResponse::id).containsExactly(101L);
        verify(ticketRepository).searchOrganizationTickets(eq(10L), eq(TicketStatus.OPEN), eq(TicketPriority.HIGH),
                eq(22L), eq("billing"), any());
    }

    @Test
    void createTrimsTicketFieldsAndRecordsInitialStatus() {
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            saved.setId(102L);
            return saved;
        });
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(102L)).thenReturn(List.of());
        when(historyRepository.findAllByTicket_IdOrderByCreatedAtAsc(102L)).thenReturn(List.of());

        TicketResponse response = ticketService.create(customer,
                new com.helpdesk.api.CreateTicketRequest("  Login issue  ", "  Cannot sign in  ",
                        TicketPriority.NORMAL));

        assertThat(response.subject()).isEqualTo("Login issue");
        assertThat(response.description()).isEqualTo("Cannot sign in");
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFieldName()).isEqualTo("status");
        assertThat(history.getValue().getNewValue()).isEqualTo("OPEN");
    }

    @Test
    void staffUpdateChangesStatusPriorityAndAssignmentAndRecordsHistory() {
        UserAccount agent = user(22L, "agent@acme.test", UserRole.SUPPORT_AGENT, organization);
        when(ticketRepository.findByIdAndOrganization_Id(101L, 10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(22L)).thenReturn(Optional.of(agent));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());
        when(historyRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());

        TicketResponse response = ticketService.update(agent, 101L,
                new com.helpdesk.api.UpdateTicketRequest(TicketStatus.IN_PROGRESS, TicketPriority.URGENT, 22L));

        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.priority()).isEqualTo(TicketPriority.URGENT);
        assertThat(response.assignedAgentId()).isEqualTo(22L);
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository, org.mockito.Mockito.times(2)).save(history.capture());
        assertThat(history.getAllValues()).extracting(TicketHistory::getFieldName)
                .containsExactly("status", "assignment");
    }
//232434535q3e
    @Test
    void staffNoOpUpdateDoesNotCreateHistory() {
        UserAccount agent = user(22L, "agent@acme.test", UserRole.SUPPORT_AGENT, organization);
        when(ticketRepository.findByIdAndOrganization_Id(101L, 10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());
        when(historyRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());

        ticketService.update(agent, 101L, new com.helpdesk.api.UpdateTicketRequest(null, null, null));

        verify(historyRepository, never()).save(any(TicketHistory.class));
    }

    @Test
    void unchangedStatusAndAssignmentDoNotAddHistory() {
        UserAccount agent = user(22L, "agent@acme.test", UserRole.SUPPORT_AGENT, organization);
        ticket.setAssignedAgent(agent);
        when(ticketRepository.findByIdAndOrganization_Id(101L, 10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(22L)).thenReturn(Optional.of(agent));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());
        when(historyRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());

        TicketResponse response = ticketService.update(agent, 101L,
                new com.helpdesk.api.UpdateTicketRequest(TicketStatus.OPEN, null, 22L));

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.assignedAgentId()).isEqualTo(22L);
        verify(historyRepository, never()).save(any(TicketHistory.class));
    }

    @Test
    void reassignmentFromExistingAgentRecordsTheirName() {
        UserAccount actor = user(24L, "actor@acme.test", UserRole.SUPPORT_AGENT, organization);
        UserAccount previousAgent = user(22L, "previous@acme.test", UserRole.SUPPORT_AGENT, organization);
        UserAccount nextAgent = user(23L, "admin@acme.test", UserRole.ORG_ADMIN, organization);
        ticket.setAssignedAgent(previousAgent);
        when(ticketRepository.findByIdAndOrganization_Id(101L, 10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(23L)).thenReturn(Optional.of(nextAgent));
        when(ticketRepository.save(ticket)).thenReturn(ticket);
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());
        when(historyRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());

        TicketResponse response = ticketService.update(actor, 101L,
                new com.helpdesk.api.UpdateTicketRequest(null, null, 23L));

        assertThat(response.assignedAgentId()).isEqualTo(23L);
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getOldValue()).isEqualTo(previousAgent.getDisplayName());
    }

    @Test
    void customerCannotUpdateTicket() {
        assertThatThrownBy(() -> ticketService.update(customer, 101L,
                new com.helpdesk.api.UpdateTicketRequest(TicketStatus.CLOSED, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(ticketRepository, never()).findByIdAndOrganization_Id(any(), any());
    }

    @Test
    void assignmentRejectsCustomerAndCrossOrganizationUsers() {
        UserAccount agent = user(22L, "agent@acme.test", UserRole.SUPPORT_AGENT, organization);
        UserAccount anotherCustomer = user(23L, "another@acme.test", UserRole.CUSTOMER, organization);
        Organization otherOrganization = new Organization("Northstar", "northstar");
        otherOrganization.setId(11L);
        UserAccount otherAgent = user(31L, "agent@northstar.test", UserRole.SUPPORT_AGENT, otherOrganization);
        when(ticketRepository.findByIdAndOrganization_Id(101L, 10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(23L)).thenReturn(Optional.of(anotherCustomer));
        when(userRepository.findById(31L)).thenReturn(Optional.of(otherAgent));

        assertThatThrownBy(() -> ticketService.update(agent, 101L,
                new com.helpdesk.api.UpdateTicketRequest(null, null, 23L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> ticketService.update(agent, 101L,
                new com.helpdesk.api.UpdateTicketRequest(null, null, 31L)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void addingPublicCommentTrimsBodyAndReturnsActivity() {
        UserAccount agent = user(22L, "agent@acme.test", UserRole.SUPPORT_AGENT, organization);
        when(ticketRepository.findByIdAndOrganization_Id(101L, 10L)).thenReturn(Optional.of(ticket));
        when(commentRepository.save(any(TicketComment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());
        when(historyRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());

        ticketService.addComment(agent, 101L, new CreateCommentRequest("  Please retry  ", false));

        verify(commentRepository).save(argThat(comment -> comment.getBody().equals("Please retry")
                && !comment.isInternal() && comment.getAuthor().equals(agent)));
    }

    @Test
    void customersDoNotReceiveInternalNotes() {
        UserAccount agent = user(22L, "agent@acme.test", UserRole.SUPPORT_AGENT, organization);
        TicketComment publicReply = new TicketComment(ticket, agent, "We are checking this.", false);
        publicReply.setId(1L);
        publicReply.setCreatedAt(Instant.now());
        TicketComment internalNote = new TicketComment(ticket, agent, "Review identity logs.", true);
        internalNote.setId(2L);
        internalNote.setCreatedAt(Instant.now());
        when(ticketRepository.findByIdAndOrganization_Id(101L, 10L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L))
                .thenReturn(List.of(publicReply, internalNote));
        when(historyRepository.findAllByTicket_IdOrderByCreatedAtAsc(101L)).thenReturn(List.of());

        var response = ticketService.get(customer, 101L);

        assertThat(response.comments()).extracting(TicketResponse.CommentResponse::body)
            .containsExactly("We are checking this.");
        assertThat(response.comments()).noneMatch(comment -> comment.internal());
    }

    @Test
    void customerCannotReadAnotherCustomersTicketInSameOrganization() {
        UserAccount otherCustomer = user(23L, "other@acme.test", UserRole.CUSTOMER, organization);
        ticket.setRequester(otherCustomer);
        when(ticketRepository.findByIdAndOrganization_Id(101L, 10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.get(customer, 101L))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void ticketLookupIsScopedToTheSignedInOrganizationsId() {
        Organization otherOrganization = new Organization("Northstar", "northstar");
        otherOrganization.setId(11L);
        UserAccount otherCustomer = user(31L, "customer@northstar.test", UserRole.CUSTOMER, otherOrganization);
        when(ticketRepository.findByIdAndOrganization_Id(101L, 11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.get(otherCustomer, 101L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(ticketRepository).findByIdAndOrganization_Id(101L, 11L);
    }

    @Test
    void customerCannotCreateAnInternalNote() {
        assertThatThrownBy(() -> ticketService.addComment(customer, 101L,
                new CreateCommentRequest("Private note", true)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void missingInternalFlagDefaultsToPublicComment() {
        assertThat(new CreateCommentRequest("Public reply", null).internal()).isFalse();
    }

    private UserAccount user(Long id, String email, UserRole role, Organization tenant) {
        UserAccount user = new UserAccount(email, email, "unused", role, tenant);
        user.setId(id);
        return user;
    }
}