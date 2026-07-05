CREATE TABLE click_events
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    short_url_id    BIGINT      NOT NULL REFERENCES short_urls (id) ON DELETE CASCADE,
    clicked_at      timestamp   NOT NULL DEFAULT now(),
    referrer_domain varchar(255),
    country         varchar(2),
    device_type     varchar(16),
    browser         varchar(32)
);

CREATE INDEX idx_click_events_short_url_id ON click_events (short_url_id);
CREATE INDEX idx_click_events_clicked_at ON click_events (clicked_at);
