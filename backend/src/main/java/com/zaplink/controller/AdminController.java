package com.zaplink.controller;

import com.zaplink.dto.AdminBanUserRequest;
import com.zaplink.dto.AdminDisableLinkRequest;
import com.zaplink.dto.AdminLinkResponse;
import com.zaplink.dto.AdminUserResponse;
import com.zaplink.dto.SystemReportsResponse;
import com.zaplink.exception.InvalidQueryParamException;
import com.zaplink.security.AuthenticatedUser;
import com.zaplink.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ── Endpoint 11 — GET /api/admin/users ───────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminService.listUsers(search, status));
    }

    // ── Endpoint 14 — GET /api/admin/links ───────────────────────────────────

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

    // ── Endpoint 13 — DELETE /api/admin/users/{id} ───────────────────────────

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        adminService.deleteUser(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Endpoint 12 — PATCH /api/admin/users/{id}/ban ────────────────────────

    @PatchMapping("/users/{id}/ban")
    public ResponseEntity<AdminUserResponse> setBanned(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody AdminBanUserRequest request) {
        return ResponseEntity.ok(adminService.setUserBanned(principal.userId(), id, request.banned()));
    }

    // ── Endpoint 15 — PATCH /api/admin/links/{id}/disable ────────────────────

    @PatchMapping("/links/{id}/disable")
    public ResponseEntity<AdminLinkResponse> setDisabled(
            @PathVariable Long id,
            @Valid @RequestBody AdminDisableLinkRequest request) {
        return ResponseEntity.ok(adminService.setLinkDisabled(id, request.disabled()));
    }

    // ── Endpoint 16 — GET /api/admin/reports ─────────────────────────────────

    @GetMapping("/reports")
    public ResponseEntity<SystemReportsResponse> getReports() {
        return ResponseEntity.ok(adminService.getSystemReports());
    }
}
