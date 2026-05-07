package com.zaplink.controller;

import com.zaplink.model.Click;
import com.zaplink.model.Link;
import com.zaplink.repository.ClickRepository;
import com.zaplink.repository.LinkRepository;
import com.zaplink.util.ReservedShortCodes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
public class RedirectController {

    private static final String DISABLED_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><title>Link Disabled</title></head>
            <body>
              <h2>This link has been disabled.</h2>
              <p><a href="/">Back to ZapLink</a></p>
            </body>
            </html>
            """;

    private static final String EXPIRED_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><title>Link Expired</title></head>
            <body>
              <h2>This link has expired.</h2>
              <p><a href="/">Back to ZapLink</a></p>
            </body>
            </html>
            """;

    private final LinkRepository linkRepository;
    private final ClickRepository clickRepository;

    public RedirectController(LinkRepository linkRepository, ClickRepository clickRepository) {
        this.linkRepository = linkRepository;
        this.clickRepository = clickRepository;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode) {

        // 1. Reject reserved keywords before touching the DB
        if (ReservedShortCodes.isReserved(shortCode)) {
            return ResponseEntity.notFound().build();
        }

        // 2. Look up the short code
        Optional<Link> optLink = linkRepository.findByShortCode(shortCode);
        if (optLink.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Link link = optLink.get();

        // 3. Soft-deleted → treat as not found (don't reveal the link existed)
        if (link.getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }

        // 4. Disabled by admin or user-ban cascade → 410 Gone
        if (Boolean.FALSE.equals(link.getIsActive())) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.TEXT_HTML)
                    .body(DISABLED_HTML);
        }

        // 5. Past expiration → 410 Gone
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.TEXT_HTML)
                    .body(EXPIRED_HTML);
        }

        // 6. Log the click synchronously (will be made async in Phase 4)
        Click click = new Click();
        click.setLinkId(link.getId());
        clickRepository.save(click);

        // 7. 302 redirect — no-cache so every visit is tracked
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(link.getLongUrl()));
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
