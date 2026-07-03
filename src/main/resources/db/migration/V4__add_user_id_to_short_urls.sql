ALTER TABLE short_urls
    ADD COLUMN user_id BIGINT NULL REFERENCES users (id);
