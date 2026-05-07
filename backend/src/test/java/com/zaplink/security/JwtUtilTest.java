package com.zaplink.security;

import com.zaplink.model.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=test-secret-for-jwt-unit-testing-minimum-32-characters-here",
        "jwt.expiration-ms=3600000"
})
class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    private static final String TEST_SECRET =
            "test-secret-for-jwt-unit-testing-minimum-32-characters-here";

    @Test
    void generateToken_roundtrip_recoversAllClaims() {
        String token = jwtUtil.generateToken(42L, "alice", Role.USER);

        assertEquals(42L, jwtUtil.getUserIdFromToken(token));
        assertEquals("alice", jwtUtil.getUsernameFromToken(token));
        assertEquals(Role.USER, jwtUtil.getRoleFromToken(token));
    }

    @Test
    void validateToken_freshToken_returnsTrue() {
        String token = jwtUtil.generateToken(1L, "bob", Role.ADMIN);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_garbageToken_returnsFalse() {
        assertFalse(jwtUtil.validateToken("garbage.token.here"));
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("99")
                .claim("username", "carol")
                .claim("role", Role.USER.name())
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtUtil.validateToken(expiredToken));
    }
}
