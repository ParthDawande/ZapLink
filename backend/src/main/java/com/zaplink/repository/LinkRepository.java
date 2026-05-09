package com.zaplink.repository;

import com.zaplink.model.Link;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    Optional<Link> findByUserIdAndLongUrlAndIsActiveTrue(Long userId, String longUrl);

    long countByUserId(Long userId);

    // @SQLRestriction is NOT applied to JPQL bulk UPDATE in Hibernate 6, so this reaches
    // all links (including already-soft-deleted ones — advancing their timestamp is harmless).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Link l SET l.deletedAt = CURRENT_TIMESTAMP WHERE l.userId = :userId")
    int softDeleteAllLinksForUser(@Param("userId") Long userId);

    // Native SQL: @SQLRestriction is NOT applied to JPQL bulk UPDATE in Hibernate 6,
    // so deleted_at IS NULL is stated explicitly. Only touches non-deleted, currently-active
    // links — ADMIN_DISABLED links (is_active=false) are untouched by the is_active=TRUE guard.
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE links
            SET is_active = FALSE, disabled_reason = 'USER_BANNED'
            WHERE user_id = :userId AND is_active = TRUE AND deleted_at IS NULL
            """, nativeQuery = true)
    int cascadeBanForUser(@Param("userId") Long userId);

    // Third condition (disabled_reason = 'USER_BANNED') ensures ADMIN_DISABLED links are never restored.
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE links
            SET is_active = TRUE, disabled_reason = NULL
            WHERE user_id = :userId AND is_active = FALSE
              AND disabled_reason = 'USER_BANNED' AND deleted_at IS NULL
            """, nativeQuery = true)
    int cascadeUnbanForUser(@Param("userId") Long userId);

    @Query("""
        SELECT l, COUNT(c.id)
        FROM Link l
        LEFT JOIN Click c ON c.linkId = l.id
        WHERE l.id = :id
        GROUP BY l.id
        """)
    List<Object[]> findByIdWithClickCount(@Param("id") Long id);

    @Query(
        value = """
            SELECT l, COUNT(c.id)
            FROM Link l
            LEFT JOIN Click c ON c.linkId = l.id
            WHERE l.userId = :userId
            GROUP BY l.id
            ORDER BY l.createdAt DESC
            """,
        countQuery = """
            SELECT COUNT(l)
            FROM Link l
            WHERE l.userId = :userId
            """
    )
    Page<Object[]> findUserLinksWithClickCount(@Param("userId") Long userId, Pageable pageable);

    // Bypasses @SQLRestriction so admins can find soft-deleted links by id.
    @Query(value = "SELECT * FROM links WHERE id = :id", nativeQuery = true)
    Optional<Link> findByIdIncludingDeleted(@Param("id") Long id);

    // Native query — bypasses @SQLRestriction so admins can see soft-deleted links.
    // Column order: id, short_code, long_url, user_id, expires_at, is_active,
    //               disabled_reason, deleted_at, created_at,
    //               owner_id, owner_username, owner_email, click_count
    @Query(value = """
            SELECT l.id, l.short_code, l.long_url, l.user_id, l.expires_at, l.is_active,
                   l.disabled_reason, l.deleted_at, l.created_at,
                   u.id          AS owner_id,
                   u.username    AS owner_username,
                   u.email       AS owner_email,
                   COUNT(c.id)   AS click_count
            FROM links l
            LEFT JOIN users  u ON u.id = l.user_id
            LEFT JOIN clicks c ON c.link_id = l.id
            WHERE (:search IS NULL
                   OR LOWER(l.short_code) LIKE :search
                   OR LOWER(l.long_url)   LIKE :search
                   OR LOWER(u.username)   LIKE :search
                   OR LOWER(u.email)      LIKE :search)
              AND (   :statusFilter = 'all'
                   OR (:statusFilter = 'active'   AND l.is_active = TRUE  AND l.deleted_at IS NULL
                                                  AND (l.expires_at IS NULL OR l.expires_at > NOW()))
                   OR (:statusFilter = 'expired'  AND l.is_active = TRUE  AND l.deleted_at IS NULL
                                                  AND l.expires_at IS NOT NULL AND l.expires_at <= NOW())
                   OR (:statusFilter = 'disabled' AND l.is_active = FALSE AND l.deleted_at IS NULL)
                   OR (:statusFilter = 'deleted'  AND l.deleted_at IS NOT NULL))
            GROUP BY l.id, l.short_code, l.long_url, l.user_id, l.expires_at, l.is_active,
                     l.disabled_reason, l.deleted_at, l.created_at,
                     u.id, u.username, u.email
            ORDER BY l.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT l.id)
            FROM links l
            LEFT JOIN users u ON u.id = l.user_id
            WHERE (:search IS NULL
                   OR LOWER(l.short_code) LIKE :search
                   OR LOWER(l.long_url)   LIKE :search
                   OR LOWER(u.username)   LIKE :search
                   OR LOWER(u.email)      LIKE :search)
              AND (   :statusFilter = 'all'
                   OR (:statusFilter = 'active'   AND l.is_active = TRUE  AND l.deleted_at IS NULL
                                                  AND (l.expires_at IS NULL OR l.expires_at > NOW()))
                   OR (:statusFilter = 'expired'  AND l.is_active = TRUE  AND l.deleted_at IS NULL
                                                  AND l.expires_at IS NOT NULL AND l.expires_at <= NOW())
                   OR (:statusFilter = 'disabled' AND l.is_active = FALSE AND l.deleted_at IS NULL)
                   OR (:statusFilter = 'deleted'  AND l.deleted_at IS NOT NULL))
            """,
            nativeQuery = true)
    Page<Object[]> findAdminLinks(@Param("search") String search,
                                  @Param("statusFilter") String statusFilter,
                                  Pageable pageable);

    // Native query — bypasses @SQLRestriction to count soft-deleted links in reports.
    // Column order: total_links, active_links, expired_links, disabled_links,
    //               deleted_links, orphaned_links, new_links_last_30
    // Returns List<Object[]> — Spring Data JPA wraps native results; call .get(0) for the row.
    @Query(value = """
            SELECT
                SUM(CASE WHEN deleted_at IS NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN is_active = TRUE  AND deleted_at IS NULL
                              AND (expires_at IS NULL OR expires_at > NOW()) THEN 1 ELSE 0 END),
                SUM(CASE WHEN is_active = TRUE  AND deleted_at IS NULL
                              AND expires_at IS NOT NULL AND expires_at <= NOW() THEN 1 ELSE 0 END),
                SUM(CASE WHEN is_active = FALSE AND deleted_at IS NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN deleted_at IS NOT NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN user_id IS NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN created_at >= :since AND deleted_at IS NULL THEN 1 ELSE 0 END)
            FROM links
            """, nativeQuery = true)
    List<Object[]> getLinkStats(@Param("since") LocalDateTime since);
}
