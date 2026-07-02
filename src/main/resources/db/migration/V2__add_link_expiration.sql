ALTER TABLE short_urls
    ADD COLUMN expires_at TIMESTAMP NULL,
    ADD COLUMN max_clicks BIGINT NULL;