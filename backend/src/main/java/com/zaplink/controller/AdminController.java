package com.zaplink.controller;

import com.zaplink.dto.AdminBanUserRequest;
import com.zaplink.dto.AdminDisableLinkRequest;
import com.zaplink.dto.AdminLinkResponse;
import com.zaplink.dto.AdminUserResponse;
import com.zaplink.dto.SystemReportsResponse;
import com.zaplink.exception.InvalidQueryParamException;
import com.zaplink.security.AuthenticatedUser;
import com.zaplink.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(
        summary = "List all users",
        description = "Returns all registered users with their link counts. Supports optional " +
                      "`search` (matches username or email, case-insensitive) and `status` filter " +
                      "(`active` or `disabled`). Results are not paginated. Requires ADMIN role."
    )
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.listUsers(search, status));
    }

    @Operation(
        summary = "Ban or unban a user",
        description = "Sets `is_active` on the target user. Banning also bulk-disables all of the user's " +
                      "active links (reason: USER_BANNED). Unbanning re-enables only USER_BANNED links — " +
                      "links disabled by an admin action are not touched. " +
                      "An admin cannot ban themselves. Requires ADMIN role."
    )
    @PatchMapping("/users/{id}/ban")
    public ResponseEntity<AdminUserResponse> setBanned(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminBanUserRequest request) {
        return ResponseEntity.ok(adminService.setUserBanned(principal.userId(), id, request.banned()));
    }

    @Operation(
        summary = "Delete a user",
        description = "Hard-deletes the target user. The user must be banned first (is_active = false). " +
                      "Their links are soft-deleted so click history is preserved for admin reports. " +
                      "The FK on links.user_id is ON DELETE SET NULL, so link rows become orphaned after deletion. " +
                      "An admin cannot delete themselves. Requires ADMIN role."
    )
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        adminService.deleteUser(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "List all links in the system",
        description = "Returns a paginated list of every link across all users. Supports optional " +
                      "`search` (matches short code, long URL, owner username or email) and `status` filter " +
                      "(`all`, `active`, `expired`, `disabled`, `deleted`). Includes click count and owner info. " +
                      "Soft-deleted links are visible when status=deleted or status=all. Requires ADMIN role."
    )
    @GetMapping("/links")
    public ResponseEntity<Page<AdminLinkResponse>> listLinks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "all") String status) {
        if (page < 0) {
            throw new InvalidQueryParamException("Page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new InvalidQueryParamException("Page size must be between 1 and 100");
        }
        return ResponseEntity.ok(adminService.listLinks(search, status, PageRequest.of(page, size)));
    }

    @Operation(
        summary = "Disable or re-enable a link",
        description = "Toggles `is_active` on a link with reason ADMIN_DISABLED. " +
                      "Links disabled due to a user ban (reason: USER_BANNED) cannot be modified here — " +
                      "unban the user to re-enable them. Soft-deleted links cannot be modified. " +
                      "Requires ADMIN role."
    )
    @PatchMapping("/links/{id}/disable")
    public ResponseEntity<AdminLinkResponse> setDisabled(
            @PathVariable Long id,
            @Valid @RequestBody AdminDisableLinkRequest request) {
        return ResponseEntity.ok(adminService.setLinkDisabled(id, request.disabled()));
    }

    @Operation(
        summary = "Get system-wide reports",
        description = "Returns aggregate statistics across the entire platform: user counts " +
                      "(total, active, banned, new in last 30 days), link counts " +
                      "(total, active, expired, disabled, deleted, orphaned, new in last 30 days), " +
                      "and click counts (total, last 30 days). Requires ADMIN role."
    )
    @GetMapping("/reports")
    public ResponseEntity<SystemReportsResponse> getReports() {
        return ResponseEntity.ok(adminService.getSystemReports());
    }
}
