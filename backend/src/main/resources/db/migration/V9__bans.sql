-- People an operator removed (upheld report). Logging in again must not hand them a fresh account:
-- platform accounts are matched by the central identity, local ones by email.
create table banned_identities (
    id               bigserial primary key,
    external_issuer  varchar(200),
    external_subject varchar(200),
    email            varchar(254),
    display_name     varchar(40),
    reason           varchar(200),
    created_at       timestamptz not null,
    constraint banned_identities_target_ck check (
        (external_issuer is null) = (external_subject is null) and (external_subject is not null or email is not null))
);
create unique index banned_platform_uk on banned_identities (external_issuer, external_subject) where external_subject is not null;
create unique index banned_email_uk on banned_identities (email) where external_subject is null;
