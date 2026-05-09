package com.zaplink.repository;

import com.zaplink.dto.AdminUserResponse;
import com.zaplink.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("""
            SELECT new com.zaplink.dto.AdminUserResponse(
                u.id, u.username, u.email, u.role, u.isActive,
                (SELECT COUNT(l) FROM Link l WHERE l.userId = u.id),
                u.createdAt
            )
            FROM User u
            WHERE (:search IS NULL OR LOWER(u.username) LIKE :search OR LOWER(u.email) LIKE :search)
            AND (:isActive IS NULL OR u.isActive = :isActive)
            ORDER BY u.createdAt DESC
            """)
    List<AdminUserResponse> findUsersWithLinkCount(
            @Param("search") String search,
            @Param("isActive") Boolean isActive);

    // Column order: total_users, active_users, banned_users, new_users_last_30
    // Returns List<Object[]> — Spring Data JPA wraps native results; call .get(0) for the row.
    @Query(value = """
            SELECT COUNT(*),
                   SUM(CASE WHEN is_active = TRUE  THEN 1 ELSE 0 END),
                   SUM(CASE WHEN is_active = FALSE THEN 1 ELSE 0 END),
                   SUM(CASE WHEN created_at >= :since THEN 1 ELSE 0 END)
            FROM users
            """, nativeQuery = true)
    List<Object[]> getUserStats(@Param("since") LocalDateTime since);
}
