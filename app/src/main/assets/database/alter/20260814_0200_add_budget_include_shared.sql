-- A budget can leave out what the other person wrote.
-- On by default, which is what every budget did before this column existed.
alter table budget add column include_shared integer not null default 1;
