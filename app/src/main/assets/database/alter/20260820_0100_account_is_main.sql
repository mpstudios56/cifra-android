-- Which accounts are the ones used every day, so the picker can offer them first
alter table account add column is_main integer not null default 0;
