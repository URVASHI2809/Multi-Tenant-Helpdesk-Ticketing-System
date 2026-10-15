package com.helpdesk.api;

import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.TicketStatus;

public record UpdateTicketRequest(TicketStatus status, TicketPriority priority, Long assignedAgentId) {
}