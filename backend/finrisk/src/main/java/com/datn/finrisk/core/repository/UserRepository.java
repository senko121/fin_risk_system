// package com.datn.finrisk.core.repository;

// import com.datn.finrisk.core.entities.User;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.Pageable;
// import org.springframework.data.jpa.repository.EntityGraph; 
// import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.data.jpa.repository.Query;
// import org.springframework.data.repository.query.Param;
// import org.springframework.stereotype.Repository;

// import java.util.Optional;

// @Repository
// public interface UserRepository extends JpaRepository<User, Long> {

//     @EntityGraph(attributePaths = {"userSecurity"})
//     Optional<User> findByUsername(String username);

//     @EntityGraph(attributePaths = {"userSecurity"}) 
//     Optional<User> findById(Long id);

//     @Query("SELECT u FROM User u WHERE " +
//            "(:search IS NULL OR :search = '' OR " +
//            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
//            "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
//            "u.phoneNumber LIKE CONCAT('%', :search, '%') OR " +
//            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
//     Page<User> searchUsers(@Param("search") String search, Pageable pageable);
// }


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

    // 🚀 Dùng EntityGraph để khi lấy User là lôi luôn cả Security và Behavior (Trị dứt điểm Lazy Load)
    @EntityGraph(attributePaths = {"userSecurity", "behaviorProfile"})
    Optional<User> findByUsername(String username);

    // 🚀 Ghi đè hàm mặc định của JPA để gắn thêm bùa EntityGraph
    @EntityGraph(attributePaths = {"userSecurity", "behaviorProfile"}) 
    Optional<User> findById(Long id);

    // 🚀 Tối ưu cho trang danh sách Admin
    // Không dùng EntityGraph ở đây nữa vì đã dùng JOIN FETCH trực tiếp trong câu SQL để kiểm soát tốt hơn
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