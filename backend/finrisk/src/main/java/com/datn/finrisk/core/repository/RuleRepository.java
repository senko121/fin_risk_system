package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

//Transaction B4: lấy tất cả các luật đang acctive trong database lleen để chuẩn bị chạy qua máy chấm điểm SpEl -> Transaction B5: TransactionRepository
@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {
 
    List<Rule> findByIsActiveTrue();
}