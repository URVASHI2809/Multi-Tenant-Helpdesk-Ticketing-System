package com.helpdesk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ticket_history")
@Getter
@Setter
@NoArgsConstructor
public class TicketHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_id", nullable = false)
    private UserAccount changedBy;

    @Column(nullable = false, length = 40)
    private String fieldName;

    @Column(length = 180)
    private String oldValue;

    @Column(length = 180)
    private String newValue;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public TicketHistory(Ticket ticket, UserAccount changedBy, String fieldName,
                         String oldValue, String newValue) {
        this.ticket = ticket;
        this.changedBy = changedBy;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}