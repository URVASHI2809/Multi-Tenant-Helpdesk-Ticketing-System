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
@Table(name = "ticket_comments")
@Getter
@Setter
@NoArgsConstructor
public class TicketComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccount author;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "is_internal", nullable = false)
    private boolean internal;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public TicketComment(Ticket ticket, UserAccount author, String body, boolean internal) {
        this.ticket = ticket;
        this.author = author;
        this.body = body;
        this.internal = internal;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}