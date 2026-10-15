package com.helpdesk.repository;

import com.helpdesk.domain.TicketComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findAllByTicket_IdOrderByCreatedAtAsc(Long ticketId);
}