// package com.datn.finrisk.core.repository;

// import com.datn.finrisk.core.entities.UserContact;
// import org.springframework.data.jpa.repository.JpaRepository;
// import java.util.List;

// public interface UserContactRepository extends JpaRepository<UserContact, Long> {
//     List<UserContact> findByOwnerIdOrderByIsPinnedDescCreatedAtDesc(Long userId);
// }


package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.UserContact;
import com.datn.finrisk.application.dtos.IContactLastTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {
    
@Query(value = "SELECT uc.id as id, uc.contact_name as contactName, " +
       "uc.contact_account_number as contactAccountNumber, uc.is_pinned as isPinned, " +
       "(SELECT t.amount FROM transactions t " +
       " JOIN accounts a ON t.from_account_id = a.id " +
       " WHERE t.to_account_number = uc.contact_account_number " +
       " AND a.user_id = :userId " + // 🔥 Chỉ lấy giao dịch CỦA CHÍNH BRO gửi đi
       " AND t.status = 'SUCCESS' " +
       " ORDER BY t.created_at DESC LIMIT 1) as lastAmount, " +
       "(SELECT t.created_at FROM transactions t " +
       " JOIN accounts a ON t.from_account_id = a.id " +
       " WHERE t.to_account_number = uc.contact_account_number " +
       " AND a.user_id = :userId " + 
       " AND t.status = 'SUCCESS' " +
       " ORDER BY t.created_at DESC LIMIT 1) as lastDate " +
       "FROM user_contacts uc WHERE uc.user_id = :userId " +
       "ORDER BY uc.is_pinned DESC, uc.created_at DESC", nativeQuery = true)
List<IContactLastTransaction> findContactsWithLastTransaction(@Param("userId") Long userId);
}