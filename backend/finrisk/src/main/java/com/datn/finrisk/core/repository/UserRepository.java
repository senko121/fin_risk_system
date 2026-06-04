 

package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph; 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"userSecurity", "behaviorProfile"})
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = {"userSecurity", "behaviorProfile"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithDetails(@Param("id") Long id);

   
    @Query(value = """
        SELECT DISTINCT u FROM User u 
        LEFT JOIN FETCH u.userSecurity 
        LEFT JOIN FETCH u.behaviorProfile 
        WHERE (:search IS NULL OR :search = '' OR 
               LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR 
               LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR 
               u.phoneNumber LIKE CONCAT('%', :search, '%') OR 
               LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
    """, countQuery = "SELECT COUNT(u) FROM User u")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
}