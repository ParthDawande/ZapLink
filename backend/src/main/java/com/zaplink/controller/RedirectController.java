package com.zaplink.controller;

import com.zaplink.event.ClickRecordedEvent;
import com.zaplink.exception.LinkDisabledException;
import com.zaplink.exception.LinkExpiredException;
import com.zaplink.exception.LinkGoneException;
import com.zaplink.exception.LinkNotFoundException;
import com.zaplink.model.Link;
import com.zaplink.repository.LinkRepository;
import com.zaplink.util.ReservedShortCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import java.net.URI;
import java.time.LocalDateTime;

@Controller
public class RedirectController {

    @Value("${zaplink.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private final LinkRepository linkRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RedirectController(LinkRepository linkRepository, ApplicationEventPublisher eventPublisher) {
        this.linkRepository = linkRepository;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {

        // 1. Reject reserved keywords before touching the DB
        if (ReservedShortCodes.isReserved(shortCode)) {
            throw new LinkNotFoundException("Reserved short code");
        }

        // 2. Look up the short code; treat soft-deleted rows as not found
        Link link = linkRepository.findByShortCode(shortCode)
                .filter(l -> l.getDeletedAt() == null)  // belt-and-suspenders; @SQLRestriction also covers this
                .orElseThrow(() -> new LinkNotFoundException("Link not found"));

        // 3. Status precedence per API_DOCS.md: disabled → expired → redirect
        if (Boolean.FALSE.equals(link.getIsActive())) {
            throw new LinkDisabledException();
        }

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new LinkExpiredException();
        }

        // 4. Capture click time at request time, then publish async — 302 is not delayed
        eventPublisher.publishEvent(new ClickRecordedEvent(link.getId(), LocalDateTime.now()));

        // 5. 302 redirect — no-cache so every visit is tracked
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(link.getLongUrl()));
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    // ── Error page handlers (HTML only — /api/* errors stay JSON via GlobalExceptionHandler) ──

    // Local handlers take priority over GlobalExceptionHandler for exceptions thrown from this controller.

    @ExceptionHandler(LinkNotFoundException.class)
    public ModelAndView handleNotFound() {
        ModelAndView mav = new ModelAndView("link-error");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("title", "Link Not Found");
        mav.addObject("message", "This link doesn't exist.");
        mav.addObject("frontendUrl", frontendUrl);
        return mav;
    }

    @ExceptionHandler({LinkExpiredException.class, LinkDisabledException.class})
    public ModelAndView handleGone(LinkGoneException ex) {
        ModelAndView mav = new ModelAndView("link-error");
        mav.setStatus(HttpStatus.GONE);
        mav.addObject("title", ex.getTitle());
        mav.addObject("message", ex.getErrorMessage());
        mav.addObject("frontendUrl", frontendUrl);
        return mav;
    }
}
