# ⚡ ZapLink — API Documentation

Detailed reference for every REST endpoint exposed by the ZapLink backend. Each endpoint section covers the request shape, validation rules, internal logic (step-by-step), database queries, success and error responses.

> 📘 **Live API Docs:** Once the backend is running, an interactive Swagger UI is also available at **http://localhost:8080/swagger-ui.html**.

---

## 📑 Table of Contents

- [Conventions](#conventions)
- [Schema Reference](#schema-reference)
- 🔐 **Authentication**
  - [POST /api/auth/register](#1-post-apiauthregister)
  - [POST /api/auth/login](#2-post-apiauthlogin)
  - [GET /api/me](#3-get-apime)
- 🔗 **Links**
  - [POST /api/links](#4-post-apilinks)
  - [GET /api/links](#5-get-apilinks)
  - [GET /api/links/{id}](#6-get-apilinksid)
  - [DELETE /api/links/{id}](#7-delete-apilinksid)
  - [GET /api/links/{id}/qr](#8-get-apilinksidqr)
  - [GET /{shortCode}](#9-get-shortcode)
- 📊 **Analytics**
  - [GET /api/links/{id}/analytics](#10-get-apilinksidanalytics)
- 🛠️ **Admin**
  - [GET /api/admin/users](#11-get-apiadminusers)
  - [PATCH /api/admin/users/{id}/ban](#12-patch-apiadminusersidban)
  - [DELETE /api/admin/users/{id}](#13-delete-apiadminusersid)
  - [GET /api/admin/links](#14-get-apiadminlinks)
  - [PATCH /api/admin/links/{id}/disable](#15-patch-apiadminlinksiddisable)
  - [GET /api/admin/reports](#16-get-apiadminreports)

---

## Conventions

- **Base URL:** `http://localhost:8080` in development. The configured base URL is exposed via the `zaplink.base-url` property and is used to construct `shortUrl` values in responses.
- **Authentication:** All `/api/*` endpoints require a JWT in the `Authorization: Bearer <token>` header **except** `/api/auth/register` and `/api/auth/login`. The redirect endpoint `/{shortCode}` is fully public.
- **Authorization:** Endpoints under `/api/admin/*` require the authenticated user to have `role = ADMIN`.
- **Date format:** All timestamps are ISO-8601 (e.g., `2025-05-04T10:15:30`).
- **Rate limiting:** All `/api/*` endpoints are subject to per-user rate limiting via Bucket4j. Exceeding the limit returns `429 Too Many Requests`.
- **Error envelope:** All non-2xx JSON responses follow this shape:
  ```json
  {
    "timestamp": "2025-05-04T10:15:30",
    "status": 400,
    "error": "Bad Request",
    "message": "Long URL is required",
    "path": "/api/links"
  }
  ```
  The redirect endpoint (`GET /{shortCode}`) is the only exception — its error responses are HTML pages, not JSON.
- **404 vs 403 for ownership:** When a user tries to access a resource they don't own, endpoints return `404 Not Found` (not `403 Forbidden`) to avoid leaking the existence of other users' resources. Admin endpoints, by contrast, return `403` to non-admins because the *category* of admin endpoints is public knowledge.

---

## Schema Reference

The endpoints assume the following MySQL schema. The full schema lives in `backend/src/main/resources/schema.sql`; the key columns referenced throughout this document are summarized below.

### `users`
| Column          | Type                  | Constraints                                |
|-----------------|-----------------------|--------------------------------------------|
| `id`            | BIGINT                | PRIMARY KEY, AUTO_INCREMENT                |
| `username`      | VARCHAR(50)           | UNIQUE, NOT NULL                           |
| `email`         | VARCHAR(100)          | UNIQUE, NOT NULL                           |
| `password_hash` | VARCHAR(255)          | NOT NULL (BCrypt)                          |
| `role`          | ENUM('USER', 'ADMIN') | NOT NULL, DEFAULT 'USER'                   |
| `is_active`     | BOOLEAN               | NOT NULL, DEFAULT TRUE                     |
| `created_at`    | TIMESTAMP             | NOT NULL, DEFAULT CURRENT_TIMESTAMP        |

### `links`
| Column            | Type                                       | Constraints                                                            |
|-------------------|--------------------------------------------|------------------------------------------------------------------------|
| `id`              | BIGINT                                     | PRIMARY KEY, AUTO_INCREMENT                                            |
| `short_code`      | VARCHAR(10)                                | UNIQUE, NULL during the brief creation window (see Endpoint 4)         |
| `long_url`        | TEXT                                       | NOT NULL                                                               |
| `user_id`         | BIGINT                                     | NULL, FK → `users(id)` **ON DELETE SET NULL** (orphans on user delete) |
| `expires_at`      | TIMESTAMP                                  | NULL                                                                   |
| `is_active`       | BOOLEAN                                    | NOT NULL, DEFAULT TRUE                                                 |
| `disabled_reason` | ENUM('USER_BANNED', 'ADMIN_DISABLED')      | NULL (NULL = not disabled)                                             |
| `deleted_at`      | TIMESTAMP                                  | NULL (NULL = not deleted; non-NULL = soft-deleted)                     |
| `created_at`      | TIMESTAMP                                  | NOT NULL, DEFAULT CURRENT_TIMESTAMP                                    |

### `clicks`
| Column        | Type       | Constraints                                                |
|---------------|------------|------------------------------------------------------------|
| `id`          | BIGINT     | PRIMARY KEY, AUTO_INCREMENT                                |
| `link_id`     | BIGINT     | NOT NULL, FK → `links(id)` **ON DELETE CASCADE**           |
| `clicked_at`  | TIMESTAMP  | NOT NULL, DEFAULT CURRENT_TIMESTAMP                        |

### Recommended indexes
- `clicks(link_id, clicked_at)` — composite, used by analytics queries (Endpoint 10) and the redirect path (Endpoint 9).
- `links(user_id, deleted_at)` — used by the user's link list (Endpoint 5).
- `links(short_code)` — already implied by the UNIQUE constraint, used by the redirect endpoint.

---

# 🔐 Authentication

## 1. POST /api/auth/register

**Description:** Registers a new user account in the system.

**Auth:** Public (no token required)

### Request
- **Method:** POST
- **URL:** `/api/auth/register`
- **Headers:** `Content-Type: application/json`
- **Body:**
  ```json
  {
    "username": "johndoe",
    "email": "john@example.com",
    "password": "securePass123"
  }
  ```

### Validation Rules
| Field      | Rule                                                                 |
|------------|----------------------------------------------------------------------|
| `username` | Required, 3–50 characters, alphanumeric + underscore only, unique    |
| `email`    | Required, valid email format, max 100 characters, unique             |
| `password` | Required, min 8 characters, must contain at least 1 letter & 1 digit |

### Internal Logic (step-by-step)
1. Receive registration request at `AuthController.register(RegisterRequest)`
2. Validate request body using `@Valid` (Jakarta Bean Validation annotations on DTO)
3. Call `AuthService.register(request)`:
   1. Check if `username` already exists in `users` table → if yes, throw `UsernameAlreadyExistsException`
   2. Check if `email` already exists in `users` table → if yes, throw `EmailAlreadyExistsException`
   3. Hash the plain password using `BCryptPasswordEncoder` (strength = 10)
   4. Build a new `User` entity with:
      - `username`, `email` from request
      - `password_hash` = hashed password
      - `role` = `USER` (default)
      - `is_active` = `true`
      - `created_at` = current timestamp (auto-set by DB)
   5. Save the entity via `UserRepository.save(user)`
4. Map the saved `User` entity to a `UserResponse` DTO (excluding `password_hash`)
5. Return `201 Created` with the user info

### Database Queries
```sql
-- Uniqueness checks
SELECT id FROM users WHERE username = ?;
SELECT id FROM users WHERE email = ?;

-- Insert new user
INSERT INTO users (username, email, password_hash, role, is_active)
VALUES (?, ?, ?, 'USER', TRUE);
```

### Success Response
- **Status:** `201 Created`
- **Body:**
  ```json
  {
    "id": 1,
    "username": "johndoe",
    "email": "john@example.com",
    "role": "USER",
    "createdAt": "2025-05-04T10:15:30"
  }
  ```

### Error Responses
| Status | Reason                | Example Message                                  |
|--------|-----------------------|--------------------------------------------------|
| 400    | Validation failed     | "Password must be at least 8 characters"         |
| 400    | Invalid email format  | "Email is invalid"                               |
| 409    | Username conflict     | "Username 'johndoe' is already taken"            |
| 409    | Email conflict        | "Email 'john@example.com' is already registered" |

---

## 2. POST /api/auth/login

**Description:** Authenticates a user via email + password and returns a JWT token used for subsequent requests.

**Auth:** Public (no token required)

### Request
- **Method:** POST
- **URL:** `/api/auth/login`
- **Headers:** `Content-Type: application/json`
- **Body:**
  ```json
  {
    "email": "john@example.com",
    "password": "securePass123"
  }
  ```

### Validation Rules
| Field      | Rule                          |
|------------|-------------------------------|
| `email`    | Required, not blank           |
| `password` | Required, not blank           |

> Note: We don't enforce format rules here (length, complexity) — those are checked at registration. Login only validates that fields are present, then defers to the actual credential check.

### Internal Logic (step-by-step)
1. Receive login request at `AuthController.login(LoginRequest)`
2. Validate request body using `@Valid`
3. Call `AuthService.login(request)`:
   1. Look up the user by `email` in the `users` table
      - If not found → throw `BadCredentialsException` (return 401, generic message)
   2. Check `user.is_active`
      - If `false` → throw `AccountDisabledException` (return 403)
   3. Compare the provided plain password against `user.password_hash` using `BCryptPasswordEncoder.matches()`
      - If mismatch → throw `BadCredentialsException` (return 401, generic message)
   4. Build JWT claims:
      - `sub` = user id
      - `email` = user.email
      - `role` = user.role
      - `iat` = current timestamp
      - `exp` = current timestamp + `zaplink.jwt.expiration-ms`
   5. Sign the token using `zaplink.jwt.secret` (HS256)
4. Return `200 OK` with the token and expiration info

> 🔒 **Security note:** The same generic error message is returned for "user not found" and "wrong password" to prevent email enumeration attacks.

### Database Queries
```sql
-- Look up user by email
SELECT id, username, email, password_hash, role, is_active
FROM users
WHERE email = ?;
```

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
    "tokenType": "Bearer",
    "expiresIn": 86400000
  }
  ```

After receiving the token, the client should call `GET /api/me` to fetch the authenticated user's profile.

### Error Responses
| Status | Reason                  | Example Message                       |
|--------|-------------------------|---------------------------------------|
| 400    | Validation failed       | "Email is required"                   |
| 401    | Invalid credentials     | "Invalid email or password"           |
| 403    | Account disabled/banned | "Your account has been disabled"      |

---

## 3. GET /api/me

**Description:** Returns the profile of the currently authenticated user. Typically called by the frontend right after login (or on app load) to populate user state.

**Auth:** Required (JWT)

### Request
- **Method:** GET
- **URL:** `/api/me`
- **Headers:**
  - `Authorization: Bearer <jwt_token>`
- **Body:** None

### Validation Rules
None — the only requirement is a valid JWT in the `Authorization` header. JWT validation is handled centrally by the `JwtAuthenticationFilter`.

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` (Spring Security filter chain):
   1. Extract token from `Authorization: Bearer ...` header
      - If missing or malformed → return 401
   2. Validate signature using `zaplink.jwt.secret`
      - If invalid → return 401
   3. Check `exp` claim
      - If expired → return 401
   4. Extract `sub` (user id) and `role` from claims
   5. Build a Spring Security `Authentication` object and place it in the `SecurityContextHolder`
2. Request reaches `AuthController.getCurrentUser(Authentication)`
3. Call `AuthService.getCurrentUser(userId)`:
   1. Look up the user by `id` in the `users` table
      - If not found → throw `UserNotFoundException` (rare edge case: token valid but user was deleted)
   2. Check `user.is_active`
      - If `false` → throw `AccountDisabledException` (return 403)
4. Map the `User` entity to a `UserResponse` DTO (excluding `password_hash`)
5. Return `200 OK` with the user info

### Database Queries
```sql
-- Fetch the authenticated user
SELECT id, username, email, role, is_active, created_at
FROM users
WHERE id = ?;
```

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "id": 1,
    "username": "johndoe",
    "email": "john@example.com",
    "role": "USER",
    "createdAt": "2025-05-04T10:15:30"
  }
  ```

### Error Responses
| Status | Reason                          | Example Message                  |
|--------|---------------------------------|----------------------------------|
| 401    | Missing / invalid / expired JWT | "Authentication required"        |
| 403    | Account disabled/banned         | "Your account has been disabled" |
| 404    | User no longer exists           | "User not found"                 |

---

# 🔗 Links

## 4. POST /api/links

**Description:** Creates a new short link for the currently authenticated user. Validates the URL, checks against a malicious URL blacklist, applies rate limiting, generates a unique short code, and persists the link. If the user has already shortened this exact URL before, returns the existing short link instead.

**Auth:** Required (JWT)

### Request
- **Method:** POST
- **URL:** `/api/links`
- **Headers:**
  - `Authorization: Bearer <jwt_token>`
  - `Content-Type: application/json`
- **Body:**
  ```json
  {
    "longUrl": "https://example.com/some/very/long/article-url",
    "expiresAt": "2025-12-31T23:59:59"
  }
  ```

### Validation Rules
| Field        | Rule                                                                                  |
|--------------|---------------------------------------------------------------------------------------|
| `longUrl`    | Required, valid URL format (must start with `http://` or `https://`), max 2048 chars  |
| `expiresAt`  | Optional, must be a future timestamp (after current time)                             |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → user authenticated, `userId` available in `SecurityContext`
2. Request hits `RateLimitFilter` (Bucket4j):
   1. Look up the user's bucket (keyed by `userId`)
   2. Try to consume 1 token
      - If bucket empty → return `429 Too Many Requests`
3. Request reaches `LinkController.createLink(CreateLinkRequest)`
4. Validate request body using `@Valid`
5. Call `LinkService.createLink(userId, request)`:
   1. **URL format validation** — use `java.net.URI` + custom checks:
      - Scheme must be `http` or `https`
      - Host must be non-empty
      - If invalid → throw `InvalidUrlException` (return 400)
   2. **Self-referencing check** — reject URLs pointing back to ZapLink itself (e.g., `zaplink.base-url` or its short codes) to prevent infinite redirect loops
      - If self-referencing → throw `InvalidUrlException` (return 400)
   3. **Deduplication check** — query the `links` table for a record where `user_id = ?` AND `long_url = ?` AND `is_active = TRUE` AND `deleted_at IS NULL`:
      - If a match exists and is **not expired** → return that existing link as the response (skip the rest of the logic). Set HTTP status to `200 OK` instead of `201 Created` to signal "already existed".
      - If a match exists but is expired → ignore it and proceed to create a new one.
   4. **Malicious URL check** — call `SafeBrowsingService.isSafe(longUrl)`:
      - Sends the URL to Google Safe Browsing API
      - If the API flags it (MALWARE, SOCIAL_ENGINEERING, UNWANTED_SOFTWARE) → throw `MaliciousUrlException` (return 400)
      - If the API call fails (timeout/error) → log warning and **fail open** (allow the URL); we don't want a third-party outage to block link creation
   5. **Build and save the Link entity** (without short_code yet):
      - `long_url` = request.longUrl
      - `user_id` = current user's id
      - `expires_at` = request.expiresAt (or null)
      - `is_active` = true
      - `created_at` = auto-set by DB
   6. **Save** via `LinkRepository.save(link)` → returns the entity with the auto-generated `id`
   7. **Generate short code** using `Base62Encoder.encode(link.id)`:
      - Converts the numeric id (e.g., 12345) to a Base62 string (e.g., "3d7")
      - **Reserved keyword check** — if the generated code matches a reserved keyword (`api`, `admin`, `login`, `swagger-ui`, etc.), the service rolls forward: it deletes the just-inserted row, retries the insert (which gets a fresh auto-increment id), and re-encodes. Maintained in a single config class (`ReservedShortCodes.SET`).
      - Guarantees uniqueness (1:1 mapping with id, minus reserved skips)
      - Handles concurrency naturally (each insert gets its own id)
   8. **Update the link** with the generated `short_code` and save again
6. Build a `LinkResponse` DTO including the full `shortUrl` (`zaplink.base-url + "/" + shortCode`)
7. Return `201 Created` (or `200 OK` if dedup match)

> 💡 **Why insert-then-encode?** Generating the short code from the auto-increment `id` means there's no collision possible. The two-step save (insert blank short_code, then update with generated code) is wrapped in a single `@Transactional` method so it's atomic.

> 🔁 **Deduplication scope:** Dedup applies **per user**. Two different users shortening the same URL each get their own short link (so they each own their own analytics, deletion rights, etc.). Dedup excludes soft-deleted links — re-shortening a previously-deleted URL produces a brand new link.

### Database Queries
```sql
-- Step 1: Deduplication check (per-user, excludes soft-deleted)
SELECT id, short_code, long_url, expires_at, is_active, created_at
FROM links
WHERE user_id = ?
  AND long_url = ?
  AND is_active = TRUE
  AND deleted_at IS NULL;

-- Step 2: Insert link (short_code temporarily NULL)
INSERT INTO links (short_code, long_url, user_id, expires_at, is_active)
VALUES (NULL, ?, ?, ?, TRUE);

-- Step 3: Update with the generated short_code (Base62 of the new id)
UPDATE links SET short_code = ? WHERE id = ?;
```

> ⚠️ The `short_code` column allows NULL during the brief window between insert and update. The whole operation runs in a single transaction so external readers never see a NULL `short_code`.

### Success Response
- **Status:** `201 Created` (new link) or `200 OK` (existing link returned via dedup)
- **Body:**
  ```json
  {
    "id": 42,
    "shortCode": "abc123",
    "shortUrl": "http://localhost:8080/abc123",
    "longUrl": "https://example.com/some/very/long/article-url",
    "expiresAt": "2025-12-31T23:59:59",
    "isActive": true,
    "createdAt": "2025-05-04T10:15:30"
  }
  ```

### Error Responses
| Status | Reason                       | Example Message                                  |
|--------|------------------------------|--------------------------------------------------|
| 400    | Validation failed            | "Long URL is required"                           |
| 400    | Invalid URL format           | "URL must start with http:// or https://"        |
| 400    | URL too long                 | "URL exceeds maximum length of 2048 characters"  |
| 400    | Self-referencing URL         | "Cannot shorten a ZapLink URL"                   |
| 400    | Malicious URL                | "This URL has been flagged as unsafe"            |
| 400    | Past expiration date         | "Expiration date must be in the future"          |
| 401    | Missing / invalid JWT        | "Authentication required"                        |
| 429    | Rate limit exceeded          | "Too many requests. Please try again later."     |

---

## 5. GET /api/links

**Description:** Returns a paginated list of all short links owned by the currently authenticated user, including a click count for each link. Results are ordered by creation date, newest first.

**Auth:** Required (JWT)

### Request
- **Method:** GET
- **URL:** `/api/links`
- **Headers:**
  - `Authorization: Bearer <jwt_token>`
- **Query Parameters:**
  | Param   | Type | Default | Description                              |
  |---------|------|---------|------------------------------------------|
  | `page`  | int  | 0       | Page number (zero-indexed)               |
  | `size`  | int  | 20      | Number of links per page (max 100)       |
- **Body:** None

### Validation Rules
| Field   | Rule                            |
|---------|---------------------------------|
| `page`  | Must be ≥ 0                     |
| `size`  | Must be between 1 and 100       |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → user authenticated, `userId` available in `SecurityContext`
2. Request reaches `LinkController.listLinks(Authentication, Pageable)`
3. Spring resolves the `Pageable` from `page` and `size` query params (default sort: `createdAt DESC`, applied server-side)
4. Validate page size — if outside 1–100, throw `InvalidQueryParamException` (return 400)
5. Call `LinkService.listLinksForUser(userId, pageable)`:
   1. Build a JPA query that:
      - Filters `user_id = userId`
      - Excludes soft-deleted links (`deleted_at IS NULL`)
      - Performs a `LEFT JOIN` on the `clicks` table, grouped by `link.id`, to compute `clickCount` per link
      - Applies pagination
      - Orders by `created_at DESC` (newest first)
   2. Execute the query → returns `Page<LinkWithClickCount>`
6. For each link, compute a `status` field at response time. **Checks are evaluated in this exact order** — the first match wins:
   1. `"disabled"` if `is_active = FALSE`
   2. `"expired"` if `expires_at IS NOT NULL` AND `expires_at <= NOW()`
   3. `"active"` otherwise
7. Map each row to a `LinkResponse` DTO including:
   - All link fields
   - `disabledReason` (mirrors the DB column; always `null` for active/expired links, always `"ADMIN_DISABLED"` for disabled links in this endpoint — see note below)
   - `clickCount` from the aggregation
   - `shortUrl` (built from `zaplink.base-url + "/" + shortCode`)
   - `status` (computed above)
8. Wrap results in a paginated response envelope and return `200 OK`

> 💡 **Why "disabled" wins over "expired":** A link can be both disabled AND past its expiration date. The status field returns the more *actionable* state — "disabled" reflects a deliberate action (by an admin), while "expired" is just time passing. The frontend can still inspect `expires_at` directly if it needs to surface both facts.

> 🔒 **Why `disabledReason` is always `ADMIN_DISABLED` here (when present):** This endpoint is the user's view of their *own* links. To reach it, the user must currently be unbanned (banned users get 403 at the auth layer). When a user is unbanned, all their `USER_BANNED`-flagged links are auto-restored (see Endpoint 12). So if a link the user owns is currently disabled, the only possible reason is `ADMIN_DISABLED`. The field is included in the response for consistency with the admin endpoints and to make the contract explicit.

> 💡 **On click count:** Computing `clickCount` via a `LEFT JOIN clicks` + `COUNT(clicks.id) GROUP BY links.id` keeps the schema simple but means analytics queries scale with click volume. Acceptable for portfolio scale. Denormalizing a `click_count` column on `links` is listed in the Roadmap.

> 🗑️ **On deletion:** This endpoint shows only links where the user is the owner. Soft-deleted links don't appear here.

> 💡 **Spring Data tip:** You can implement the soft-delete filter cleanly with Hibernate's `@SQLRestriction("deleted_at IS NULL")` annotation on the `Link` entity, which automatically appends the filter to all queries.

### Database Queries
```sql
-- Main query
SELECT
    l.id,
    l.short_code,
    l.long_url,
    l.user_id,
    l.expires_at,
    l.is_active,
    l.disabled_reason,
    l.created_at,
    COUNT(c.id) AS click_count
FROM links l
LEFT JOIN clicks c ON c.link_id = l.id
WHERE l.user_id = ?
  AND l.deleted_at IS NULL
GROUP BY l.id
ORDER BY l.created_at DESC
LIMIT ? OFFSET ?;

-- Count query (for pagination metadata)
SELECT COUNT(*) FROM links
WHERE user_id = ? AND deleted_at IS NULL;
```

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "content": [
      {
        "id": 42,
        "shortCode": "abc123",
        "shortUrl": "http://localhost:8080/abc123",
        "longUrl": "https://example.com/some/very/long/article-url",
        "expiresAt": "2025-12-31T23:59:59",
        "isActive": true,
        "disabledReason": null,
        "status": "active",
        "clickCount": 137,
        "createdAt": "2025-05-04T10:15:30"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 47,
    "totalPages": 3,
    "first": true,
    "last": false
  }
  ```

### Error Responses
| Status | Reason                       | Example Message                                  |
|--------|------------------------------|--------------------------------------------------|
| 400    | Page size too large/small    | "Page size must be between 1 and 100"            |
| 400    | Negative page number         | "Page must be greater than or equal to 0"        |
| 401    | Missing / invalid JWT        | "Authentication required"                        |

---

## 6. GET /api/links/{id}

**Description:** Returns the full details of a single short link owned by the currently authenticated user, including its click count and computed status.

**Auth:** Required (JWT)

### Request
- **Method:** GET
- **URL:** `/api/links/{id}`
- **Headers:**
  - `Authorization: Bearer <jwt_token>`
- **Path Parameters:**
  | Param | Type   | Description                  |
  |-------|--------|------------------------------|
  | `id`  | long   | The numeric ID of the link   |
- **Body:** None

### Validation Rules
| Field | Rule                                |
|-------|-------------------------------------|
| `id`  | Must be a positive long integer     |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → user authenticated, `userId` available in `SecurityContext`
2. Request reaches `LinkController.getLink(Authentication, @PathVariable id)`
3. Validate that `id` is a positive number (Spring handles the type conversion; negative or zero ids fail validation)
4. Call `LinkService.getLinkForUser(userId, linkId)`:
   1. Look up the link by `id` in the `links` table, joining `clicks` to compute `clickCount`. The query also enforces ownership (`user_id = userId`) and excludes soft-deleted (`deleted_at IS NULL`) in the WHERE clause.
      - If no row → throw `LinkNotFoundException` (return 404)
5. Compute the link's `status` at response time. **Checks are evaluated in this exact order** — the first match wins:
   1. `"disabled"` if `is_active = FALSE`
   2. `"expired"` if `expires_at IS NOT NULL` AND `expires_at <= NOW()`
   3. `"active"` otherwise
6. Map the entity to a `LinkResponse` DTO with `shortUrl`, `clickCount`, `disabledReason`, and `status` (same shape as Endpoint 5)
7. Return `200 OK` with the link details

> 💡 **Status precedence and `disabledReason`:** Same rules as Endpoint 5 — "disabled" wins over "expired", and `disabledReason` (when non-null) will always be `"ADMIN_DISABLED"` for the user's own view. See Endpoint 5 for the full reasoning.

> 🔒 **Why 404 instead of 403 for ownership mismatch?** Returning `403 Forbidden` would leak information — it tells an attacker that a link with that `id` exists but belongs to someone else. Returning `404 Not Found` is indistinguishable from "the link truly doesn't exist", which is more secure.

> 💡 The ownership check is enforced **in the WHERE clause itself** (`l.user_id = ?`). This means the query returns no row if either the link doesn't exist or it belongs to someone else — both cases yield a 404. Defense-in-depth: even if a service-layer bug skipped the ownership check, the DB query wouldn't return another user's link.

### Database Queries
```sql
-- Fetch link + click count, scoped to the requesting user, excluding soft-deleted
SELECT
    l.id,
    l.short_code,
    l.long_url,
    l.user_id,
    l.expires_at,
    l.is_active,
    l.disabled_reason,
    l.created_at,
    COUNT(c.id) AS click_count
FROM links l
LEFT JOIN clicks c ON c.link_id = l.id
WHERE l.id = ?
  AND l.user_id = ?
  AND l.deleted_at IS NULL
GROUP BY l.id;
```

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "id": 42,
    "shortCode": "abc123",
    "shortUrl": "http://localhost:8080/abc123",
    "longUrl": "https://example.com/some/very/long/article-url",
    "expiresAt": "2025-12-31T23:59:59",
    "isActive": true,
    "disabledReason": null,
    "status": "active",
    "clickCount": 137,
    "createdAt": "2025-05-04T10:15:30"
  }
  ```

### Error Responses
| Status | Reason                       | Example Message                                  |
|--------|------------------------------|--------------------------------------------------|
| 400    | Invalid id format            | "Link id must be a positive number"              |
| 401    | Missing / invalid JWT        | "Authentication required"                        |
| 404    | Link not found OR not owned  | "Link not found"                                 |

---

## 7. DELETE /api/links/{id}

**Description:** Soft-deletes a short link owned by the currently authenticated user. The link's data and click history are retained in the database, but the link is no longer visible in the user's dashboard, becomes ineligible for deduplication, and returns 404 when its short URL is visited.

**Auth:** Required (JWT)

### Request
- **Method:** DELETE
- **URL:** `/api/links/{id}`
- **Headers:**
  - `Authorization: Bearer <jwt_token>`
- **Path Parameters:**
  | Param | Type | Description                  |
  |-------|------|------------------------------|
  | `id`  | long | The numeric ID of the link   |
- **Body:** None

### Validation Rules
| Field | Rule                                |
|-------|-------------------------------------|
| `id`  | Must be a positive long integer     |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → user authenticated, `userId` available in `SecurityContext`
2. Request reaches `LinkController.deleteLink(Authentication, @PathVariable id)`
3. Validate that `id` is a positive number
4. Call `LinkService.deleteLinkForUser(userId, linkId)` (wrapped in `@Transactional`):
   1. Look up the link by `id` AND `user_id = userId` AND `deleted_at IS NULL`
      - If not found (doesn't exist, not owned, or already deleted) → throw `LinkNotFoundException` (return 404)
   2. **Soft delete** — set `deleted_at = NOW()` on the link row
5. Return `204 No Content`

> 🗑️ **Soft delete behavior:**
> - The link disappears from `GET /api/links` and `GET /api/links/{id}`
> - Visiting the short URL (`GET /{shortCode}`) returns 404
> - Click history is preserved in the `clicks` table for system-wide admin reporting
> - The link is excluded from deduplication checks on future `POST /api/links` calls (so the user can re-shorten the same URL and get a brand new link)

> 🔒 **Why 404 for ownership mismatch / already-deleted?** Same reasoning as `GET /api/links/{id}` — returning 403 would leak that the link exists but belongs to someone else. The WHERE clause `id = ? AND user_id = ? AND deleted_at IS NULL` collapses all "can't delete this" cases into a uniform 404.

> ⚙️ **DB-level safety net:** The `clicks.link_id` foreign key uses `ON DELETE CASCADE`. Soft delete doesn't trigger it (no actual `DELETE`), but if a future cleanup job hard-deletes truly old soft-deleted links, the cascade will clean up their clicks automatically.

### Database Queries
```sql
-- Step 1: Verify ownership and that link is not already deleted
SELECT id FROM links
WHERE id = ? AND user_id = ? AND deleted_at IS NULL;

-- Step 2: Soft delete
UPDATE links
SET deleted_at = NOW()
WHERE id = ? AND user_id = ? AND deleted_at IS NULL;
```

> 💡 The duplicated condition in step 2 makes the UPDATE idempotent — even under unlikely concurrent-delete races, only one update succeeds.

### Success Response
- **Status:** `204 No Content`
- **Body:** *(empty)*

### Error Responses
| Status | Reason                                        | Example Message                       |
|--------|-----------------------------------------------|---------------------------------------|
| 400    | Invalid id format                             | "Link id must be a positive number"   |
| 401    | Missing / invalid JWT                         | "Authentication required"             |
| 404    | Link not found, not owned, or already deleted | "Link not found"                      |

---

## 8. GET /api/links/{id}/qr

**Description:** Generates and returns a 300×300 PNG QR code encoding the short URL of an active link owned by the currently authenticated user.

**Auth:** Required (JWT)

### Request
- **Method:** GET
- **URL:** `/api/links/{id}/qr`
- **Headers:**
  - `Authorization: Bearer <jwt_token>`
- **Path Parameters:**
  | Param | Type | Description                  |
  |-------|------|------------------------------|
  | `id`  | long | The numeric ID of the link   |
- **Body:** None

### Validation Rules
| Field | Rule                                |
|-------|-------------------------------------|
| `id`  | Must be a positive long integer     |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → user authenticated, `userId` available in `SecurityContext`
2. Request reaches `LinkController.getQrCode(Authentication, @PathVariable id)`
3. Validate `id` is a positive number; if invalid → return 400
4. Call `LinkService.getActiveLinkForUser(userId, linkId)`:
   1. Look up the link by `id` AND `user_id = userId` AND `deleted_at IS NULL`
      - If not found → throw `LinkNotFoundException` (return 404)
   2. **Status check** — verify the link is currently active:
      - If `is_active = FALSE` (regardless of `disabled_reason` — covers both `ADMIN_DISABLED` and `USER_BANNED`) → throw `LinkNotFoundException` (return 404)
      - If `expires_at IS NOT NULL` AND `expires_at <= NOW()` (expired) → throw `LinkNotFoundException` (return 404)
5. Build the full short URL: `zaplink.base-url + "/" + link.shortCode`
6. Call `QrCodeService.generate(shortUrl)`:
   1. Use **ZXing** (`QRCodeWriter`) to encode the short URL as a `BitMatrix` at 300×300
   2. Use ZXing's `MatrixToImageWriter` to render the `BitMatrix` to a `BufferedImage`
   3. Write the `BufferedImage` to a `ByteArrayOutputStream` as PNG via `ImageIO.write(img, "PNG", stream)`
   4. Return the byte array
7. Wrap the byte array in a `ResponseEntity<byte[]>` with:
   - `Content-Type: image/png`
   - `Content-Disposition: inline; filename="zaplink-{shortCode}.png"`
   - `Cache-Control: public, max-age=86400` (24h — QR for a given short code never changes)
8. Return `200 OK` with the PNG bytes

> 🖼️ **Why PNG?** Lossless, universally supported, browsers render it inline via `<img src="...">`, and ZXing has built-in PNG output via `ImageIO`.

> ⚡ **On caching:** The QR for a given `shortCode` is fully deterministic — same input always produces the same image. The `Cache-Control` header lets browsers and CDNs cache aggressively.

> 🔒 **Why deny QR for expired/disabled links?** A QR is a *durable artifact* — once generated, it can be printed on a poster, embedded in a doc, or shared widely. Generating QRs for non-functional links would be misleading and could damage user trust. Returning 404 mirrors the redirect endpoint's behavior: if the short URL doesn't work, neither does its QR.

> 🔒 **Why require auth?** A QR encodes a public short URL, but auth on this endpoint:
> - Keeps the endpoint consistent with other `/api/links/{id}/*` endpoints (ownership-scoped)
> - Prevents drive-by QR generation against arbitrary link ids (abuse vector + server load)
> - Allows rate limiting to apply per-user

### Database Queries
```sql
-- Fetch link, scoped to owner and not soft-deleted
SELECT id, short_code, user_id, is_active, expires_at, deleted_at
FROM links
WHERE id = ? AND user_id = ? AND deleted_at IS NULL;
```

> 💡 The active/expired check is done in code rather than in the WHERE clause, so we can return a single uniform 404 from the service layer regardless of whether the link is missing, disabled, or expired.

### Success Response
- **Status:** `200 OK`
- **Headers:**
  - `Content-Type: image/png`
  - `Content-Disposition: inline; filename="zaplink-abc123.png"`
  - `Cache-Control: public, max-age=86400`
- **Body:** Binary PNG image data (300×300)

### Error Responses
| Status | Reason                                              | Example Message                       |
|--------|-----------------------------------------------------|---------------------------------------|
| 400    | Invalid id format                                   | "Link id must be a positive number"   |
| 401    | Missing / invalid JWT                               | "Authentication required"             |
| 404    | Link not found, not owned, expired, or disabled     | "Link not found"                      |
| 500    | QR generation failed                                | "Failed to generate QR code"          |

> ℹ️ Error responses return JSON (standard error envelope), not an image. Clients should check `Content-Type` before treating the body as binary.

---

## 9. GET /{shortCode}

**Description:** Public redirect endpoint. When anyone visits `https://zaplink.com/{shortCode}`, this endpoint looks up the original long URL and redirects the browser to it. Logs the click for analytics asynchronously.

**Auth:** Public (no token required — this is the entire point of a short URL)

### Request
- **Method:** GET
- **URL:** `/{shortCode}` (root-level path, NOT under `/api`)
- **Path Parameters:**
  | Param        | Type   | Description                                   |
  |--------------|--------|-----------------------------------------------|
  | `shortCode`  | string | The Base62 short code (e.g., `abc123`)        |
- **Body:** None

### Validation Rules
| Field        | Rule                                                                |
|--------------|---------------------------------------------------------------------|
| `shortCode`  | 1–10 characters, Base62 alphabet only (`0-9`, `A-Z`, `a-z`)         |
| `shortCode`  | Must NOT match a reserved keyword (see internal logic)              |

### Internal Logic (step-by-step)
1. Request reaches `RedirectController.redirect(@PathVariable shortCode, HttpServletRequest)` (no auth filter — this path is whitelisted in Spring Security)
2. **Format check** — verify `shortCode` matches Base62 pattern `^[0-9A-Za-z]{1,10}$`:
   - If invalid → return 404 (don't even hit the DB)
3. **Reserved keyword check** — if `shortCode` matches a value in the reserved set (`api`, `admin`, `login`, etc.) → return 404
   - These are blocked from being generated in the first place (see Endpoint 4), so this check is defense-in-depth in case the reserved list grows after some links were already created
4. Call `RedirectService.resolveAndRedirect(shortCode, requestMetadata)`:
   1. Look up the link by `short_code = shortCode`:
      - If not found → throw `LinkNotFoundException` (return 404 with friendly "Link not available" HTML page)
   2. **Soft-delete check** — if `deleted_at IS NOT NULL` → return 404
   3. **Disabled check** — if `is_active = FALSE` (admin-disabled or owner-banned) → return 410 Gone with HTML page "This link has been disabled"
   4. **Expiration check** — if `expires_at IS NOT NULL` AND `expires_at <= NOW()` → return 410 Gone with HTML page "This link has expired"
   5. **Log the click asynchronously** — publish a `LinkClickedEvent` via `ApplicationEventPublisher`:
      - An `@Async @EventListener` consumer inserts into `clicks (link_id, clicked_at)`
      - The redirect doesn't wait for the insert
      - If the insert fails, the error is logged but the redirect is unaffected
   6. Return the link's `long_url`
5. Build the redirect response:
   - `HttpStatus.FOUND` (302) — temporary redirect, since the long URL is mutable in principle
   - `Location: <long_url>` header
   - `Cache-Control: no-cache, no-store, must-revalidate` — prevents browser/CDN caching so every visit is tracked
6. Return the redirect response

> 🚀 **Why this endpoint must be fast:** This is the only endpoint hit by external traffic — every short URL anyone shares lands here. It needs to resolve in <100ms ideally. Async click logging is the key optimization: the user gets redirected immediately, and the click is recorded in the background.

> 🔄 **Why 302 (Found) instead of 301 (Moved Permanently):**
> - 301 lets browsers cache the redirect *forever*. Subsequent visits would bypass your server → no click tracking.
> - 302 forces the browser to hit your server every time → accurate analytics.
> - The trade-off is more server load, which is acceptable for an analytics-driven product.

> 🚫 **Why 410 Gone for expired/disabled, not 404:**
> - 404 means "this resource never existed" — wrong, since it did
> - 410 means "this resource existed but is gone permanently" — accurate
> - SEO crawlers handle 410 differently (faster removal from indexes)
> - Truly unknown short codes still return 404

> 🛡️ **Reserved keyword check:** Codes like `api`, `admin`, `swagger-ui`, `actuator`, `login`, etc. are blacklisted both at *generation time* (Endpoint 4 skips ids that would produce reserved strings) and at *resolve time* (this endpoint). Belt + suspenders prevents short codes from colliding with current or future system routes.

> ⚡ **Performance note (future enhancement):** A Redis cache keyed by `short_code` would eliminate the DB lookup for hot links. Listed in the Roadmap.

### Database Queries
```sql
-- Lookup (synchronous, blocks the redirect)
SELECT id, long_url, is_active, expires_at, deleted_at
FROM links
WHERE short_code = ?;

-- Click log (asynchronous, fire-and-forget via @Async event listener)
INSERT INTO clicks (link_id, clicked_at) VALUES (?, NOW());
```

> 💡 The lookup query intentionally does NOT filter `deleted_at IS NULL` or expiration in the WHERE clause. We fetch the row, then decide in code whether to redirect, return 404, or return 410 — each case has a different user-facing message.

### Success Response (typical case — link is active)
- **Status:** `302 Found`
- **Headers:**
  - `Location: https://example.com/some/very/long/article-url`
  - `Cache-Control: no-cache, no-store, must-revalidate`
- **Body:** *(empty)*

The browser follows the `Location` header automatically, so the user lands on the original long URL.

### Error Responses
All error responses for this endpoint return **HTML pages** (not JSON), since this endpoint is hit by browsers, not API clients.

| Status | Reason                                              | User sees (HTML page)                  |
|--------|-----------------------------------------------------|----------------------------------------|
| 404    | Invalid format, reserved keyword, not found, or soft-deleted | "Link not available"           |
| 410    | Expired                                             | "This link has expired"                |
| 410    | Disabled by admin or owner banned                   | "This link has been disabled"          |

> 🎨 **UX note:** Render these as templated HTML (Thymeleaf or static pages served by the React frontend). Each page should include a clear message and a link back to the ZapLink home page.

---

# 📊 Analytics

## 10. GET /api/links/{id}/analytics

**Description:** Returns analytics for a single short link owned by the currently authenticated user. Includes total click count (all time), recent click count (last 30 days), and a daily time-series for the last 30 days for charting.

**Auth:** Required (JWT)

### Request
- **Method:** GET
- **URL:** `/api/links/{id}/analytics`
- **Headers:**
  - `Authorization: Bearer <jwt_token>`
- **Path Parameters:**
  | Param | Type | Description                  |
  |-------|------|------------------------------|
  | `id`  | long | The numeric ID of the link   |
- **Body:** None

### Validation Rules
| Field | Rule                                |
|-------|-------------------------------------|
| `id`  | Must be a positive long integer     |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → user authenticated, `userId` available in `SecurityContext`
2. Request reaches `AnalyticsController.getLinkAnalytics(Authentication, @PathVariable id)`
3. Validate `id` is a positive number; if invalid → return 400
4. Call `AnalyticsService.getLinkAnalytics(userId, linkId)`:
   1. **Ownership check** — look up the link by `id` AND `user_id = userId` AND `deleted_at IS NULL`:
      - If not found → throw `LinkNotFoundException` (return 404)
   2. Compute `rangeStart` = `NOW() - INTERVAL 30 DAY` (start of midnight 30 days ago)
   3. **Total click count (all time)** — count all rows in `clicks` where `link_id = ?`
   4. **Recent click count (last 30 days)** — count rows in `clicks` where `link_id = ?` AND `clicked_at >= rangeStart`
   5. **Daily time-series query** — group clicks by day for the last 30 days:
      - Use `DATE(clicked_at)` to truncate to the day
      - Group and count
   6. **Fill zero-days** — the query returns only days with ≥ 1 click. The service generates the full 30-day sequence in code and fills missing days with `count = 0`. The frontend just plots the array.
5. Build an `AnalyticsResponse` DTO with link metadata + analytics fields
6. Return `200 OK`

> 💡 **Why two click counts (total + recent)?** Showing only "last 30 days" hides historical context (a viral link from 6 months ago looks dead today). Showing only "all time" hides recency. Showing both gives the user a useful at-a-glance summary.

> 💡 **On zero-fill:** Without it, the frontend chart would have gaps on no-click days and look odd. The backend filling them in keeps the frontend logic dumb (just plot the array).

> 🔒 **Why 404 for ownership mismatch?** Same reasoning as Endpoint 6 — ownership filtered in the WHERE clause, uniform 404 prevents leaking that another user's link exists.

> ⚡ **Performance note:** A composite index on `clicks(link_id, clicked_at)` makes both the count and the time-series query efficient. Listed in `schema.sql`.

### Database Queries
```sql
-- Step 1: Ownership check
SELECT id, short_code FROM links
WHERE id = ? AND user_id = ? AND deleted_at IS NULL;

-- Step 2: Total click count (all time)
SELECT COUNT(*) FROM clicks WHERE link_id = ?;

-- Step 3: Recent click count (last 30 days)
SELECT COUNT(*) FROM clicks
WHERE link_id = ? AND clicked_at >= ?;

-- Step 4: Daily time-series (last 30 days)
SELECT
    DATE(clicked_at) AS day,
    COUNT(*) AS click_count
FROM clicks
WHERE link_id = ? AND clicked_at >= ?
GROUP BY day
ORDER BY day ASC;
```

> 💡 The time-series query and the recent count can be merged into a single query if performance becomes an issue. Kept separate here for clarity.

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "linkId": 42,
    "shortCode": "abc123",
    "shortUrl": "http://localhost:8080/abc123",
    "totalClicks": 1842,
    "clicksLast30Days": 137,
    "dailyClicks": [
      { "date": "2025-04-04", "clicks": 0 },
      { "date": "2025-04-05", "clicks": 3 },
      { "date": "2025-04-06", "clicks": 12 },
      { "date": "2025-04-07", "clicks": 0 },
      { "date": "2025-04-08", "clicks": 8 },
      { "date": "...", "clicks": "..." },
      { "date": "2025-05-04", "clicks": 5 }
    ]
  }
  ```

The `dailyClicks` array always contains exactly **30 entries**, one per day, ordered oldest → newest.

### Error Responses
| Status | Reason                       | Example Message                                  |
|--------|------------------------------|--------------------------------------------------|
| 400    | Invalid id format            | "Link id must be a positive number"              |
| 401    | Missing / invalid JWT        | "Authentication required"                        |
| 404    | Link not found OR not owned  | "Link not found"                                 |

---

# 🛠️ Admin

All endpoints in this section require `role = ADMIN`. Non-admins receive `403 Forbidden`.

## 11. GET /api/admin/users

**Description:** Returns a paginated list of all registered users in the system, with optional search (by username or email) and status filtering. Includes per-user link and click counts to help admins identify heavy users or potential abusers. Admin-only.

**Auth:** Required (JWT) — must have `role = ADMIN`

### Request
- **Method:** GET
- **URL:** `/api/admin/users`
- **Headers:**
  - `Authorization: Bearer <admin_jwt_token>`
- **Query Parameters:**
  | Param    | Type   | Default | Description                                                  |
  |----------|--------|---------|--------------------------------------------------------------|
  | `page`   | int    | 0       | Page number (zero-indexed)                                   |
  | `size`   | int    | 20      | Number of users per page (max 100)                           |
  | `search` | string | (none)  | Substring match against `username` or `email` (case-insensitive) |
  | `status` | string | `all`   | Filter: `all`, `active`, `banned`                            |
- **Body:** None

### Validation Rules
| Field    | Rule                                          |
|----------|-----------------------------------------------|
| `page`   | Must be ≥ 0                                   |
| `size`   | Must be between 1 and 100                     |
| `search` | Optional; if provided, max 100 characters     |
| `status` | Must be one of: `all`, `active`, `banned`     |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → user authenticated, `role` available in `SecurityContext`
2. **Authorization check** — `@PreAuthorize("hasRole('ADMIN')")`:
   - If user's role ≠ ADMIN → return 403 Forbidden
3. Request reaches `AdminController.listUsers(Pageable, search, status)`
4. Spring resolves the `Pageable` from `page` and `size`
5. Validate page size, `status`, and `search` length; if invalid → return 400
6. Call `AdminService.listUsers(pageable, search, status)`:
   1. Build a JPA query (Specifications or Criteria API) that:
      - Selects all rows from `users`
      - Applies the `status` filter:
        - `active` → `is_active = TRUE`
        - `banned` → `is_active = FALSE`
        - `all` → no filter
      - Applies the `search` filter (if provided):
        - `LOWER(username) LIKE LOWER('%search%')` OR `LOWER(email) LIKE LOWER('%search%')`
        - Search is **case-insensitive** and uses substring match (not exact)
      - Performs `LEFT JOIN` on `links` (excluding soft-deleted) to compute `linkCount` per user
      - Performs `LEFT JOIN` on `clicks` (joined through `links`) to compute `totalClicks` per user
      - Applies pagination
      - Orders by `created_at DESC`
   2. Execute the query → returns `Page<UserWithStats>`
7. Map each row to an `AdminUserResponse` DTO:
   - User fields: `id`, `username`, `email`, `role`, `isActive`, `createdAt`
   - **Excludes** `password_hash` (never returned in any response)
   - Stats: `linkCount`, `totalClicks`
8. Wrap in a paginated envelope and return `200 OK`

> 🔒 **Why 403 (not 404) for non-admins:** Admin endpoints are a known category of routes; returning 403 is standard and signals "authenticated but lacks privilege."

> 🔍 **On search:** `LIKE '%...%'` can't use a B-tree index efficiently, but is fine at portfolio scale. For production scale, consider full-text indexes or a search service.

### Database Queries
```sql
-- Main query (with search + status filters applied)
SELECT
    u.id,
    u.username,
    u.email,
    u.role,
    u.is_active,
    u.created_at,
    COUNT(DISTINCT l.id) AS link_count,
    COUNT(c.id) AS total_clicks
FROM users u
LEFT JOIN links l ON l.user_id = u.id AND l.deleted_at IS NULL
LEFT JOIN clicks c ON c.link_id = l.id
WHERE 1=1
  -- Status filter (one of these, depending on ?status):
  AND u.is_active = TRUE                                 -- when status=active
  AND u.is_active = FALSE                                -- when status=banned
  -- Search filter (when ?search=foo):
  AND (LOWER(u.username) LIKE LOWER(?) OR LOWER(u.email) LIKE LOWER(?))
GROUP BY u.id
ORDER BY u.created_at DESC
LIMIT ? OFFSET ?;

-- Count query (for pagination metadata, with same filters)
SELECT COUNT(*) FROM users u
WHERE 1=1
  AND u.is_active = ?
  AND (LOWER(u.username) LIKE LOWER(?) OR LOWER(u.email) LIKE LOWER(?));
```

> 💡 `COUNT(DISTINCT l.id)` is necessary because the JOIN on `clicks` multiplies link rows. Without DISTINCT, a link with 100 clicks would be counted 100 times.

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "content": [
      {
        "id": 1,
        "username": "johndoe",
        "email": "john@example.com",
        "role": "USER",
        "isActive": true,
        "linkCount": 42,
        "totalClicks": 1842,
        "createdAt": "2025-04-15T10:15:30"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 153,
    "totalPages": 8,
    "first": true,
    "last": false
  }
  ```

### Error Responses
| Status | Reason                       | Example Message                                  |
|--------|------------------------------|--------------------------------------------------|
| 400    | Page size out of range       | "Page size must be between 1 and 100"            |
| 400    | Invalid status value         | "Status must be one of: all, active, banned"     |
| 400    | Search string too long       | "Search must be 100 characters or fewer"         |
| 401    | Missing / invalid JWT        | "Authentication required"                        |
| 403    | Not an admin                 | "Admin access required"                          |

---

## 12. PATCH /api/admin/users/{id}/ban

**Description:** Bans or unbans a user. Banning sets `is_active = FALSE` on the user and cascades to disable all their links (with `disabled_reason = USER_BANNED`). Unbanning restores the user and re-enables only the links that were auto-disabled by the ban (admin-disabled links stay disabled). Admin-only.

**Auth:** Required (JWT) — must have `role = ADMIN`

### Request
- **Method:** PATCH
- **URL:** `/api/admin/users/{id}/ban`
- **Headers:**
  - `Authorization: Bearer <admin_jwt_token>`
  - `Content-Type: application/json`
- **Path Parameters:**
  | Param | Type | Description                  |
  |-------|------|------------------------------|
  | `id`  | long | The numeric ID of the user   |
- **Body:**
  ```json
  {
    "banned": true
  }
  ```
  - `banned: true` → ban the user (set `is_active = FALSE` + cascade-disable links)
  - `banned: false` → unban the user (set `is_active = TRUE` + cascade-re-enable links)

### Validation Rules
| Field    | Rule                                |
|----------|-------------------------------------|
| `id`     | Must be a positive long integer     |
| `banned` | Required, must be a boolean         |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → admin authenticated, `adminUserId` available in `SecurityContext`
2. **Authorization check** — `@PreAuthorize("hasRole('ADMIN')")`
3. Request reaches `AdminController.setUserBanStatus(Authentication, @PathVariable id, BanRequest)`
4. Validate `id` (positive) and request body (`banned` is non-null boolean); if invalid → return 400
5. Call `AdminService.setUserBanStatus(adminUserId, targetUserId, banned)` (wrapped in `@Transactional`):
   1. **Self-ban guard** — if `targetUserId == adminUserId` → throw `IllegalOperationException` (return 400, "You cannot ban yourself")
   2. Look up the target user by `id`:
      - If not found → throw `UserNotFoundException` (return 404)
   3. **Idempotency check** — if `target.is_active == !banned` (already in the requested state):
      - Return the user unchanged (no DB write, return 200)
   4. **Update user state:**
      - If `banned == true` → set `target.is_active = FALSE`
      - If `banned == false` → set `target.is_active = TRUE`
      - Save the user
   5. **Cascade to links:**
      - If banning: update all of the user's currently-active, non-soft-deleted links to `is_active = FALSE`, `disabled_reason = USER_BANNED`. Already-disabled links (any `disabled_reason`) are left alone — we never overwrite an `ADMIN_DISABLED` flag.
      - If unbanning: re-enable only the links that were auto-disabled by the ban — set `is_active = TRUE`, `disabled_reason = NULL` for rows where `user_id = targetUserId` AND `disabled_reason = 'USER_BANNED'`. Links with `disabled_reason = 'ADMIN_DISABLED'` stay disabled.
6. Map the updated user to an `AdminUserResponse` DTO (without `password_hash`)
7. Return `200 OK`

> 🔒 **Self-ban guard:** Without this, an admin could lock themselves out of the system. Even with other admins around, blocking self-ban is a sane safety net.

> 🤝 **Admin-to-admin bans:** Allowed. Admins are trusted operators; ZapLink doesn't enforce a hierarchy among them.

> 🔁 **Cascade disable & re-enable — why the `disabled_reason` column matters:** Without it, unbanning a user would either re-enable *every* disabled link (including ones an admin specifically took down for abuse — bad) or leave *every* link disabled (admin has to redo all their work — bad UX). The `disabled_reason` enum lets us distinguish "auto-disabled because of ban" from "manually disabled by admin," and treat them differently on unban.

> 🔁 **What happens to a banned user's existing JWT?** JWTs are stateless and signed — they don't get invalidated server-side just because the user is banned. On the next request, the `JwtAuthenticationFilter` validates the signature successfully, then the auth flow checks `user.is_active`. If `FALSE` → 403. The current token effectively dies on the next request.

> 💡 **Idempotency:** Banning an already-banned user (or unbanning an already-active user) is a no-op. Always returns 200 with the user's current state. Safe to retry.

### Database Queries
```sql
-- Step 1: Look up the target user
SELECT id, username, email, role, is_active, created_at
FROM users
WHERE id = ?;

-- Step 2a: Update user (ban or unban)
UPDATE users SET is_active = ? WHERE id = ?;

-- Step 2b: Cascade-disable user's links (when banning)
UPDATE links
SET is_active = FALSE,
    disabled_reason = 'USER_BANNED'
WHERE user_id = ?
  AND is_active = TRUE
  AND deleted_at IS NULL;

-- Step 2c: Cascade-re-enable user's links (when unbanning)
UPDATE links
SET is_active = TRUE,
    disabled_reason = NULL
WHERE user_id = ?
  AND disabled_reason = 'USER_BANNED'
  AND deleted_at IS NULL;
```

> 💡 The whole operation runs in a single `@Transactional` method. If the user update succeeds but the link cascade fails (or vice versa), everything rolls back — no partial state.

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "id": 1,
    "username": "johndoe",
    "email": "john@example.com",
    "role": "USER",
    "isActive": false,
    "createdAt": "2025-04-15T10:15:30"
  }
  ```

### Error Responses
| Status | Reason                                  | Example Message                                  |
|--------|-----------------------------------------|--------------------------------------------------|
| 400    | Invalid id format                       | "User id must be a positive number"              |
| 400    | Missing/invalid `banned` field          | "Field 'banned' is required and must be a boolean" |
| 400    | Self-ban attempt                        | "You cannot ban yourself"                        |
| 401    | Missing / invalid JWT                   | "Authentication required"                        |
| 403    | Not an admin                            | "Admin access required"                          |
| 404    | Target user not found                   | "User not found"                                 |

---

## 13. DELETE /api/admin/users/{id}

**Description:** Permanently deletes a user account. The user's links are soft-deleted (their short URLs become 404), but the click history is preserved for system-wide reporting. The user row itself is hard-deleted; their links become "orphaned" with `user_id = NULL`. Admin-only.

**Auth:** Required (JWT) — must have `role = ADMIN`

### Request
- **Method:** DELETE
- **URL:** `/api/admin/users/{id}`
- **Headers:**
  - `Authorization: Bearer <admin_jwt_token>`
- **Path Parameters:**
  | Param | Type | Description                  |
  |-------|------|------------------------------|
  | `id`  | long | The numeric ID of the user   |
- **Body:** None

### Validation Rules
| Field | Rule                                |
|-------|-------------------------------------|
| `id`  | Must be a positive long integer     |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → admin authenticated, `adminUserId` available in `SecurityContext`
2. **Authorization check** — `@PreAuthorize("hasRole('ADMIN')")`:
   - If user's role ≠ ADMIN → return 403
3. Request reaches `AdminController.deleteUser(Authentication, @PathVariable id)`
4. Validate `id` is a positive number; if invalid → return 400
5. Call `AdminService.deleteUser(adminUserId, targetUserId)` (wrapped in `@Transactional`):
   1. **Self-delete guard** — if `targetUserId == adminUserId` → throw `IllegalOperationException` (return 400, "You cannot delete your own account")
   2. Look up the target user by `id`:
      - If not found → throw `UserNotFoundException` (return 404)
   3. **Soft-delete the user's links** — for every link where `user_id = targetUserId` AND `deleted_at IS NULL`, set `deleted_at = NOW()`. Click rows stay attached.
   4. **Hard-delete the user row** — `DELETE FROM users WHERE id = targetUserId`. The `links.user_id` FK is configured `ON DELETE SET NULL`, so MySQL automatically sets `user_id = NULL` on all of the (now soft-deleted) links.
6. Return `204 No Content`

> 🔒 **Self-delete guard:** Without this, an admin could delete themselves and lose access to admin functions, possibly locking the system out of administration entirely.

> 🤝 **Admin-to-admin deletion:** Allowed. Consistent with Endpoint 12.

> 🗑️ **Why soft-delete links instead of hard-delete?** Click history is valuable for system-wide reports (Endpoint 16) — total clicks across the platform, abuse trends, etc. Hard-deleting links would cascade into the `clicks` table (`ON DELETE CASCADE`), wiping that history. Soft-delete keeps everything for analytics while making the URLs go 404.

> 💀 **Orphaned links:** After this operation, the deleted user's links exist with `user_id = NULL`. They're soft-deleted (so the redirect endpoint returns 404 anyway), but they show up in admin list views (Endpoint 14) labeled as "[deleted user]". The click history they accumulated remains intact and is included in system-wide reports.

> ⚠️ **Why hard-delete the user (not soft-delete)?** Because the user account itself has very little reason to be retained — there are no analytics tied to *the user account*, only to their links. Their email becomes available for re-registration. PII is wiped from the system.

> 🔁 **What happens to a deleted user's existing JWT?** The JWT is still cryptographically valid (until it expires), but the next request will fail: the auth flow looks up the user by `sub` claim, finds no row, and returns 401.

### Database Queries
```sql
-- Step 1: Look up the target user (verify existence)
SELECT id FROM users WHERE id = ?;

-- Step 2: Soft-delete all the user's active links
UPDATE links
SET deleted_at = NOW()
WHERE user_id = ? AND deleted_at IS NULL;

-- Step 3: Hard-delete the user row.
-- The links.user_id FK has ON DELETE SET NULL,
-- so MySQL automatically sets user_id = NULL on all of the user's links.
DELETE FROM users WHERE id = ?;
```

> 💡 The whole operation is wrapped in `@Transactional`. If any step fails, everything rolls back — no partial state where the user is gone but links are still attached, or vice versa.

### Success Response
- **Status:** `204 No Content`
- **Body:** *(empty)*

### Error Responses
| Status | Reason                       | Example Message                                  |
|--------|------------------------------|--------------------------------------------------|
| 400    | Invalid id format            | "User id must be a positive number"              |
| 400    | Self-delete attempt          | "You cannot delete your own account"             |
| 401    | Missing / invalid JWT        | "Authentication required"                        |
| 403    | Not an admin                 | "Admin access required"                          |
| 404    | Target user not found        | "User not found"                                 |

---

## 14. GET /api/admin/links

**Description:** Returns a paginated list of all links in the system, with optional search and status filtering. Includes the owning user's username (or `null` for orphaned links) and click count for each link. Admin-only.

**Auth:** Required (JWT) — must have `role = ADMIN`

### Request
- **Method:** GET
- **URL:** `/api/admin/links`
- **Headers:**
  - `Authorization: Bearer <admin_jwt_token>`
- **Query Parameters:**
  | Param    | Type   | Default | Description                                                  |
  |----------|--------|---------|--------------------------------------------------------------|
  | `page`   | int    | 0       | Page number (zero-indexed)                                   |
  | `size`   | int    | 20      | Number of links per page (max 100)                           |
  | `search` | string | (none)  | Substring match against `short_code`, `long_url`, or owner's `username`/`email` (case-insensitive) |
  | `status` | string | `all`   | Filter: `all`, `active`, `expired`, `disabled`, `deleted`    |
- **Body:** None

### Validation Rules
| Field    | Rule                                                                                           |
|----------|------------------------------------------------------------------------------------------------|
| `page`   | Must be ≥ 0                                                                                    |
| `size`   | Must be between 1 and 100                                                                      |
| `search` | Optional; max 200 characters                                                                   |
| `status` | Must be one of: `all`, `active`, `expired`, `disabled`, `deleted`                              |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → admin authenticated
2. **Authorization check** — `@PreAuthorize("hasRole('ADMIN')")`
3. Request reaches `AdminController.listLinks(Pageable, search, status)`
4. Spring resolves the `Pageable` from `page` and `size`
5. Validate page size, `status`, and `search` length; if invalid → return 400
6. Call `AdminService.listLinks(pageable, search, status)`:
   1. Build a JPA query (Specifications or Criteria API) that:
      - Selects all rows from `links` (including soft-deleted, since admins should see those too — distinguished by the `status` filter)
      - `LEFT JOIN` on `users` (because `links.user_id` is nullable — orphaned links have no owner)
      - `LEFT JOIN` on `clicks` to compute `clickCount`
      - Applies the `status` filter:
        - `active` → `is_active = TRUE` AND `deleted_at IS NULL` AND (`expires_at IS NULL` OR `expires_at > NOW()`)
        - `expired` → `is_active = TRUE` AND `deleted_at IS NULL` AND `expires_at <= NOW()`
        - `disabled` → `is_active = FALSE` AND `deleted_at IS NULL`
        - `deleted` → `deleted_at IS NOT NULL`
        - `all` → no filter
      - Applies the `search` filter (if provided):
        - `LOWER(l.short_code) LIKE LOWER('%search%')` OR `LOWER(l.long_url) LIKE LOWER('%search%')` OR `LOWER(u.username) LIKE LOWER('%search%')` OR `LOWER(u.email) LIKE LOWER('%search%')`
      - Applies pagination
      - Orders by `created_at DESC`
   2. Execute the query → returns `Page<AdminLinkRow>`
7. For each row, compute the `status` field at response time:
   - `"active"`, `"expired"`, `"disabled"`, or `"deleted"` (using the same logic as the filter)
8. Map each row to an `AdminLinkResponse` DTO:
   - All link fields including `disabled_reason`
   - `owner` object (or `null` if `user_id IS NULL`):
     - `id`, `username`, `email` (admins can see emails for moderation)
   - `clickCount` from the aggregation
   - `shortUrl` (built from `zaplink.base-url + "/" + shortCode`)
   - `status` (computed)
9. Wrap in a paginated envelope and return `200 OK`

> 🧑‍🦱 **Orphaned links:** Links whose owner was deleted (Endpoint 13) have `user_id = NULL`. The `LEFT JOIN` on `users` returns no row for the owner, and we render `owner: null` in the response. The frontend should display these as "[deleted user]".

> 👁️ **Why admins see all four statuses (including deleted):** Soft-deleted links retain click history and may be subject to abuse reports or audits. Admin visibility is the whole point of soft delete.

### Database Queries
```sql
-- Main query (with all filters applied as needed)
SELECT
    l.id,
    l.short_code,
    l.long_url,
    l.user_id,
    l.expires_at,
    l.is_active,
    l.disabled_reason,
    l.deleted_at,
    l.created_at,
    u.id          AS owner_id,
    u.username    AS owner_username,
    u.email       AS owner_email,
    COUNT(c.id)   AS click_count
FROM links l
LEFT JOIN users  u ON u.id = l.user_id
LEFT JOIN clicks c ON c.link_id = l.id
WHERE 1=1
  -- Status filter (one of the following based on ?status):
  AND l.is_active = TRUE AND l.deleted_at IS NULL AND (l.expires_at IS NULL OR l.expires_at > NOW())  -- active
  AND l.is_active = TRUE AND l.deleted_at IS NULL AND l.expires_at <= NOW()                            -- expired
  AND l.is_active = FALSE AND l.deleted_at IS NULL                                                     -- disabled
  AND l.deleted_at IS NOT NULL                                                                         -- deleted
  -- Search filter (when ?search is provided):
  AND (
        LOWER(l.short_code) LIKE LOWER(?)
     OR LOWER(l.long_url)   LIKE LOWER(?)
     OR LOWER(u.username)   LIKE LOWER(?)
     OR LOWER(u.email)      LIKE LOWER(?)
  )
GROUP BY l.id
ORDER BY l.created_at DESC
LIMIT ? OFFSET ?;

-- Count query (for pagination metadata, with the same filters applied)
SELECT COUNT(DISTINCT l.id) FROM links l
LEFT JOIN users u ON u.id = l.user_id
WHERE /* same filters */;
```

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "content": [
      {
        "id": 42,
        "shortCode": "abc123",
        "shortUrl": "http://localhost:8080/abc123",
        "longUrl": "https://example.com/some/long/url",
        "expiresAt": "2025-12-31T23:59:59",
        "isActive": true,
        "disabledReason": null,
        "deletedAt": null,
        "status": "active",
        "clickCount": 137,
        "owner": {
          "id": 1,
          "username": "johndoe",
          "email": "john@example.com"
        },
        "createdAt": "2025-05-04T10:15:30"
      },
      {
        "id": 88,
        "shortCode": "xY9kQ",
        "shortUrl": "http://localhost:8080/xY9kQ",
        "longUrl": "https://malicious-example.com/scam",
        "expiresAt": null,
        "isActive": false,
        "disabledReason": "ADMIN_DISABLED",
        "deletedAt": null,
        "status": "disabled",
        "clickCount": 2418,
        "owner": null,
        "createdAt": "2025-04-22T03:11:50"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1247,
    "totalPages": 63,
    "first": true,
    "last": false
  }
  ```

The second example shows an **orphaned, admin-disabled link** — owner was deleted (`owner: null`) and an admin disabled the link (`disabledReason: ADMIN_DISABLED`).

### Error Responses
| Status | Reason                       | Example Message                                  |
|--------|------------------------------|--------------------------------------------------|
| 400    | Page size out of range       | "Page size must be between 1 and 100"            |
| 400    | Invalid status value         | "Status must be one of: all, active, expired, disabled, deleted" |
| 400    | Search string too long       | "Search must be 200 characters or fewer"         |
| 401    | Missing / invalid JWT        | "Authentication required"                        |
| 403    | Not an admin                 | "Admin access required"                          |

---

## 15. PATCH /api/admin/links/{id}/disable

**Description:** Disables or re-enables a link. Disabling sets `is_active = FALSE` and `disabled_reason = ADMIN_DISABLED`, causing the redirect endpoint to return 410 Gone. Re-enabling restores the link to active status. Admin-only.

**Auth:** Required (JWT) — must have `role = ADMIN`

### Request
- **Method:** PATCH
- **URL:** `/api/admin/links/{id}/disable`
- **Headers:**
  - `Authorization: Bearer <admin_jwt_token>`
  - `Content-Type: application/json`
- **Path Parameters:**
  | Param | Type | Description                  |
  |-------|------|------------------------------|
  | `id`  | long | The numeric ID of the link   |
- **Body:**
  ```json
  {
    "disabled": true
  }
  ```
  - `disabled: true` → disable the link (set `is_active = FALSE`, `disabled_reason = ADMIN_DISABLED`)
  - `disabled: false` → re-enable the link (set `is_active = TRUE`, `disabled_reason = NULL`)

### Validation Rules
| Field      | Rule                                |
|------------|-------------------------------------|
| `id`       | Must be a positive long integer     |
| `disabled` | Required, must be a boolean         |

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → admin authenticated
2. **Authorization check** — `@PreAuthorize("hasRole('ADMIN')")`
3. Request reaches `AdminController.setLinkDisabledStatus(@PathVariable id, DisableLinkRequest)`
4. Validate `id` (positive) and request body (`disabled` is non-null boolean); if invalid → return 400
5. Call `AdminService.setLinkDisabledStatus(linkId, disabled)` (wrapped in `@Transactional`):
   1. Look up the link by `id`:
      - If not found → throw `LinkNotFoundException` (return 404)
   2. **Soft-delete check** — if `link.deleted_at IS NOT NULL` → throw `IllegalOperationException` (return 400, "Cannot modify a deleted link")
   3. **Owner-banned guard (when re-enabling):** If `disabled == false` AND the link's owner has `is_active = FALSE` (i.e., owner is banned) → throw `IllegalOperationException` (return 400, "Cannot re-enable a link whose owner is banned. Unban the user first.")
   4. **Idempotency / transition rules:**
      - If `disabled == true` (admin wants to disable):
        - If `link.is_active == FALSE` AND `link.disabled_reason == 'ADMIN_DISABLED'` → no-op (return 200 with current state)
        - If `link.is_active == FALSE` AND `link.disabled_reason == 'USER_BANNED'` → throw `IllegalOperationException` (return 400, "This link is already disabled because its owner is banned. Use the user-ban endpoint to manage its state.")
        - Otherwise → set `is_active = FALSE`, `disabled_reason = ADMIN_DISABLED`, save
      - If `disabled == false` (admin wants to re-enable):
        - If `link.is_active == TRUE` → no-op (return 200 with current state)
        - If `link.disabled_reason == 'USER_BANNED'` → throw `IllegalOperationException` (return 400, "This link was disabled because its owner was banned. Unban the user to re-enable.")
        - Otherwise (i.e., `disabled_reason == 'ADMIN_DISABLED'`) → set `is_active = TRUE`, `disabled_reason = NULL`, save
6. Map the updated link to an `AdminLinkResponse` DTO (same shape as Endpoint 14, including `owner`, `clickCount`, `status`)
7. Return `200 OK`

> 🔒 **Why the `disabled_reason` distinction matters here too:** An admin disabling a link they want to take down for abuse is a different action from a link auto-disabled because its owner was banned. We never want admin actions and ban cascades to overwrite each other.

> 🗑️ **Cannot modify deleted links:** Soft-deleted links are user-deleted; the admin shouldn't be re-enabling or disabling them. Their state is essentially frozen — they appear in admin reports but can't be acted on.

> 💡 **Idempotency:** Disabling an already-`ADMIN_DISABLED` link, or re-enabling an already-active link, is a no-op. The endpoint always returns the link's current state. Safe to retry.

> 🔁 **Effect on redirect endpoint:** Once disabled, the next visit to `/{shortCode}` returns 410 Gone (Endpoint 9). Existing browser tabs that already have the link open are unaffected — the disable only kicks in on the next request.

### Database Queries
```sql
-- Step 1: Look up the link (need its current state and the owner's is_active)
SELECT
    l.id,
    l.short_code,
    l.user_id,
    l.is_active,
    l.disabled_reason,
    l.deleted_at,
    u.is_active AS owner_is_active
FROM links l
LEFT JOIN users u ON u.id = l.user_id
WHERE l.id = ?;

-- Step 2: Update (only if a state change is actually happening)
UPDATE links
SET is_active = ?,
    disabled_reason = ?
WHERE id = ?;
-- Disable:    is_active=FALSE, disabled_reason='ADMIN_DISABLED'
-- Re-enable:  is_active=TRUE,  disabled_reason=NULL
```

### Success Response
- **Status:** `200 OK`
- **Body:** Same shape as the items in Endpoint 14's list response, e.g.:
  ```json
  {
    "id": 42,
    "shortCode": "abc123",
    "shortUrl": "http://localhost:8080/abc123",
    "longUrl": "https://example.com/some/long/url",
    "expiresAt": null,
    "isActive": false,
    "disabledReason": "ADMIN_DISABLED",
    "deletedAt": null,
    "status": "disabled",
    "clickCount": 137,
    "owner": {
      "id": 1,
      "username": "johndoe",
      "email": "john@example.com"
    },
    "createdAt": "2025-05-04T10:15:30"
  }
  ```

### Error Responses
| Status | Reason                                                                | Example Message                                                                  |
|--------|-----------------------------------------------------------------------|----------------------------------------------------------------------------------|
| 400    | Invalid id format                                                     | "Link id must be a positive number"                                              |
| 400    | Missing/invalid `disabled` field                                      | "Field 'disabled' is required and must be a boolean"                             |
| 400    | Trying to disable a link that's already `USER_BANNED`-disabled        | "This link is already disabled because its owner is banned. Use the user-ban endpoint to manage its state." |
| 400    | Trying to re-enable a link whose owner is banned                      | "Cannot re-enable a link whose owner is banned. Unban the user first."           |
| 400    | Trying to re-enable a `USER_BANNED`-disabled link directly            | "This link was disabled because its owner was banned. Unban the user to re-enable." |
| 400    | Trying to act on a soft-deleted link                                  | "Cannot modify a deleted link"                                                   |
| 401    | Missing / invalid JWT                                                 | "Authentication required"                                                        |
| 403    | Not an admin                                                          | "Admin access required"                                                          |
| 404    | Link not found                                                        | "Link not found"                                                                 |

---

## 16. GET /api/admin/reports

**Description:** Returns aggregate, system-wide statistics for ZapLink: user counts, link counts, and click totals. Used by the admin dashboard for at-a-glance health metrics. Admin-only.

**Auth:** Required (JWT) — must have `role = ADMIN`

### Request
- **Method:** GET
- **URL:** `/api/admin/reports`
- **Headers:**
  - `Authorization: Bearer <admin_jwt_token>`
- **Query Parameters:** None
- **Body:** None

### Validation Rules
None — no inputs.

### Internal Logic (step-by-step)
1. Request hits `JwtAuthenticationFilter` → admin authenticated
2. **Authorization check** — `@PreAuthorize("hasRole('ADMIN')")`:
   - If user's role ≠ ADMIN → return 403 Forbidden
3. Request reaches `AdminController.getSystemReports()`
4. Call `AdminService.getSystemReports()`:
   1. Compute `rangeStart` = `NOW() - INTERVAL 30 DAY`
   2. **User stats** — single query with conditional aggregation:
      - `totalUsers` — count of all rows in `users`
      - `activeUsers` — `is_active = TRUE`
      - `bannedUsers` — `is_active = FALSE`
      - `newUsersLast30Days` — `created_at >= rangeStart`
   3. **Link stats** — single query with conditional aggregation:
      - `totalLinks` — count of all non-soft-deleted links
      - `activeLinks` — `is_active = TRUE` AND `deleted_at IS NULL` AND (`expires_at IS NULL` OR `expires_at > NOW()`)
      - `expiredLinks` — `is_active = TRUE` AND `deleted_at IS NULL` AND `expires_at <= NOW()`
      - `disabledLinks` — `is_active = FALSE` AND `deleted_at IS NULL`
      - `deletedLinks` — `deleted_at IS NOT NULL`
      - `orphanedLinks` — `user_id IS NULL`
      - `newLinksLast30Days` — `created_at >= rangeStart`
   4. **Click stats** — single query:
      - `totalClicks` — count of all rows in `clicks`
      - `clicksLast30Days` — `clicked_at >= rangeStart`
5. Build a `SystemReportsResponse` DTO combining the three sections
6. Return `200 OK`

> 💡 **Why pre-defined sections instead of flexible query params?** A reports endpoint serves an admin dashboard — the dashboard knows exactly what it wants to show. Letting clients pick arbitrary metrics adds complexity (input validation, dynamic SQL) for little benefit.

> 🧩 **Why conditional aggregation in single queries?** `SELECT COUNT(*) AS x, SUM(CASE WHEN ... THEN 1 ELSE 0 END) AS y, ...` is one pass over the table instead of N. At scale this matters; at portfolio scale, it's just cleaner code.

> 🔗 **Note on metric overlap — `orphanedLinks` ⊆ `deletedLinks`:** Every orphaned link (i.e., `user_id IS NULL`) is also soft-deleted by design. This is because Endpoint 13 always soft-deletes a user's links *before* hard-deleting the user account (which is what triggers `ON DELETE SET NULL` on `user_id`). So `orphanedLinks` is always a **subset** of `deletedLinks`, not a separate category. Do not add the two together — the `orphanedLinks` count is provided to tell admins "of the X deleted links, Y are deleted because their owner was deleted (rather than because the link was deleted directly)."

> 📊 **Other potential overlaps:** `totalLinks` excludes deleted links (`deleted_at IS NULL`), so `totalLinks + deletedLinks` = all link rows ever created. `activeLinks + expiredLinks + disabledLinks = totalLinks` (these three are mutually exclusive partitions of non-deleted links).

> ⚡ **Performance note:** Aggregating across the full `clicks` table on every call is fine at portfolio scale but becomes a bottleneck first. The natural progression is: (1) cache the response in memory for ~60 seconds (admin reports don't need real-time accuracy), then (2) pre-compute daily rollups via a scheduled job. Listed in the Roadmap.

### Database Queries
```sql
-- 1. User stats
SELECT
    COUNT(*)                                              AS total_users,
    SUM(CASE WHEN is_active = TRUE  THEN 1 ELSE 0 END)    AS active_users,
    SUM(CASE WHEN is_active = FALSE THEN 1 ELSE 0 END)    AS banned_users,
    SUM(CASE WHEN created_at >= ?   THEN 1 ELSE 0 END)    AS new_users_last_30
FROM users;

-- 2. Link stats
SELECT
    SUM(CASE WHEN deleted_at IS NULL THEN 1 ELSE 0 END)                                                                  AS total_links,
    SUM(CASE WHEN is_active = TRUE  AND deleted_at IS NULL AND (expires_at IS NULL OR expires_at > NOW()) THEN 1 ELSE 0 END) AS active_links,
    SUM(CASE WHEN is_active = TRUE  AND deleted_at IS NULL AND expires_at <= NOW() THEN 1 ELSE 0 END)                    AS expired_links,
    SUM(CASE WHEN is_active = FALSE AND deleted_at IS NULL THEN 1 ELSE 0 END)                                            AS disabled_links,
    SUM(CASE WHEN deleted_at IS NOT NULL THEN 1 ELSE 0 END)                                                              AS deleted_links,
    SUM(CASE WHEN user_id IS NULL THEN 1 ELSE 0 END)                                                                     AS orphaned_links,
    SUM(CASE WHEN created_at >= ? AND deleted_at IS NULL THEN 1 ELSE 0 END)                                              AS new_links_last_30
FROM links;

-- 3. Click stats
SELECT
    COUNT(*)                                            AS total_clicks,
    SUM(CASE WHEN clicked_at >= ? THEN 1 ELSE 0 END)    AS clicks_last_30
FROM clicks;
```

### Success Response
- **Status:** `200 OK`
- **Body:**
  ```json
  {
    "userStats": {
      "totalUsers": 153,
      "activeUsers": 148,
      "bannedUsers": 5,
      "newUsersLast30Days": 24
    },
    "linkStats": {
      "totalLinks": 1247,
      "activeLinks": 1090,
      "expiredLinks": 87,
      "disabledLinks": 12,
      "deletedLinks": 58,
      "orphanedLinks": 6,
      "newLinksLast30Days": 312
    },
    "clickStats": {
      "totalClicks": 184302,
      "clicksLast30Days": 28471
    }
  }
  ```

### Error Responses
| Status | Reason                       | Example Message                  |
|--------|------------------------------|----------------------------------|
| 401    | Missing / invalid JWT        | "Authentication required"        |
| 403    | Not an admin                 | "Admin access required"          |
