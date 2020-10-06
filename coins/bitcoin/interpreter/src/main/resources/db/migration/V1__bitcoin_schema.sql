CREATE TYPE operation_type as ENUM(
    'send',
    'received'
);

CREATE TABLE block (
    hash VARCHAR NOT NULL PRIMARY KEY,
    height INTEGER NOT NULL,
    time VARCHAR NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE transaction (
    account_id UUID NOT NULL,
    id VARCHAR NOT NULL,
    hash VARCHAR NOT NULL,
    block_hash VARCHAR NOT NULL,
    received_at VARCHAR,
    lock_time VARCHAR,
    fees BIGINT,
    confirmations INTEGER,

    PRIMARY KEY (account_id, hash),
    FOREIGN KEY (block_hash) REFERENCES block (hash)
);

CREATE TABLE input (
    account_id UUID NOT NULL,
    hash VARCHAR NOT NULL,
    output_hash VARCHAR NOT NULL,
    output_index INTEGER NOT NULL,
    input_index INTEGER NOT NULL,
    value BIGINT NOT NULL,
    address VARCHAR NOT NULL,
    script_signature VARCHAR,
    txinwitness VARCHAR[],
    sequence BIGINT NOT NULL,

    PRIMARY KEY (account_id, hash, output_hash, output_index),
    FOREIGN KEY (account_id, hash) REFERENCES transaction (account_id, hash)
);

CREATE TABLE output (
    account_id UUID NOT NULL,
    hash VARCHAR NOT NULL,
    output_index INTEGER NOT NULL,
    value BIGINT NOT NULL,
    address VARCHAR NOT NULL,
    script_hex VARCHAR,

    PRIMARY KEY (account_id, hash, output_index),
    FOREIGN KEY (account_id, hash) REFERENCES transaction (account_id, hash)
);

CREATE TABLE operation (
    account_id UUID NOT NULL,
    hash VARCHAR NOT NULL,
    operation_type operation_type NOT NULL,
    amount BIGINT NOT NULL,
    time VARCHAR NOT NULL,

    PRIMARY KEY (account_id, hash, operation_type),
    FOREIGN KEY (account_id, hash) REFERENCES transaction (account_id, hash)
);
