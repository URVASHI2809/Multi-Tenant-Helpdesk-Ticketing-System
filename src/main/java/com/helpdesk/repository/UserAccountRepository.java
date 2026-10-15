package com.helpdesk.repository;

import com.helpdesk.domain.UserAccount;
import com.helpdesk.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<UserAccount> findAllByOrganization_IdOrderByDisplayNameAsc(Long organizationId);

    List<UserAccount> findAllByOrganization_IdAndRoleInOrderByDisplayNameAsc(
            Long organizationId, List<UserRole> roles);
}