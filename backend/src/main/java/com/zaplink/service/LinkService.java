package com.zaplink.service;

import com.zaplink.dto.CreateLinkRequest;
import com.zaplink.dto.LinkResponse;
import com.zaplink.exception.InvalidUrlException;
import com.zaplink.model.Link;
import com.zaplink.repository.LinkRepository;
import com.zaplink.util.Base62Encoder;
import com.zaplink.util.ReservedShortCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;

@Service
public class LinkService {

    @Value("${zaplink.base-url}")
    private String baseUrl;

    private final LinkRepository linkRepository;

    public LinkService(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @Transactional
    public LinkResponse createLink(Long userId, CreateLinkRequest request) {
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
            // misconfigured baseUrl — fail open rather than block the request
        }

        // c. expiresAt must be in the future if provided
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidUrlException("Expiration date must be in the future");
        }

        // d+e. Build initial entity (shortCode=null) and save to get the auto-generated id
        Link link = buildLink(userId, longUrl, request.getExpiresAt());
        link = linkRepository.save(link);

        // f+g. Encode id → Base62 short code; retry if the code is a reserved keyword
        String code = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            code = Base62Encoder.encode(link.getId());
            if (!ReservedShortCodes.isReserved(code)) {
                break;
            }
            // Mark the current slot as a placeholder so its id is permanently consumed
            link.setShortCode("__reserved__" + link.getId());
            link.setIsActive(false);
            linkRepository.save(link);
            // Advance the auto-increment by inserting a fresh real link
            link = buildLink(userId, longUrl, request.getExpiresAt());
            link = linkRepository.save(link);
            code = null;
        }

        if (code == null) {
            throw new IllegalStateException("Could not generate a non-reserved short code after 5 attempts");
        }

        // h. Stamp the short code onto the winning row
        link.setShortCode(code);
        linkRepository.save(link);

        // i. Build and return the response
        return new LinkResponse(
                link.getId(),
                code,
                baseUrl + "/" + code,
                link.getLongUrl(),
                link.getExpiresAt(),
                link.getIsActive(),
                link.getCreatedAt()
        );
    }

    private Link buildLink(Long userId, String longUrl, LocalDateTime expiresAt) {
        Link link = new Link();
        link.setUserId(userId);
        link.setLongUrl(longUrl);
        link.setShortCode(null);
        link.setIsActive(true);
        link.setExpiresAt(expiresAt);
        return link;
    }
}
