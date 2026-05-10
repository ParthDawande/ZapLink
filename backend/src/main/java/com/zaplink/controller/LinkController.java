package com.zaplink.controller;

import com.zaplink.dto.AnalyticsResponse;
import com.zaplink.dto.CreateLinkRequest;
import com.zaplink.dto.CreateLinkResult;
import com.zaplink.dto.LinkResponse;
import com.zaplink.exception.InvalidQueryParamException;
import com.zaplink.security.AuthenticatedUser;
import com.zaplink.service.LinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Links")
@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @Operation(
        summary = "Create a new short link",
        description = "Validates the URL format and scheme (http/https), checks it against the Google " +
                      "Safe Browsing blacklist, and generates a unique Base62 short code. " +
                      "If the same user has already shortened this exact URL and the existing link is not expired, " +
                      "the existing link is returned with 200 OK instead of 201 Created. " +
                      "Self-referencing ZapLink URLs are rejected."
    )
    @PostMapping
    public ResponseEntity<LinkResponse> createLink(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateLinkRequest request) {

        CreateLinkResult result = linkService.createLink(principal.userId(), request);
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @Operation(
        summary = "List all links owned by the current user",
        description = "Returns a paginated list of the authenticated user's links, sorted by creation date " +
                      "descending. Includes click count per link. Soft-deleted links are excluded. " +
                      "Page size is capped at 100."
    )
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

    @Operation(
        summary = "Get details of a specific link",
        description = "Returns the full details of a single link owned by the current user, including " +
                      "click count and status. Returns 404 for non-existent links or links owned by other users " +
                      "(to avoid leaking existence)."
    )
    @GetMapping("/{id}")
    public ResponseEntity<LinkResponse> getLink(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(linkService.getLinkForUser(principal.userId(), id));
    }

    @Operation(
        summary = "Delete a link",
        description = "Soft-deletes the link identified by {id}. The short code immediately stops redirecting. " +
                      "Click history is preserved for admin reporting. Returns 204 No Content on success. " +
                      "Returns 404 for links not owned by the current user."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLink(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {

        linkService.deleteLinkForUser(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get the QR code PNG for a short link",
        description = "Generates and returns a 300×300 PNG QR code encoding the short URL. " +
                      "The response Content-Type is image/png. Cached for 24 hours (Cache-Control: public, max-age=86400). " +
                      "Returns 404 for expired or disabled links."
    )
    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQrCode(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {

        byte[] png = linkService.getQrCodeForUser(principal.userId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(png.length)
                .header("Content-Disposition", "inline; filename=\"qr-" + id + ".png\"")
                .header("Cache-Control", "public, max-age=86400")
                .body(png);
    }

    @Operation(
        summary = "Get click analytics for a link",
        description = "Returns total click count, last-30-days click count, and a day-by-day breakdown " +
                      "for the past 30 days (zero-filled — every day is always present in the array). " +
                      "Returns 404 for links not owned by the current user."
    )
    @GetMapping("/{id}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {

        return ResponseEntity.ok(linkService.getAnalyticsForUser(principal.userId(), id));
    }
}
