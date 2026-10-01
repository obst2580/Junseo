-- Reactions can be tapped many times: one row per (moment, user, emoji) with a tap count.
alter table reactions add column taps integer not null default 1;
alter table reactions add constraint reactions_taps_ck check (taps between 1 and 99);
alter table reactions drop constraint reactions_moment_user_uk;
alter table reactions add constraint reactions_moment_user_emoji_uk unique (moment_id, user_id, emoji);
create index reactions_moment_user_idx on reactions (moment_id, user_id);
