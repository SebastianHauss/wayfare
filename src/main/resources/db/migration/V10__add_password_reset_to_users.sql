ALTER TABLE users ADD COLUMN password_reset_token VARCHAR(64) UNIQUE;
ALTER TABLE users ADD COLUMN password_reset_token_expires_at TIMESTAMP;
