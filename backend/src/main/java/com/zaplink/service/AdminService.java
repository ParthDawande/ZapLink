package com.zaplink.service;

import com.zaplink.dto.AdminLinkOwner;
import com.zaplink.dto.AdminLinkResponse;
import com.zaplink.dto.AdminUserResponse;
import com.zaplink.dto.SystemReportsResponse;
import com.zaplink.exception.InvalidOperationException;
import com.zaplink.exception.InvalidQueryParamException;
import com.zaplink.exception.LinkNotFoundException;
import com.zaplink.exception.UserNotFoundException;
import com.zaplink.model.DisabledReason;
import com.zaplink.model.Link;
import com.zaplink.model.User;
import com.zaplink.repository.ClickRepository;
import com.zaplink.repository.LinkRepository;
import com.zaplink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AdminService {

    private static final Set<String> VALID_LINK_STATUSES =
            Set.of("all", "active", "expired", "disabled", "deleted");

    private final UserRepository userRepository;
    private final LinkRepository linkRepository;
    private final ClickRepository clickRepository;

    @Value("${zaplink.base-url}")
    private String baseUrl;

    public AdminService(UserRepository userRepository,
                        LinkRepository linkRepository,
                        ClickRepository clickRepository) {
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.clickRepository = clickRepository;
    }

    // ── Endpoint 11 — GET /api/admin/users ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(String search, String status) {
        if (status != null && !status.equals("active") && !status.equals("disabled")) {
            throw new InvalidQueryParamException("Invalid status: must be 'active' or 'disabled'");
        }

        String searchPattern = search != null ? "%" + search.toLowerCase() + "%" : null;
        Boolean isActive = status == null ? null : "active".equals(status);

        return userRepository.findUsersWithLinkCount(searchPattern, isActive);
    }

    // ── Endpoint 12 — PATCH /api/admin/users/{id}/ban ────────────────────────

    @Transactional
    public AdminUserResponse setUserBanned(Long callerId, Long targetUserId, boolean banned) {
        if (callerId.equals(targetUserId)) {
            throw new InvalidOperationException("You cannot ban or unban yourself");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (banned) {
            if (!target.getIsActive()) {
                return toUserResponse(target);
            }
            target.setIsActive(false);
            userRepository.save(target);
            linkRepository.cascadeBanForUser(targetUserId);
        } else {
            if (target.getIsActive()) {
                return toUserResponse(target);
            }
            target.setIsActive(true);
            userRepository.save(target);
            linkRepository.cascadeUnbanForUser(targetUserId);
        }

        return toUserResponse(target);
    }

    // ── Endpoint 13 — DELETE /api/admin/users/{id} ───────────────────────────

    @Transactional
    public void deleteUser(Long callerId, Long targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new InvalidOperationException("You cannot delete yourself");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (target.getIsActive()) {
            throw new InvalidOperationException("User must be banned before deletion");
        }

        // Soft-delete all their links first so click history is preserved after FK cascade.
        linkRepository.softDeleteAllLinksForUser(targetUserId);
        // Hard-delete the user — ON DELETE SET NULL cascades user_id=NULL onto the links.
        userRepository.delete(target);
    }

    // ── Endpoint 14 — GET /api/admin/links ───────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminLinkResponse> listLinks(String search, String status, Pageable pageable) {
        if (!VALID_LINK_STATUSES.contains(status)) {
            throw new InvalidQueryParamException(
                    "Invalid status: must be one of: all, active, expired, disabled, deleted");
        }

        String searchPattern = search != null ? "%" + search.toLowerCase() + "%" : null;

        return linkRepository.findAdminLinks(searchPattern, status, pageable)
                .map(this::mapToAdminLinkResponse);
    }

    // ── Endpoint 16 — GET /api/admin/reports ─────────────────────────────────

    @Transactional(readOnly = true)
    public SystemReportsResponse getSystemReports() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        // Spring Data JPA wraps native aggregate results in List<Object[]>; get(0) is the row.
        Object[] userRow = userRepository.getUserStats(since).get(0);
        var userStats = new SystemReportsResponse.UserStats(
                toLong(userRow[0]), toLong(userRow[1]), toLong(userRow[2]), toLong(userRow[3]));

        Object[] linkRow = linkRepository.getLinkStats(since).get(0);
        var linkStats = new SystemReportsResponse.LinkStats(
                toLong(linkRow[0]), toLong(linkRow[1]), toLong(linkRow[2]), toLong(linkRow[3]),
                toLong(linkRow[4]), toLong(linkRow[5]), toLong(linkRow[6]));

        Object[] clickRow = clickRepository.getClickStats(since).get(0);
        var clickStats = new SystemReportsResponse.ClickStats(
                toLong(clickRow[0]), toLong(clickRow[1]));

        return new SystemReportsResponse(userStats, linkStats, clickStats);
    }

    // ── Endpoint 15 — PATCH /api/admin/links/{id}/disable ────────────────────

    @Transactional
    public AdminLinkResponse setLinkDisabled(Long linkId, boolean disabled) {
        Link link = linkRepository.findByIdIncludingDeleted(linkId)
                .orElseThrow(() -> new LinkNotFoundException("Link not found"));

        if (link.getDeletedAt() != null) {
            throw new LinkNotFoundException("Link not found");
        }

        if (DisabledReason.USER_BANNED.equals(link.getDisabledReason())) {
            throw new InvalidOperationException(
                    "Link is disabled due to a user account ban and cannot be modified");
        }

        if (disabled) {
            if (Boolean.FALSE.equals(link.getIsActive())) {
                throw new InvalidOperationException("Link is already disabled");
            }
            link.setIsActive(false);
            link.setDisabledReason(DisabledReason.ADMIN_DISABLED);
        } else {
            if (Boolean.TRUE.equals(link.getIsActive())) {
                throw new InvalidOperationException("Link is already active");
            }
            link.setIsActive(true);
            link.setDisabledReason(null);
        }

        linkRepository.save(link);
        return toResponse(link);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AdminUserResponse toUserResponse(User user) {
        long linkCount = linkRepository.countByUserId(user.getId());
        return new AdminUserResponse(
                user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.getIsActive(), linkCount, user.getCreatedAt());
    }

    private AdminLinkResponse toResponse(Link link) {
        long clickCount = clickRepository.countByLinkId(link.getId());
        AdminLinkOwner owner = null;
        if (link.getUserId() != null) {
            owner = userRepository.findById(link.getUserId())
                    .map(u -> new AdminLinkOwner(u.getId(), u.getUsername(), u.getEmail()))
                    .orElse(null);
        }
        String shortUrl = link.getShortCode() != null ? baseUrl + "/" + link.getShortCode() : null;
        String status = computeLinkStatus(link.getDeletedAt(), link.getIsActive(), link.getExpiresAt());
        String disabledReason = link.getDisabledReason() != null ? link.getDisabledReason().name() : null;
        return new AdminLinkResponse(
                link.getId(), link.getShortCode(), shortUrl, link.getLongUrl(),
                link.getExpiresAt(), link.getIsActive(), disabledReason,
                link.getDeletedAt(), status, clickCount, owner, link.getCreatedAt());
    }

    private AdminLinkResponse mapToAdminLinkResponse(Object[] row) {
        Long id               = toLong(row[0]);
        String shortCode      = (String) row[1];
        String longUrl        = (String) row[2];
        // row[3] = user_id — not used in DTO directly, owner object covers it
        LocalDateTime expiresAt      = toLocalDateTime(row[4]);
        Boolean isActive             = toBoolean(row[5]);
        String disabledReason        = (String) row[6];
        LocalDateTime deletedAt      = toLocalDateTime(row[7]);
        LocalDateTime createdAt      = toLocalDateTime(row[8]);
        Long ownerId                 = row[9]  != null ? toLong(row[9])  : null;
        String ownerUsername         = (String) row[10];
        String ownerEmail            = (String) row[11];
        Long clickCount              = toLong(row[12]);

        String shortUrl = shortCode != null ? baseUrl + "/" + shortCode : null;
        String status   = computeLinkStatus(deletedAt, isActive, expiresAt);
        AdminLinkOwner owner = ownerId != null
                ? new AdminLinkOwner(ownerId, ownerUsername, ownerEmail)
                : null;

        return new AdminLinkResponse(id, shortCode, shortUrl, longUrl, expiresAt, isActive,
                disabledReason, deletedAt, status, clickCount, owner, createdAt);
    }

    private String computeLinkStatus(LocalDateTime deletedAt, Boolean isActive, LocalDateTime expiresAt) {
        if (deletedAt != null) return "deleted";
        if (Boolean.FALSE.equals(isActive)) return "disabled";
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) return "expired";
        return "active";
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    private Boolean toBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(value.toString());
    }
}
