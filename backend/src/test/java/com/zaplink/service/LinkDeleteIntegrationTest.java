package com.zaplink.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LinkDeleteIntegrationTest {

    @Autowired private LinkService linkService;
    @Autowired private LinkRepository linkRepository;
    @Autowired private ClickRepository clickRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void deleteLink_clickRowsPersistAfterSoftDelete() {
        Long userId = savedUser("deluser", "deluser@example.com").getId();
        Link link = saveLink(userId, "https://persist.example.com", "ppp");

        // Record 3 clicks before deleting
        insertClicks(link.getId(), 3);
        assertThat(clickRepository.count()).isGreaterThanOrEqualTo(3);
        long clicksBefore = clickRepository.count();

        // Soft-delete the link
        linkService.deleteLinkForUser(userId, link.getId());

        // Click rows must still exist — soft-delete doesn't cascade to clicks
        assertThat(clickRepository.count()).isEqualTo(clicksBefore);
    }

    @Test
    void deleteLink_twiceSameLink_secondCallThrowsLinkNotFoundException() {
        Long userId = savedUser("twice-user", "twice@example.com").getId();
        Link link = saveLink(userId, "https://double-delete.example.com", "ddd");

        // First delete succeeds
        linkService.deleteLinkForUser(userId, link.getId());

        // Second delete: @SQLRestriction filters the soft-deleted row → findById returns empty → 404
        assertThatThrownBy(() -> linkService.deleteLinkForUser(userId, link.getId()))
                .isInstanceOf(LinkNotFoundException.class)
                .hasMessage("Link not found");
    }

    @Test
    void deleteLink_subsequentGetByIdReturns404() {
        Long userId = savedUser("getafter-user", "getafter@example.com").getId();
        Link link = saveLink(userId, "https://getafter.example.com", "ggg");
        Long linkId = link.getId();

        linkService.deleteLinkForUser(userId, linkId);

        assertThatThrownBy(() -> linkService.getLinkForUser(userId, linkId))
                .isInstanceOf(LinkNotFoundException.class)
                .hasMessage("Link not found");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User savedUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("irrelevant-hash");
        user.setRole(Role.USER);
        user.setIsActive(true);
        return userRepository.save(user);
    }

    private Link saveLink(Long userId, String longUrl, String shortCode) {
        Link link = new Link();
        link.setUserId(userId);
        link.setLongUrl(longUrl);
        link.setShortCode(shortCode);
        link.setIsActive(true);
        return linkRepository.save(link);
    }

    private void insertClicks(Long linkId, int count) {
        for (int i = 0; i < count; i++) {
            Click click = new Click();
            click.setLinkId(linkId);
            clickRepository.save(click);
        }
    }
}
