-- Phone verification is deferred; store a nullable timestamp for a future OTP hook.
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS phone_verified_at TIMESTAMPTZ;

-- Single-use password reset tokens: only SHA-256 hashes are stored (never the raw secret).
CREATE TABLE password_reset_tokens (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_hash ON password_reset_tokens (token_hash);
