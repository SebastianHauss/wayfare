CREATE TABLE short_urls
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    short_code   varchar(16) unique,
    original_url varchar(2048) not null,
    created_at   timestamp     not null,
    click_count  BIGINT        NOT NULL DEFAULT 0
)