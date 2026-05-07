package com.zaplink.service;

import com.zaplink.dto.CreateLinkRequest;
import com.zaplink.dto.LinkResponse;
import com.zaplink.exception.InvalidUrlException;
import com.zaplink.model.Link;
import com.zaplink.repository.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRepository linkRepository;

    @InjectMocks
    private LinkService linkService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(linkService, "baseUrl", "http://localhost:8080");
    }

    @Test
    void createLink_withValidUrl_returnsNonNullShortCode() {
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> {
            Link link = invocation.getArgument(0);
            if (link.getId() == null) {
                link.setId(1L);
                link.setCreatedAt(LocalDateTime.now());
            }
            return link;
        });

        CreateLinkRequest request = new CreateLinkRequest(
                "https://example.com/some/very/long/article-url", null);

        LinkResponse response = linkService.createLink(1L, request);

        assertThat(response.shortCode()).isNotNull();
        assertThat(response.longUrl()).isEqualTo("https://example.com/some/very/long/article-url");
        assertThat(response.shortUrl()).startsWith("http://localhost:8080/");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void createLink_setsUserIdOnSavedLink_matchesProvidedUserId() {
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> {
            Link link = invocation.getArgument(0);
            if (link.getId() == null) {
                link.setId(7L);
                link.setCreatedAt(LocalDateTime.now());
            }
            return link;
        });

        CreateLinkRequest request = new CreateLinkRequest("https://example.com", null);
        linkService.createLink(42L, request);

        ArgumentCaptor<Link> captor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getUserId()).isEqualTo(42L);
    }

    @Test
    void createLink_withNonHttpScheme_throwsInvalidUrlException() {
        CreateLinkRequest request = new CreateLinkRequest("ftp://example.com/file.zip", null);

        assertThatThrownBy(() -> linkService.createLink(1L, request))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("http");
    }

    @Test
    void createLink_withSelfReferencingUrl_throwsInvalidUrlException() {
        CreateLinkRequest request = new CreateLinkRequest("http://localhost:8080/abc123", null);

        assertThatThrownBy(() -> linkService.createLink(1L, request))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("ZapLink");
    }

    @Test
    void createLink_withPastExpiresAt_throwsInvalidUrlException() {
        CreateLinkRequest request = new CreateLinkRequest(
                "https://example.com", LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> linkService.createLink(1L, request))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("future");
    }
}
