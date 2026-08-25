-- Headings written into the list of accounts, to gather them in groups:
-- "Casa", "Lavoro", "Risparmi". They hold no money and take part in no total.
--
-- A heading is not given a place of its own in the order. It is fastened to an
-- account and appears just above it, and the accounts under it are simply the
-- ones that follow, as far as the next heading. That way it stays where it
-- belongs however the list is sorted - by hand, by name, by the last movement -
-- and it never has to be dragged anywhere.
--
-- Whether the group is folded away is kept here, with the heading it belongs
-- to, rather than among the settings.
-- Thrown away first if it is there. It can only be there on a phone that ran
-- an unreleased build of today, where it was made with a column that has since
-- been thought better of. No released version has ever had this table, so there
-- is nothing to lose by starting it afresh.
drop table if exists account_separator;

create table account_separator (
    _id integer primary key autoincrement,
    title text not null,
    before_account_id integer not null,
    is_folded integer not null default 0
);
