-- Junseo's own sessions. LiliPlanet login only proves who someone is (its token lives about 12 hours and cannot be
-- refreshed); staying signed in on the app, the widget and the notification extension is a Junseo session, renewed
-- while the app is used and ended by logout, account deletion or a password change.
create table sessions (
    id               uuid        primary key,
    user_id          bigint      not null references users (id) on delete cascade,
    kind             varchar(10) not null,
    authenticated_at timestamptz not null,
    created_at       timestamptz not null,
    renewed_at       timestamptz not null,
    revoked_at       timestamptz,
    -- The LiliPlanet token this session was made from: on logout it is refused for new logins until it expires
    central_jti        varchar(200),
    central_expires_at timestamptz,
    constraint sessions_kind_ck check (kind in ('app', 'reauth'))
);
create index sessions_user_idx on sessions (user_id);
