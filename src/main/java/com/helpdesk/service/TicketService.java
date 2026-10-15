package com.helpdesk.service;

import com.helpdesk.api.CreateCommentRequest;
import com.helpdesk.api.CreateTicketRequest;
import com.helpdesk.api.TicketResponse;
import com.helpdesk.api.UpdateTicketRequest;
import com.helpdesk.domain.Ticket;
import com.helpdesk.domain.TicketComment;
import com.helpdesk.domain.TicketHistory;
import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.TicketStatus;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;
import com.helpdesk.repository.TicketCommentRepository;
import com.helpdesk.repository.TicketHistoryRepository;
import com.helpdesk.repository.TicketRepository;
import com.helpdesk.repository.UserAccountRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketHistoryRepository historyRepository;
    private final UserAccountRepository userRepository;

    public TicketService(TicketRepository ticketRepository, TicketCommentRepository commentRepository,
            TicketHistoryRepository historyRepository, UserAccountRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> list(UserAccount actor, TicketStatus status, TicketPriority priority,
            Long assigneeId, String query, Pageable pageable) {
        validateAssignee(actor, assigneeId);
        Long organizationId = actor.getOrganization().getId();
        Page<Ticket> tickets = actor.getRole() == UserRole.CUSTOMER
                ? ticketRepository.searchRequesterTickets(organizationId, actor.getId(), status, priority,
                    assigneeId, query, pageable)
                : ticketRepository.searchOrganizationTickets(organizationId, status, priority,
                    assigneeId, query, pageable);
        return tickets.map(ticket -> toResponse(ticket, actor, false));
    }

    @Transactional
    public TicketResponse create(UserAccount requester, CreateTicketRequest request) {
        Ticket ticket = ticketRepository.save(new Ticket(requester.getOrganization(), requester,
                request.subject().trim(), request.description().trim(), request.priority()));
        historyRepository.save(new TicketHistory(ticket, requester, "status", null, TicketStatus.OPEN.name()));
        return toResponse(ticket, requester, true);
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UserAccount actor, Long ticketId) {
        return toResponse(requireVisibleTicket(actor, ticketId), actor, true);
    }

    @Transactional
    public TicketResponse update(UserAccount actor, Long ticketId, UpdateTicketRequest request) {
        requireStaff(actor);
        Ticket ticket = findInOrganization(actor, ticketId);

        if (request.status() != null && request.status() != ticket.getStatus()) {
            historyRepository.save(new TicketHistory(ticket, actor, "status",
                    ticket.getStatus().name(), request.status().name()));
            ticket.setStatus(request.status());
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.assignedAgentId() != null) {
            UserAccount assignee = findStaffMember(actor, request.assignedAgentId());
            Long previousId = ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getId();
            if (!assignee.getId().equals(previousId)) {
                historyRepository.save(new TicketHistory(ticket, actor, "assignment",
                        ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getDisplayName(),
                        assignee.getDisplayName()));
                ticket.setAssignedAgent(assignee);
            }
        }
        return toResponse(ticketRepository.save(ticket), actor, true);
    }

    @Transactional
    public TicketResponse addComment(UserAccount actor, Long ticketId, CreateCommentRequest request) {
        if (request.internal() && actor.getRole() == UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customers cannot create internal notes");
        }
        Ticket ticket = requireVisibleTicket(actor, ticketId);
        commentRepository.save(new TicketComment(ticket, actor, request.body().trim(), request.internal()));
        return toResponse(ticket, actor, true);
    }

    private Ticket requireVisibleTicket(UserAccount actor, Long ticketId) {
        Ticket ticket = findInOrganization(actor, ticketId);
        if (actor.getRole() == UserRole.CUSTOMER
                && !ticket.getRequester().getId().equals(actor.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
        }
        return ticket;
    }

    private Ticket findInOrganization(UserAccount actor, Long ticketId) {
        return ticketRepository.findByIdAndOrganization_Id(ticketId, actor.getOrganization().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    private void validateAssignee(UserAccount actor, Long assigneeId) {
        if (assigneeId == null) {
            return;
        }
        findStaffMember(actor, assigneeId);
    }

    private UserAccount findStaffMember(UserAccount actor, Long userId) {
        UserAccount user = userRepository.findById(userId)
                .filter(candidate -> candidate.getOrganization().getId().equals(actor.getOrganization().getId()))
                .filter(candidate -> candidate.getRole() != UserRole.CUSTOMER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Assignee must be an administrator or agent in your organization"));
        return user;
    }

    private void requireStaff(UserAccount actor) {
        if (actor.getRole() == UserRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff access is required");
        }
    }

    private TicketResponse toResponse(Ticket ticket, UserAccount actor, boolean includeActivity) {
        boolean canReadInternal = actor.getRole() != UserRole.CUSTOMER;
        List<TicketResponse.CommentResponse> comments = includeActivity
                ? commentRepository.findAllByTicket_IdOrderByCreatedAtAsc(ticket.getId()).stream()
                        .filter(comment -> canReadInternal || !comment.isInternal())
                        .map(comment -> new TicketResponse.CommentResponse(comment.getId(),
                                comment.getAuthor().getDisplayName(), comment.getBody(), comment.isInternal(),
                                comment.getCreatedAt()))
                        .toList()
                : List.of();
        List<TicketResponse.HistoryResponse> history = includeActivity
                ? historyRepository.findAllByTicket_IdOrderByCreatedAtAsc(ticket.getId()).stream()
                        .map(item -> new TicketResponse.HistoryResponse(item.getId(),
                                item.getChangedBy().getDisplayName(), item.getFieldName(), item.getOldValue(),
                                item.getNewValue(), item.getCreatedAt()))
                        .toList()
                : List.of();

        return new TicketResponse(ticket.getId(), ticket.getSubject(), ticket.getDescription(),
                ticket.getPriority(), ticket.getStatus(), ticket.getOrganization().getName(),
                ticket.getRequester().getId(), ticket.getRequester().getDisplayName(),
                ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getId(),
                ticket.getAssignedAgent() == null ? null : ticket.getAssignedAgent().getDisplayName(),
                ticket.getCreatedAt(), ticket.getUpdatedAt(), comments, history);
    }
}