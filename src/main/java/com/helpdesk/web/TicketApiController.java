package com.helpdesk.web;

import com.helpdesk.api.CreateCommentRequest;
import com.helpdesk.api.CreateTicketRequest;
import com.helpdesk.api.TicketResponse;
import com.helpdesk.api.UpdateTicketRequest;
import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.TicketStatus;
import com.helpdesk.domain.UserAccount;
import com.helpdesk.service.TicketService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketApiController {
    private final TicketService ticketService;

    public TicketApiController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public Page<TicketResponse> list(@AuthenticationPrincipal UserAccount actor,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable bounded = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                pageable.getSort());
        return ticketService.list(actor, status, priority, assignedAgentId,
                q == null || q.isBlank() ? null : q.trim(), bounded);
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@AuthenticationPrincipal UserAccount actor,
            @Valid @RequestBody CreateTicketRequest request) {
        TicketResponse created = ticketService.create(actor, request);
        return ResponseEntity.created(URI.create("/api/tickets/" + created.id())).body(created);
    }

    @GetMapping("/{ticketId}")
    public TicketResponse get(@AuthenticationPrincipal UserAccount actor, @PathVariable Long ticketId) {
        return ticketService.get(actor, ticketId);
    }

    @PatchMapping("/{ticketId}")
    public TicketResponse update(@AuthenticationPrincipal UserAccount actor, @PathVariable Long ticketId,
            @RequestBody UpdateTicketRequest request) {
        return ticketService.update(actor, ticketId, request);
    }

    @PostMapping("/{ticketId}/comments")
    public TicketResponse comment(@AuthenticationPrincipal UserAccount actor, @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ticketService.addComment(actor, ticketId, request);
    }
}