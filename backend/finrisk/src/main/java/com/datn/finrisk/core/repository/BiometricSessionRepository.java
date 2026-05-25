package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.BiometricSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface BiometricSessionRepository extends JpaRepository<BiometricSession, Long> {

    Optional<BiometricSession> findBySessionToken(String token);

    // ← THÊM QUERY NÀY
    @Query("SELECT bs FROM BiometricSession bs " +
           "LEFT JOIN FETCH bs.user u " +
           "LEFT JOIN FETCH bs.transaction t " +
           "WHERE bs.sessionToken = :token")
    Optional<BiometricSession> findBySessionTokenEager(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("SELECT b FROM BiometricSession b WHERE b.sessionToken = :token")
    Optional<BiometricSession> findBySessionTokenForUpdate(@Param("token") String token);

    @Modifying
    @Transactional
    void deleteByExpiresAtBefore(LocalDateTime time);
}