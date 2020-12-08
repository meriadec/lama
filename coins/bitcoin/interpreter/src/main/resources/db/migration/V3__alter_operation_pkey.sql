ALTER TABLE operation DROP CONSTRAINT operation_pkey;

ALTER TABLE operation
ADD PRIMARY KEY (account_id, time, hash, operation_type);
