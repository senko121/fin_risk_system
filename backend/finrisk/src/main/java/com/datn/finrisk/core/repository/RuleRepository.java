package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {
    // Tự động sinh ra câu SQL: SELECT * FROM rules WHERE is_active = true
    List<Rule> findByIsActiveTrue();
}