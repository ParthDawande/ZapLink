package com.zaplink.controller;

import com.zaplink.dto.AnalyticsResponse;
import com.zaplink.dto.CreateLinkRequest;
import com.zaplink.dto.CreateLinkResult;
import com.zaplink.dto.LinkResponse;
import com.zaplink.exception.InvalidQueryParamException;
import com.zaplink.security.AuthenticatedUser;
import com.zaplink.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> createLink(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateLinkRequest request) {

        CreateLinkResult result = linkService.createLink(principal.userId(), request);
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLink(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {

        linkService.deleteLinkForUser(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LinkResponse> getLink(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(linkService.getLinkForUser(principal.userId(), id));
    }

    @GetMapping("/{id}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(linkService.getAnalyticsForUser(principal.userId(), id));
    }

    @GetMapping
    public ResponseEntity<Page<LinkResponse>> listLinks(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) {
            throw new InvalidQueryParamException("Page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new InvalidQueryParamException("Page size must be between 1 and 100");
        }

        Page<LinkResponse> result = linkService.listLinksForUser(
                principal.userId(), PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }
}
