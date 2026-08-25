-- Headings written into the list of accounts, to gather them in groups:
-- "Casa", "Lavoro", "Risparmi". They hold no money and take part in no total.
--
-- Which accounts belong to a heading is written on the accounts themselves, so
-- a group can gather accounts that are nowhere near each other in the list -
-- the current account, a card and a wallet, with three others in between that
-- belong elsewhere. An account belongs to one heading or to none.
--
-- The heading has no place of its own in the order. It is drawn just above the
-- first of its accounts, wherever that falls, and the rest are brought up under
-- it. So it follows its accounts however the list is sorted, and never has to
-- be dragged anywhere.
--
-- Whether the group is folded away is kept with the heading, not among the
-- settings. Careful with semicolons in these comments - the file is split on
-- them, and one inside a comment cuts a statement in half.
drop table if exists account_separator;

create table account_separator (
    _id integer primary key autoincrement,
    title text not null,
    is_folded integer not null default 0
);

alter table account add column separator_id integer not null default 0;
