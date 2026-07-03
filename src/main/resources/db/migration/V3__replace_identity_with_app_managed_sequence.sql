ALTER TABLE short_urls
    ALTER COLUMN id DROP IDENTITY IF EXISTS;

CREATE SEQUENCE short_urls_id_seq OWNED BY short_urls.id;

SELECT setval('short_urls_id_seq', COALESCE((SELECT MAX(id) FROM short_urls), 0) + 1, false);

ALTER TABLE short_urls
    ALTER COLUMN id SET DEFAULT nextval('short_urls_id_seq');
