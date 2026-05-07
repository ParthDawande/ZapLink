package com.zaplink.service;

import com.zaplink.dto.CreateLinkRequest;
import com.zaplink.dto.CreateLinkResult;
import com.zaplink.dto.LinkResponse;
import com.zaplink.exception.InvalidUrlException;
import com.zaplink.exception.LinkNotFoundException;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Stubs save() to auto-assign id=1 and createdAt on first call. */
    private void stubSaveWithId(long id) {
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> {
            Link link = invocation.getArgument(0);
            if (link.getId() == null) {
                link.setId(id);
                link.setCreatedAt(LocalDateTime.now());
            }
            return link;
        });
    }

    private static Link activeLink(long id, String shortCode, String longUrl, LocalDateTime expiresAt) {
        Link link = new Link();
        link.setId(id);
        link.setShortCode(shortCode);
        link.setLongUrl(longUrl);
        link.setIsActive(true);
        link.setExpiresAt(expiresAt);
        link.setCreatedAt(LocalDateTime.now().minusDays(1));
        return link;
    }

    // ── Existing tests (updated for CreateLinkResult return type) ────────────

    @Test
    void createLink_withValidUrl_returnsNonNullShortCode() {
        when(linkRepository.findByUserIdAndLongUrlAndIsActiveTrue(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        stubSaveWithId(1L);

        CreateLinkRequest request = new CreateLinkRequest(
                "https://example.com/some/very/long/article-url", null);

        CreateLinkResult result = linkService.createLink(1L, request);
        LinkResponse response = result.response();

        assertThat(response.shortCode()).isNotNull();
        assertThat(response.longUrl()).isEqualTo("https://example.com/some/very/long/article-url");
        assertThat(response.shortUrl()).startsWith("http://localhost:8080/");
        assertThat(response.isActive()).isTrue();
        assertThat(result.isNew()).isTrue();
    }

    @Test
    void createLink_setsUserIdOnSavedLink_matchesProvidedUserId() {
        when(linkRepository.findByUserIdAndLongUrlAndIsActiveTrue(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        stubSaveWithId(7L);

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

    // ── Dedup tests ──────────────────────────────────────────────────────────

    @Test
    void createLink_sameUserSameUrl_returnsExistingLink_isNotNew() {
        Link existing = activeLink(5L, "abc", "https://example.com", null);
        when(linkRepository.findByUserIdAndLongUrlAndIsActiveTrue(1L, "https://example.com"))
                .thenReturn(Optional.of(existing));

        CreateLinkRequest request = new CreateLinkRequest("https://example.com", null);
        CreateLinkResult result = linkService.createLink(1L, request);

        assertThat(result.isNew()).isFalse();
        assertThat(result.response().id()).isEqualTo(5L);
        assertThat(result.response().shortCode()).isEqualTo("abc");
        // No save should occur — we returned the existing link
        verify(linkRepository, never()).save(any());
    }

    @Test
    void createLink_sameUserExpiredUrl_createsNewLink_isNew() {
        Link expired = activeLink(3L, "xyz", "https://example.com",
                LocalDateTime.now().minusHours(1));
        when(linkRepository.findByUserIdAndLongUrlAndIsActiveTrue(1L, "https://example.com"))
                .thenReturn(Optional.of(expired));
        stubSaveWithId(99L);

        CreateLinkRequest request = new CreateLinkRequest("https://example.com", null);
        CreateLinkResult result = linkService.createLink(1L, request);

        assertThat(result.isNew()).isTrue();
        assertThat(result.response().id()).isEqualTo(99L);
        assertThat(result.response().id()).isNotEqualTo(3L);
    }

    // ── getLinkForUser tests ─────────────────────────────────────────────────

    @Test
    void getLink_ownLink_returnsLinkWithClickCount() {
        Link link = activeLink(10L, "xyz", "https://example.com", null);
        link.setUserId(1L);
        when(linkRepository.findByIdWithClickCount(10L))
                .thenReturn(List.<Object[]>of(new Object[]{link, 7L}));

        LinkResponse response = linkService.getLinkForUser(1L, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.shortCode()).isEqualTo("xyz");
        assertThat(response.clickCount()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("active");
        assertThat(response.disabledReason()).isNull();
    }

    @Test
    void getLink_otherUsersLink_throwsLinkNotFoundException() {
        Link link = activeLink(10L, "xyz", "https://example.com", null);
        link.setUserId(99L); // owned by someone else
        when(linkRepository.findByIdWithClickCount(10L))
                .thenReturn(List.<Object[]>of(new Object[]{link, 0L}));

        assertThatThrownBy(() -> linkService.getLinkForUser(1L, 10L))
                .isInstanceOf(LinkNotFoundException.class)
                .hasMessage("Link not found");
    }

    @Test
    void getLink_nonExistentLink_throwsLinkNotFoundException() {
        when(linkRepository.findByIdWithClickCount(99999L))
                .thenReturn(List.<Object[]>of());

        assertThatThrownBy(() -> linkService.getLinkForUser(1L, 99999L))
                .isInstanceOf(LinkNotFoundException.class)
                .hasMessage("Link not found");
    }

    // ── deleteLinkForUser tests ──────────────────────────────────────────────

    @Test
    void deleteLink_ownLink_setsDeletedAt() {
        Link link = activeLink(10L, "xyz", "https://example.com", null);
        link.setUserId(1L);
        when(linkRepository.findById(10L)).thenReturn(Optional.of(link));
        when(linkRepository.save(any(Link.class))).thenReturn(link);

        linkService.deleteLinkForUser(1L, 10L);

        ArgumentCaptor<Link> captor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void deleteLink_otherUsersLink_throwsLinkNotFoundException() {
        Link link = activeLink(10L, "xyz", "https://example.com", null);
        link.setUserId(99L);
        when(linkRepository.findById(10L)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> linkService.deleteLinkForUser(1L, 10L))
                .isInstanceOf(LinkNotFoundException.class)
                .hasMessage("Link not found");
        verify(linkRepository, never()).save(any());
    }

    @Test
    void deleteLink_nonExistentLink_throwsLinkNotFoundException() {
        when(linkRepository.findById(99999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.deleteLinkForUser(1L, 99999L))
                .isInstanceOf(LinkNotFoundException.class)
                .hasMessage("Link not found");
    }

    @Test
    void deleteLink_alreadySoftDeleted_throwsLinkNotFoundException() {
        // @SQLRestriction("deleted_at IS NULL") makes soft-deleted rows invisible to findById.
        // Simulate that by having the mock return empty — same result the real DB gives.
        when(linkRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.deleteLinkForUser(1L, 10L))
                .isInstanceOf(LinkNotFoundException.class)
                .hasMessage("Link not found");
    }

    @Test
    void createLink_differentUsers_sameUrl_getDifferentLinks() {
        // User 1 already has an active link for this URL
        Link user1Link = activeLink(10L, "aaa", "https://example.com", null);
        when(linkRepository.findByUserIdAndLongUrlAndIsActiveTrue(1L, "https://example.com"))
                .thenReturn(Optional.of(user1Link));

        // User 2 has no existing link
        when(linkRepository.findByUserIdAndLongUrlAndIsActiveTrue(2L, "https://example.com"))
                .thenReturn(Optional.empty());
        stubSaveWithId(20L);

        CreateLinkRequest request = new CreateLinkRequest("https://example.com", null);

        CreateLinkResult user1Result = linkService.createLink(1L, request);
        CreateLinkResult user2Result = linkService.createLink(2L, request);

        assertThat(user1Result.isNew()).isFalse();
        assertThat(user2Result.isNew()).isTrue();
        assertThat(user1Result.response().id()).isNotEqualTo(user2Result.response().id());
        assertThat(user1Result.response().shortCode()).isNotEqualTo(user2Result.response().shortCode());
    }
}
