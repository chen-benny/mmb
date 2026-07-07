ALTER TABLE posts ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('english',
            coalesce(title, '') || ' ' ||
            coalesce(description, '') || ' ' ||
            coalesce(body_md, ''))
    ) STORED;

CREATE INDEX idx_posts_search ON posts USING GIN(search_vector);
