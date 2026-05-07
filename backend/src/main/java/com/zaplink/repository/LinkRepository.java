package com.zaplink.repository;

import com.zaplink.model.Link;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    Optional<Link> findByUserIdAndLongUrlAndIsActiveTrue(Long userId, String longUrl);

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
}
