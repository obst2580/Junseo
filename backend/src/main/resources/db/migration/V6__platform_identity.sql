alter table users add column external_issuer varchar(200);
alter table users add column external_subject varchar(200);
alter table users alter column password_hash drop not null;
alter table users drop constraint users_email_uk;
create unique index users_local_email_uk on users(email) where external_subject is null;
create unique index users_platform_identity_uk on users(external_issuer, external_subject)
    where external_subject is not null;
alter table users add constraint users_identity_pair_ck
    check ((external_issuer is null) = (external_subject is null));

create table revoked_tokens (
    jti varchar(200) primary key,
    expires_at timestamptz not null
);
create index revoked_tokens_expiry_idx on revoked_tokens(expires_at);
ALTER TABLE users ADD COLUMN onboarded BOOLEAN NOT NULL DEFAULT TRUE;
