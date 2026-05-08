package com.zaplink.service;

import com.zaplink.dto.AnalyticsResponse;
import com.zaplink.exception.LinkNotFoundException;
import com.zaplink.model.Click;
import com.zaplink.model.Link;
import com.zaplink.model.Role;
import com.zaplink.model.User;
import com.zaplink.repository.ClickRepository;
import com.zaplink.repository.LinkRepository;
import com.zaplink.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LinkAnalyticsIntegrationTest {

    @Autowired private LinkService linkService;
    @Autowired private LinkRepository linkRepository;
    @Autowired private ClickRepository clickRepository;
    @Autowired private UserRepository userRepository;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User savedUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("irrelevant");
        u.setRole(Role.USER);
        u.setIsActive(true);
        return userRepository.save(u);
    }

    private Link savedLink(Long userId, String shortCode) {
        Link l = new Link();
        l.setUserId(userId);
        l.setLongUrl("https://example.com/" + shortCode);
        l.setShortCode(shortCode);
        l.setIsActive(true);
        return linkRepository.save(l);
    }

    private void insertClick(Long linkId, LocalDateTime at) {
        Click c = new Click();
        c.setLinkId(linkId);
        c.setClickedAt(at);
        clickRepository.save(c);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void analytics_noClicks_allZerosAnd30Entries() {
        Long userId = savedUser("anl-zero", "anl-zero@example.com").getId();
        Long linkId = savedLink(userId, "z0z0z").getId();

        AnalyticsResponse r = linkService.getAnalyticsForUser(userId, linkId);

        assertThat(r.totalClicks()).isZero();
        assertThat(r.last30DaysClicks()).isZero();
        assertThat(r.dailyClicks()).hasSize(30);
        assertThat(r.dailyClicks()).allMatch(d -> d.count() == 0);
    }

    @Test
    void analytics_fiveClicksToday_lastEntryHasFive() {
        Long userId = savedUser("anl-today", "anl-today@example.com").getId();
        Long linkId = savedLink(userId, "tdtd1").getId();

        LocalDateTime today = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        for (int i = 0; i < 5; i++) insertClick(linkId, today.plusMinutes(i));

        AnalyticsResponse r = linkService.getAnalyticsForUser(userId, linkId);

        assertThat(r.totalClicks()).isEqualTo(5);
        assertThat(r.last30DaysClicks()).isEqualTo(5);
        assertThat(r.dailyClicks()).hasSize(30);

        // Last entry is today, first entry is 29 days ago
        assertThat(r.dailyClicks().get(29).date()).isEqualTo(LocalDate.now());
        assertThat(r.dailyClicks().get(29).count()).isEqualTo(5);
        // All other entries should be 0
        assertThat(r.dailyClicks().subList(0, 29)).allMatch(d -> d.count() == 0);
    }

    @Test
    void analytics_clicksAcross3Days_zeroFilledGaps() {
        Long userId = savedUser("anl-3days", "anl-3days@example.com").getId();
        Long linkId = savedLink(userId, "d3d3d").getId();

        LocalDateTime today = LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime minus5 = today.minusDays(5);
        LocalDateTime minus10 = today.minusDays(10);

        // 3 clicks 10 days ago, 2 clicks 5 days ago, 1 click today
        insertClick(linkId, minus10);
        insertClick(linkId, minus10.plusHours(1));
        insertClick(linkId, minus10.plusHours(2));
        insertClick(linkId, minus5);
        insertClick(linkId, minus5.plusHours(1));
        insertClick(linkId, today);

        AnalyticsResponse r = linkService.getAnalyticsForUser(userId, linkId);

        assertThat(r.totalClicks()).isEqualTo(6);
        assertThat(r.last30DaysClicks()).isEqualTo(6);
        assertThat(r.dailyClicks()).hasSize(30);

        // Verify chronological order: first entry is 29 days ago
        assertThat(r.dailyClicks().get(0).date()).isEqualTo(LocalDate.now().minusDays(29));

        // Find the entries for the three click days
        LocalDate todayDate = LocalDate.now();
        assertThat(r.dailyClicks().stream()
                .filter(d -> d.date().equals(todayDate)).findFirst().orElseThrow().count())
                .isEqualTo(1);
        assertThat(r.dailyClicks().stream()
                .filter(d -> d.date().equals(todayDate.minusDays(5))).findFirst().orElseThrow().count())
                .isEqualTo(2);
        assertThat(r.dailyClicks().stream()
                .filter(d -> d.date().equals(todayDate.minusDays(10))).findFirst().orElseThrow().count())
                .isEqualTo(3);

        // All other 27 entries must be 0
        long zeroEntries = r.dailyClicks().stream()
                .filter(d -> !d.date().equals(todayDate)
                          && !d.date().equals(todayDate.minusDays(5))
                          && !d.date().equals(todayDate.minusDays(10)))
                .filter(d -> d.count() == 0)
                .count();
        assertThat(zeroEntries).isEqualTo(27);
    }

    @Test
    void analytics_clickOutsideWindow_countsInTotalNotIn30Days() {
        Long userId = savedUser("anl-old", "anl-old@example.com").getId();
        Long linkId = savedLink(userId, "old01").getId();

        // One click 40 days ago — outside the 30-day window
        insertClick(linkId, LocalDateTime.now().minusDays(40));

        AnalyticsResponse r = linkService.getAnalyticsForUser(userId, linkId);

        assertThat(r.totalClicks()).isEqualTo(1);
        assertThat(r.last30DaysClicks()).isZero();
        assertThat(r.dailyClicks()).hasSize(30);
        assertThat(r.dailyClicks()).allMatch(d -> d.count() == 0);
    }

    @Test
    void analytics_otherUsersLink_throwsLinkNotFoundException() {
        Long user1Id = savedUser("anl-u1", "anl-u1@example.com").getId();
        Long user2Id = savedUser("anl-u2", "anl-u2@example.com").getId();
        Long linkId  = savedLink(user1Id, "u1lnk").getId();

        assertThatThrownBy(() -> linkService.getAnalyticsForUser(user2Id, linkId))
                .isInstanceOf(LinkNotFoundException.class)
                .hasMessage("Link not found");
    }
}
