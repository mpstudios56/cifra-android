-- Where a heading is drawn, when somebody has said.
--
-- By itself a heading appears where the first of its accounts falls. That is
-- the sensible default, but it is not always what is wanted: a group may want
-- to stand at the top of the list whatever order the accounts are in. This
-- holds the account it is to be drawn above, or zero for "wherever the first
-- of mine is".
alter table account_separator add column before_account_id integer not null default 0;

-- A view left behind by the app this one grew out of. It joined the accounts to
-- a table of separators that no code has read for years, and it names columns
-- that the table no longer has, so it would fail to be built at all. Nothing
-- asks for it.
drop view if exists v_account_with_separator;
