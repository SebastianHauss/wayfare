ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT false;

ALTER TABLE users ADD COLUMN verification_token VARCHAR(64) UNIQUE;
ALTER TABLE users ADD COLUMN verification_token_expires_at TIMESTAMP;
