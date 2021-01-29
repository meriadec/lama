CREATE TABLE unconfirmed_transaction_view (
    account_id UUID NOT NULL,
    transaction_views JSON NOT NULL,

    PRIMARY KEY (account_id)
);

CREATE TABLE unconfirmed_transaction_cache (
    account_id UUID NOT NULL,
    transactions JSON NOT NULL
);

-- make block nullable in operation to accept unconfirmed operations
ALTER TABLE operation ALTER COLUMN block_hash DROP NOT NULL;
ALTER TABLE operation ALTER COLUMN block_height DROP NOT NULL;
ALTER TABLE operation DROP CONSTRAINT operation_account_id_hash_fkey;
