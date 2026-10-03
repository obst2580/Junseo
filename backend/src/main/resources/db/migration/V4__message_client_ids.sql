-- The app tags every send with its own id. If the response is lost and the app retries, the server
-- hands back the first copy instead of saving the message twice.
alter table messages add column client_id varchar(64);
create unique index messages_client_idx on messages (sender_id, client_id) where client_id is not null;

alter table group_messages add column client_id varchar(64);
create unique index group_messages_client_idx on group_messages (sender_id, client_id) where client_id is not null;
