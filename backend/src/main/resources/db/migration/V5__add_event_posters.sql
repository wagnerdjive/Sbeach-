-- poster_version is the upload time in epoch milliseconds; it doubles as a cache-busting token.
ALTER TABLE events ADD COLUMN poster_version BIGINT;

-- Poster bytes live in their own table so event listings never load them.
CREATE TABLE event_posters (
    event_id BIGINT PRIMARY KEY REFERENCES events (id),
    content_type VARCHAR(30) NOT NULL,
    data BYTEA NOT NULL
);
