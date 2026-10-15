package com.helpdesk.api;

import com.helpdesk.domain.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 180) String subject,
        @NotBlank @Size(max = 8000) String description,
        @NotNull TicketPriority priority) {
}