CREATE TABLE app.member (
    id uuid PRIMARY KEY,
    provider varchar(30) NOT NULL CHECK (provider = 'KAKAO'),
    provider_subject varchar(200),
    role varchar(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    security_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_subject),
    CHECK (provider_subject IS NOT NULL OR status = 'WITHDRAWN')
);
CREATE TABLE app.member_security_history (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES app.member,
    action varchar(40) NOT NULL,
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    actor_reference varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE FUNCTION app.reject_member_security_history_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '회원 보안 변경 이력은 변경할 수 없습니다';
END;
$$;
CREATE TRIGGER member_security_history_immutable BEFORE UPDATE OR DELETE ON app.member_security_history
    FOR EACH ROW EXECUTE FUNCTION app.reject_member_security_history_mutation();
