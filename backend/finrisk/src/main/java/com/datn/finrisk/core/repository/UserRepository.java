package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.User;
import org.springframework.data.jpa.repository.EntityGraph; 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"userSecurity"})
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = {"userSecurity"}) 
    Optional<User> findById(Long id);
}