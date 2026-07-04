ALTER TABLE users DROP COLUMN password_hash;
ALTER TABLE users DROP COLUMN email_verified;
ALTER TABLE users DROP COLUMN verification_token;
ALTER TABLE users DROP COLUMN verification_token_expires_at;

ALTER TABLE users ADD COLUMN provider VARCHAR(32);
