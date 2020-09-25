CREATE TYPE operation_type as ENUM(
    'send',
    'received'
);

CREATE TABLE block (
    hash BYTEA NOT NULL PRIMARY KEY,
    height INTEGER NOT NULL,
    time TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE transaction (
    hash BYTEA NOT NULL,
    account_id VARCHAR NOT NULL,
    block_hash BYTEA NOT NULL,
    fee BIGINT,

    PRIMARY KEY (account_id, hash),
    FOREIGN KEY (block_hash) REFERENCES block (hash)
);

CREATE TABLE input (
    tx_hash BYTEA NOT NULL,
    account_id VARCHAR NOT NULL,
    output_tx_hash VARCHAR NOT NULL,
    output_index INTEGER NOT NULL,
    address VARCHAR NOT NULL,
    amount BIGINT NOT NULL,
    sequence VARCHAR NOT NULL,

    PRIMARY KEY (account_id, tx_hash, output_tx_hash, output_index),
    FOREIGN KEY (account_id, tx_hash) REFERENCES transaction (account_id, hash)
);

CREATE TABLE output (
    tx_hash BYTEA NOT NULL,
    account_id VARCHAR NOT NULL,
    index INTEGER NOT NULL,
    address VARCHAR NOT NULL,
    amount BIGINT NOT NULL,

    PRIMARY KEY (account_id, tx_hash, index),
    FOREIGN KEY (account_id, tx_hash) REFERENCES transaction (account_id, hash)
);

CREATE TABLE coinbase_input (
    tx_hash BYTEA NOT NULL,
    account_id VARCHAR NOT NULL,
    address VARCHAR NOT NULL,
    reward BIGINT NOT NULL,
    fee BIGINT NOT NULL,

    PRIMARY KEY (account_id, tx_hash),
    FOREIGN KEY (account_id, tx_hash) REFERENCES transaction (account_id, hash)
);

CREATE TABLE operation (
    tx_hash BYTEA NOT NULL ,
    account_id VARCHAR NOT NULL,
    operation_type operation_type NOT NULL,
    amount BIGINT NOT NULL,
    time TIMESTAMP NOT NULL,

    PRIMARY KEY (account_id, tx_hash, operation_type),
    FOREIGN KEY (account_id, tx_hash) REFERENCES transaction (account_id, hash)
);

-- CREATE UNIQUE INDEX operations_index ON account_sync_event(operation_uuid);
