-- ZapLink schema
-- Re-runnable: all statements use IF NOT EXISTS / IF EXISTS guards.
-- Run order matters: users → links → clicks (FK dependencies).
-- Character set: utf8mb4 for full Unicode + emoji support.

-- ─────────────────────────────────────────────────────────────────────────────
-- Table: users
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT                  NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)             NOT NULL,
    email         VARCHAR(100)            NOT NULL,
    password_hash VARCHAR(255)            NOT NULL,
    role          ENUM('USER', 'ADMIN')   NOT NULL DEFAULT 'USER',
    is_active     BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP               NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email    (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- Table: links
-- ─────────────────────────────────────────────────────────────────────────────
-- short_code is UNIQUE but nullable: the insert-then-encode pattern in
-- LinkService briefly stores NULL before updating with the Base62 code.
-- Multiple NULLs are allowed in a MySQL UNIQUE column, so this is safe.
--
-- user_id uses ON DELETE SET NULL so that deleting a user orphans their links
-- (user_id → NULL) rather than cascade-deleting the links and their click
-- history, which is needed for system-wide admin reporting.
CREATE TABLE IF NOT EXISTS links (
    id              BIGINT                              NOT NULL AUTO_INCREMENT,
    short_code      VARCHAR(10)                         NULL,
    long_url        TEXT                                NOT NULL,
    user_id         BIGINT                              NULL,
    expires_at      TIMESTAMP                           NULL,
    is_active       BOOLEAN                             NOT NULL DEFAULT TRUE,
    disabled_reason ENUM('USER_BANNED', 'ADMIN_DISABLED') NULL,
    deleted_at      TIMESTAMP                           NULL,
    created_at      TIMESTAMP                           NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_links_short_code (short_code),

    CONSTRAINT fk_links_user_id
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- Table: clicks
-- ─────────────────────────────────────────────────────────────────────────────
-- ON DELETE CASCADE: when a link row is hard-deleted (only by a future cleanup
-- job — normal user flow uses soft-delete), its click rows go with it.
CREATE TABLE IF NOT EXISTS clicks (
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    link_id    BIGINT    NOT NULL,
    clicked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_clicks_link_id
        FOREIGN KEY (link_id) REFERENCES links (id)
        ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- Indexes
-- ─────────────────────────────────────────────────────────────────────────────

-- clicks(link_id, clicked_at)
-- Used by the analytics time-series query (Endpoint 10) and the async click
-- INSERT triggered by the redirect (Endpoint 9). link_id leads so range scans
-- on clicked_at within a single link are covered by this index.
CREATE INDEX IF NOT EXISTS idx_clicks_link_id_clicked_at
    ON clicks (link_id, clicked_at);

-- links(user_id, deleted_at)
-- Used by GET /api/links (Endpoint 5): filters user_id = ? AND deleted_at IS NULL,
-- then sorts by created_at. Prefix (user_id) narrows the scan; deleted_at
-- is included so the soft-delete filter is resolved in the index.
CREATE INDEX IF NOT EXISTS idx_links_user_id_deleted_at
    ON links (user_id, deleted_at);

-- links(short_code)
-- The redirect hot path (Endpoint 9) looks up rows by short_code on every hit.
-- This index is already implied by the UNIQUE KEY uq_links_short_code above,
-- so no separate CREATE INDEX is needed — it is listed here for documentation
-- purposes only, to match the "Recommended indexes" section in API_DOCS.md.
-- (Duplicate: uq_links_short_code already serves as this index.)
