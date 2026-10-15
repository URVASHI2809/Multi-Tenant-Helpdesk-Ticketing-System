package com.helpdesk.repository;

import com.helpdesk.domain.Ticket;
import com.helpdesk.domain.TicketPriority;
import com.helpdesk.domain.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @Query("""
            select t from Ticket t
            where t.organization.id = :organizationId
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:assigneeId is null or t.assignedAgent.id = :assigneeId)
              and (:query is null or lower(t.subject) like lower(concat('%', :query, '%'))
                   or lower(t.description) like lower(concat('%', :query, '%')))
            """)
    Page<Ticket> searchOrganizationTickets(@Param("organizationId") Long organizationId,
            @Param("status") TicketStatus status, @Param("priority") TicketPriority priority,
            @Param("assigneeId") Long assigneeId, @Param("query") String query, Pageable pageable);

    @Query("""
            select t from Ticket t
            where t.organization.id = :organizationId and t.requester.id = :requesterId
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:assigneeId is null or t.assignedAgent.id = :assigneeId)
                    and (:query is null or lower(t.subject) like lower(concat('%', :query, '%'))
                            or lower(t.description) like lower(concat('%', :query, '%')))
            """)
    Page<Ticket> searchRequesterTickets(@Param("organizationId") Long organizationId,
            @Param("requesterId") Long requesterId, @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority, @Param("assigneeId") Long assigneeId,
            @Param("query") String query, Pageable pageable);

    java.util.Optional<Ticket> findByIdAndOrganization_Id(Long id, Long organizationId);
}