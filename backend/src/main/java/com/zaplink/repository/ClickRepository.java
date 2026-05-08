package com.zaplink.repository;

import com.zaplink.model.Click;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickRepository extends JpaRepository<Click, Long> {

    long countByLinkId(Long linkId);

    @Query("SELECT COUNT(c) FROM Click c WHERE c.linkId = :linkId AND c.clickedAt >= :since")
    long countByLinkIdSince(@Param("linkId") Long linkId, @Param("since") LocalDateTime since);

    // Uses standard JPQL YEAR/MONTH/DAY functions (portable across MySQL and H2).
    // Returns [year, month, day, count] rows — only days with >= 1 click; zero-fill in service.
    @Query("""
        SELECT YEAR(c.clickedAt), MONTH(c.clickedAt), DAY(c.clickedAt), COUNT(c)
        FROM Click c
        WHERE c.linkId = :linkId AND c.clickedAt >= :since
        GROUP BY YEAR(c.clickedAt), MONTH(c.clickedAt), DAY(c.clickedAt)
        ORDER BY YEAR(c.clickedAt) ASC, MONTH(c.clickedAt) ASC, DAY(c.clickedAt) ASC
        """)
    List<Object[]> findDailyClickCounts(@Param("linkId") Long linkId,
                                        @Param("since") LocalDateTime since);
}
