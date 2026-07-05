-- Revert OAuth-only auth back to email + password + email verification.
-- OAuth accounts have no password, so they can't be carried over; wipe the
-- user data (and their owned links / refresh tokens) for a clean slate before
-- re-adding the password-auth columns. Anonymous links (user_id IS NULL) stay.
DELETE FROM refresh_tokens;
DELETE FROM short_urls WHERE user_id IS NOT NULL;
DELETE FROM users;

ALTER TABLE users DROP COLUMN provider;

ALTER TABLE users ADD COLUMN password_hash VARCHAR(60) NOT NULL;
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN verification_token VARCHAR(64) UNIQUE;
ALTER TABLE users ADD COLUMN verification_token_expires_at TIMESTAMP;
