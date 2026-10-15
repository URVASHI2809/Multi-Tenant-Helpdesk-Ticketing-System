package com.helpdesk.repository;

import com.helpdesk.domain.TicketHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {
    List<TicketHistory> findAllByTicket_IdOrderByCreatedAtAsc(Long ticketId);
}