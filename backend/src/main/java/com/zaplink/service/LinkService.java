package com.zaplink.service;

import com.zaplink.dto.AnalyticsResponse;
import com.zaplink.dto.CreateLinkRequest;
import com.zaplink.dto.CreateLinkResult;
import com.zaplink.dto.DailyClickCount;
import com.zaplink.dto.LinkResponse;
import com.zaplink.exception.InvalidQueryParamException;
import com.zaplink.exception.InvalidUrlException;
import com.zaplink.exception.LinkNotFoundException;
import com.zaplink.exception.MaliciousUrlException;
import com.zaplink.model.Link;
import com.zaplink.repository.ClickRepository;
import com.zaplink.repository.LinkRepository;
import com.zaplink.util.Base62Encoder;
import com.zaplink.util.ReservedShortCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LinkService {

    @Value("${zaplink.base-url}")
    private String baseUrl;

    private final LinkRepository linkRepository;
    private final ClickRepository clickRepository;
    private final SafeBrowsingService safeBrowsingService;

    public LinkService(LinkRepository linkRepository, ClickRepository clickRepository,
                       SafeBrowsingService safeBrowsingService) {
        this.linkRepository = linkRepository;
        this.clickRepository = clickRepository;
        this.safeBrowsingService = safeBrowsingService;
    }

    @Transactional
    public CreateLinkResult createLink(Long userId, CreateLinkRequest request) {
        String longUrl = request.getLongUrl();

        // a. URL format validation
        URI uri;
        try {
            uri = URI.create(longUrl);
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("Invalid URL format");
        }
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new InvalidUrlException("URL must start with http:// or https://");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must have a valid host");
        }

        // b. Self-referencing check
        try {
            String baseHost = URI.create(baseUrl).getHost();
            if (baseHost != null && baseHost.equalsIgnoreCase(host)) {
                throw new InvalidUrlException("Cannot shorten a ZapLink URL");
            }
        } catch (IllegalArgumentException ignored) {
            // misconfigured baseUrl — fail open
        }

        // c. expiresAt must be in the future if provided
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidUrlException("Expiration date must be in the future");
        }

        // d. Per-user deduplication (@SQLRestriction handles deleted_at IS NULL)
        Optional<Link> existing = linkRepository.findByUserIdAndLongUrlAndIsActiveTrue(userId, longUrl);
        if (existing.isPresent()) {
            Link existingLink = existing.get();
            boolean isExpired = existingLink.getExpiresAt() != null
                    && existingLink.getExpiresAt().isBefore(LocalDateTime.now());
            if (!isExpired) {
                return new CreateLinkResult(toResponse(existingLink, 0L), false);
            }
            // expired — fall through and create a new link
        }

        // e. Safe Browsing check — after dedup so cached hits don't burn API quota
        if (!safeBrowsingService.isSafe(longUrl)) {
            throw new MaliciousUrlException("This URL has been flagged as unsafe");
        }

        // f+g. Build initial entity (shortCode=null) and save to get the auto-generated id
        Link link = buildLink(userId, longUrl, request.getExpiresAt());
        link = linkRepository.save(link);

        // h+i. Encode id → Base62; retry if the code collides with a reserved keyword
        String code = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            code = Base62Encoder.encode(link.getId());
            if (!ReservedShortCodes.isReserved(code)) {
                break;
            }
            // Permanently consume this id slot so the auto-increment advances
            link.setShortCode("__reserved__" + link.getId());
            link.setIsActive(false);
            linkRepository.save(link);
            link = buildLink(userId, longUrl, request.getExpiresAt());
            link = linkRepository.save(link);
            code = null;
        }

        if (code == null) {
            throw new IllegalStateException("Could not generate a non-reserved short code after 5 attempts");
        }

        // j. Stamp the short code onto the winning row
        link.setShortCode(code);
        linkRepository.save(link);

        return new CreateLinkResult(toResponse(link, 0L), true);
    }

    @Transactional(readOnly = true)
    public Page<LinkResponse> listLinksForUser(Long userId, Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new InvalidQueryParamException("Page size must be between 1 and 100");
        }

        // Always sort by createdAt DESC regardless of what the caller passed
        Pageable fixedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Object[]> rows = linkRepository.findUserLinksWithClickCount(userId, fixedPageable);

        return rows.map(row -> {
            Link link = (Link) row[0];
            long clickCount = ((Number) row[1]).longValue();
            return toResponse(link, clickCount);
        });
    }

    @Transactional(readOnly = true)
    public LinkResponse getLinkForUser(Long userId, Long linkId) {
        List<Object[]> rows = linkRepository.findByIdWithClickCount(linkId);
        if (rows.isEmpty()) throw new LinkNotFoundException("Link not found");
        Object[] row = rows.get(0);

        Link link = (Link) row[0];
        // 404 (not 403) for ownership mismatch — don't leak that the link exists
        if (!userId.equals(link.getUserId())) {
            throw new LinkNotFoundException("Link not found");
        }

        long clickCount = ((Number) row[1]).longValue();
        return toResponse(link, clickCount);
    }

    @Transactional
    public void deleteLinkForUser(Long userId, Long linkId) {
        Link link = linkRepository.findById(linkId)
                .orElseThrow(() -> new LinkNotFoundException("Link not found"));

        // Explicit check: @SQLRestriction doesn't apply to EntityManager first-level cache hits
        if (link.getDeletedAt() != null) {
            throw new LinkNotFoundException("Link not found");
        }

        // 404 for ownership mismatch — don't leak that the link exists
        if (!userId.equals(link.getUserId())) {
            throw new LinkNotFoundException("Link not found");
        }

        link.setDeletedAt(LocalDateTime.now());
        linkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalyticsForUser(Long userId, Long linkId) {
        // Same 404-for-everything pattern as get-one — ownership mismatch leaks nothing.
        Link link = linkRepository.findById(linkId)
                .orElseThrow(() -> new LinkNotFoundException("Link not found"));
        if (!userId.equals(link.getUserId())) {
            throw new LinkNotFoundException("Link not found");
        }

        LocalDateTime now = LocalDateTime.now();
        // 30-day window: today (inclusive) + the previous 29 days = 30 entries total.
        // Truncate to midnight so the window starts at 00:00:00, not the current second.
        // Off-by-one note: minusDays(29) + today = 30 days, not 31.
        LocalDateTime since = now.minusDays(29).truncatedTo(ChronoUnit.DAYS);

        long totalClicks      = clickRepository.countByLinkId(linkId);
        long last30DaysClicks = clickRepository.countByLinkIdSince(linkId, since);
        List<Object[]> raw    = clickRepository.findDailyClickCounts(linkId, since);

        // Build lookup map — DB only returns days with >= 1 click.
        // Row format: [year, month, day, count] — using standard JPQL YEAR/MONTH/DAY functions.
        Map<LocalDate, Long> clicksByDay = new HashMap<>();
        for (Object[] row : raw) {
            LocalDate date = LocalDate.of(
                    ((Number) row[0]).intValue(),
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).intValue());
            clicksByDay.put(date, ((Number) row[3]).longValue());
        }

        // Zero-fill: produce exactly 30 entries ordered oldest → newest.
        LocalDate today = now.toLocalDate();
        List<DailyClickCount> dailyClicks = new ArrayList<>(30);
        for (int i = 0; i < 30; i++) {
            LocalDate date = today.minusDays(29 - i);
            dailyClicks.add(new DailyClickCount(date, clicksByDay.getOrDefault(date, 0L)));
        }

        return new AnalyticsResponse(linkId, link.getShortCode(), totalClicks, last30DaysClicks, dailyClicks);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Link buildLink(Long userId, String longUrl, LocalDateTime expiresAt) {
        Link link = new Link();
        link.setUserId(userId);
        link.setLongUrl(longUrl);
        link.setShortCode(null);
        link.setIsActive(true);
        link.setExpiresAt(expiresAt);
        return link;
    }

    private LinkResponse toResponse(Link link, long clickCount) {
        String disabledReason = link.getDisabledReason() != null
                ? link.getDisabledReason().name()
                : null;
        return new LinkResponse(
                link.getId(),
                link.getShortCode(),
                baseUrl + "/" + link.getShortCode(),
                link.getLongUrl(),
                link.getExpiresAt(),
                link.getIsActive(),
                disabledReason,
                LinkResponse.computeStatus(link.getIsActive(), link.getExpiresAt()),
                clickCount,
                link.getCreatedAt()
        );
    }
}
