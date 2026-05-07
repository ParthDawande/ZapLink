package com.zaplink.controller;

import com.zaplink.dto.CreateLinkRequest;
import com.zaplink.dto.LinkResponse;
import com.zaplink.security.AuthenticatedUser;
import com.zaplink.service.LinkService;
import jakarta.validation.Valid;
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

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(linkService.createLink(principal.userId(), request));
    }
}
