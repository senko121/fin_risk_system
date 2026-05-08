 

package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.UserContact;
import com.datn.finrisk.application.dtos.IContactLastTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {
    
@Query(value = """
    SELECT 
        uc.id as id, 
        uc.contact_name as contactName, 
        uc.contact_account_number as contactAccountNumber, 
        uc.is_pinned as isPinned,
        t.amount as lastAmount, 
        t.created_at as lastDate
    FROM user_contacts uc
    LEFT JOIN (
        SELECT from_account_id, to_account_number, amount, created_at,
               ROW_NUMBER() OVER (PARTITION BY to_account_number ORDER BY created_at DESC) as rn
        FROM transactions
        WHERE status = 'SUCCESS'
          AND from_account_id = (SELECT id FROM accounts WHERE user_id = :userId LIMIT 1)
    ) t ON uc.contact_account_number = t.to_account_number AND t.rn = 1
    WHERE uc.user_id = :userId
    ORDER BY uc.is_pinned DESC, uc.created_at DESC
""", nativeQuery = true)
List<IContactLastTransaction> findContactsWithLastTransaction(@Param("userId") Long userId);
}