package com.vrms.repository;

import com.vrms.model.Role;
import com.vrms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRoleInOrderByCreatedAtAsc(Collection<Role> roles);
    long countByRoleAndEnabledTrue(Role role);
}
