-- Al-Muezzin — Supabase schema (free tier, no Firebase).
--
--  devices       : one row per installed app (no secrets in here — just metadata).
--  notifications : the send log + what the app pulls to catch up while offline.
--
-- Delivery model:
--   1. The dashboard (admin, Supabase Auth) INSERTs a row into `notifications`.
--   2. Supabase Realtime broadcasts that INSERT to every connected app.
--   3. Apps that were offline fetch missed rows over the REST API.

-- ---------------------------------------------------------------- tables
create table if not exists public.devices (
  id            text primary key,
  platform      text not null default 'android',
  app_version   text,
  model         text,
  app_id        text,
  last_seen_at  timestamptz not null default now(),
  created_at    timestamptz not null default now(),
  constraint devices_id_shape check (char_length(id) between 4 and 128)
);

create table if not exists public.notifications (
  id          bigint generated always as identity primary key,
  type        text not null default 'text' check (type in ('text', 'audio')),
  title       text not null check (char_length(title) between 1 and 120),
  body        text not null default '',
  audio_url   text,
  target      text,
  sent_by     text,
  recipients  integer not null default 0,
  sent_at     timestamptz not null default now()
);

create index if not exists notifications_sent_at_idx on public.notifications (sent_at desc);
create index if not exists devices_last_seen_idx on public.devices (last_seen_at desc);

-- ---------------------------------------------------------------- realtime
alter publication supabase_realtime add table public.notifications;

-- ---------------------------------------------------------------- grants
-- Explicitly expose the tables to the Data (REST) API roles. RLS below then
-- decides which rows each role may touch.
grant usage on schema public to anon, authenticated, service_role;
grant select, insert, update on public.devices to anon, authenticated, service_role;
grant select on public.notifications to anon, service_role;
grant select, insert on public.notifications to authenticated, service_role;
grant usage, select on all sequences in schema public to anon, authenticated, service_role;

-- ---------------------------------------------------------------- RLS
alter table public.devices       enable row level security;
alter table public.notifications enable row level security;

-- Devices hold no secrets (ids + metadata only), so both roles may read them.
-- UPDATE must be paired with SELECT or the upsert silently updates 0 rows.
drop policy if exists devices_read on public.devices;
create policy devices_read on public.devices
  for select to anon, authenticated using (true);

drop policy if exists devices_insert_any on public.devices;
create policy devices_insert_any on public.devices
  for insert to anon, authenticated with check (true);

drop policy if exists devices_update_own on public.devices;
create policy devices_update_own on public.devices
  for update to anon, authenticated using (true) with check (true);

-- The dashboard (signed in) writes the send log.
drop policy if exists notifications_admin_insert on public.notifications;
create policy notifications_admin_insert on public.notifications
  for insert to authenticated with check (true);

drop policy if exists notifications_admin_select on public.notifications;
create policy notifications_admin_select on public.notifications
  for select to authenticated using (true);

-- Apps read the notification history (to catch up while offline). The content
-- is public prayer reminders, so this is intentionally permissive.
drop policy if exists notifications_anon_select on public.notifications;
create policy notifications_anon_select on public.notifications
  for select to anon using (true);