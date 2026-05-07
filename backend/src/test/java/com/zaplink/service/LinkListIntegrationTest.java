package com.zaplink.service;

import com.zaplink.dto.LinkResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LinkListIntegrationTest {

    @Autowired private LinkService linkService;
    @Autowired private LinkRepository linkRepository;
    @Autowired private ClickRepository clickRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void listLinksForUser_returnsCorrectTotalElementsAndClickCounts() {
        User user = new User();
        user.setUsername("listuser");
        user.setEmail("listuser@example.com");
        user.setPasswordHash("irrelevant-for-this-test");
        user.setRole(Role.USER);
        user.setIsActive(true);
        user = userRepository.save(user);
        Long userId = user.getId();

        Link link1 = saveLink(userId, "https://alpha.example.com", "lll");
        Link link2 = saveLink(userId, "https://beta.example.com",  "mmm");
        Link link3 = saveLink(userId, "https://gamma.example.com", "nnn");

        insertClicks(link1.getId(), 5);
        insertClicks(link2.getId(), 2);
        // link3 gets no clicks

        Page<LinkResponse> result = linkService.listLinksForUser(userId, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(3);

        LinkResponse r1 = findById(result, link1.getId());
        assertThat(r1.clickCount()).isEqualTo(5L);
        assertThat(r1.status()).isEqualTo("active");

        LinkResponse r2 = findById(result, link2.getId());
        assertThat(r2.clickCount()).isEqualTo(2L);

        LinkResponse r3 = findById(result, link3.getId());
        assertThat(r3.clickCount()).isEqualTo(0L);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    private LinkResponse findById(Page<LinkResponse> page, Long id) {
        return page.getContent().stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Link " + id + " not found in result"));
    }
}
