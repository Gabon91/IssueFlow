-- Phase 3.8: user soft-delete. Adds a nullable deleted_at marker so the
-- @SQLRestriction("deleted_at IS NULL") on the User entity hides soft-deleted users from
-- default reads while preserving FK integrity for historical Ticket/Comment ownership.

alter table users add column deleted_at timestamp(6) with time zone;

create index ix_users_deleted_at on users (deleted_at);
