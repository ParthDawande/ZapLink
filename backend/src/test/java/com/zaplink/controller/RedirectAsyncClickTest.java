package com.zaplink.controller;

import com.zaplink.model.Link;
import com.zaplink.model.Role;
import com.zaplink.model.User;
import com.zaplink.repository.ClickRepository;
import com.zaplink.repository.LinkRepository;
import com.zaplink.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// No @Transactional — the async listener runs in a separate thread and needs
// the committed link row to be visible. We clean up manually in @AfterEach.
class RedirectAsyncClickTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LinkRepository linkRepository;
    @Autowired private ClickRepository clickRepository;
    @Autowired private UserRepository userRepository;

    private Long savedLinkId;
    private Long savedUserId;

    @AfterEach
    void cleanUp() {
        if (savedLinkId != null) {
            clickRepository.deleteAll(
                    clickRepository.findAll().stream()
                            .filter(c -> savedLinkId.equals(c.getLinkId()))
                            .toList());
            linkRepository.deleteById(savedLinkId);
        }
        if (savedUserId != null) {
            userRepository.deleteById(savedUserId);
        }
    }

    @Test
    void redirect_asyncClickIsPersistedEventually() throws Exception {
        User user = new User();
        user.setUsername("async-click-user");
        user.setEmail("async-click@example.com");
        user.setPasswordHash("irrelevant");
        user.setRole(Role.USER);
        user.setIsActive(true);
        user = userRepository.save(user);
        savedUserId = user.getId();

        Link link = new Link();
        link.setUserId(user.getId());
        link.setLongUrl("https://async-click-test.example.com");
        link.setShortCode("asynct");
        link.setIsActive(true);
        link = linkRepository.save(link);
        savedLinkId = link.getId();

        // Redirect returns 302 immediately — the click write is async
        mockMvc.perform(get("/asynct"))
                .andExpect(status().isFound());

        // Wait up to 2 seconds for the background thread to insert the click row
        final Long linkId = savedLinkId;
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> clickRepository.countByLinkId(linkId) == 1);
    }
}
