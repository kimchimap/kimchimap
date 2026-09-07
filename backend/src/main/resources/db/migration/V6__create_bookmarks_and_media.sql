CREATE TABLE app.bookmark (
    member_id uuid NOT NULL REFERENCES app.member,
    restaurant_id uuid NOT NULL REFERENCES app.restaurant,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, restaurant_id)
);
CREATE INDEX bookmark_member_page ON app.bookmark(member_id, created_at DESC, restaurant_id);

CREATE TABLE app.media (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app.member,
    storage_key uuid NOT NULL UNIQUE,
    content_type varchar(20) NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    original_size bigint NOT NULL CHECK (original_size BETWEEN 1 AND 10485760),
    sanitized_size bigint NOT NULL CHECK (sanitized_size BETWEEN 1 AND 20971520),
    width integer NOT NULL CHECK (width BETWEEN 1 AND 8192),
    height integer NOT NULL CHECK (height BETWEEN 1 AND 8192),
    content_hash varchar(64) NOT NULL,
    state varchar(20) NOT NULL DEFAULT 'TEMPORARY' CHECK (state IN ('TEMPORARY', 'ATTACHED', 'DELETING')),
    publicly_visible boolean NOT NULL DEFAULT false,
    privacy_reviewed_by uuid REFERENCES app.member,
    privacy_reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (width::bigint * height <= 20000000),
    CHECK (NOT publicly_visible OR (state = 'ATTACHED' AND privacy_reviewed_by IS NOT NULL AND privacy_reviewed_at IS NOT NULL))
);
CREATE INDEX media_owner_created ON app.media(owner_id, created_at DESC);
CREATE INDEX media_cleanup ON app.media(created_at) WHERE state = 'TEMPORARY';
