-- Which way a sharing goes. Nought is what this phone gives to somebody;
-- one is what somebody gave to this phone. Everything written before this
-- column existed was given by this phone, which is what the default says.
alter table shared_with add column incoming integer not null default 0;
