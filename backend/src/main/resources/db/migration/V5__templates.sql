-- Photo templates the server hands out (the app also ships a few built-in ones), so new ones go live without an app release.
-- slots: JSON array in the app's shape — square {x, y, size, radius?} or quad {quad, aspect, grow?, glow?}.
-- requires: what the app must be able to draw (quad, glow, overlay, ...); older apps skip templates they can't draw.
-- version: bumped on every upload so the image URLs change and phones fetch the new pictures.
create table templates (
    id               varchar(40)  primary key,
    name             varchar(30)  not null,
    width            int          not null,
    height           int          not null,
    background_color varchar(9)   not null,
    background_file  varchar(20)  not null,
    has_overlay      boolean      not null,
    slots            text         not null,
    requires         varchar(200) not null,
    sort_order       int          not null default 0,
    active           boolean      not null default true,
    version          int          not null default 1,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null
);
create index templates_list_idx on templates (active, sort_order desc, created_at desc);
