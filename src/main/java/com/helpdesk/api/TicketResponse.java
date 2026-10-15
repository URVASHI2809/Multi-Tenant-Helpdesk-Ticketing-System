package com.helpdesk.api;

import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.TicketStatus;
import java.time.Instant;
import java.util.List;

public record TicketResponse(
        Long id,
        String subject,
        String description,
        TicketPriority priority,
        TicketStatus status,
        String organization,
        Long requesterId,
        String requesterName,
        Long assignedAgentId,
        String assignedAgentName,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> comments,
        List<HistoryResponse> history) {
    public record CommentResponse(Long id, String author, String body, boolean internal, Instant createdAt) {}
    public record HistoryResponse(Long id, String changedBy, String field, String oldValue,
                                  String newValue, Instant createdAt) {}
}