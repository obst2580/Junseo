alter table device_tokens add column platform varchar(10) not null default 'ios';
alter table device_tokens add constraint device_tokens_platform_ck check (platform in ('ios', 'android'));
alter table device_tokens add constraint device_tokens_android_kind_ck check (platform <> 'android' or kind = 'app');
-- FCM tokens are case-sensitive and contain characters beyond APNs hex.
alter table device_tokens alter column token type varchar(2048);
