CREATE TYPE operation_type as ENUM(
    'sent',
    'received'
);

CREATE TYPE change_type as ENUM(
    'internal',
    'external'
);

CREATE TABLE block (
    hash VARCHAR NOT NULL PRIMARY KEY,
    height BIGINT NOT NULL,
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
    belongs BOOLEAN,

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
    belongs BOOLEAN NOT NULL,
    change_type CHANGE_TYPE,

    PRIMARY KEY (account_id, hash, output_index),
    FOREIGN KEY (account_id, hash) REFERENCES transaction (account_id, hash)
);

CREATE TABLE operation (
    account_id UUID NOT NULL,
    hash VARCHAR NOT NULL,
    operation_type operation_type NOT NULL,
    value BIGINT NOT NULL,
    time VARCHAR NOT NULL,

    PRIMARY KEY (account_id, hash, operation_type),
    FOREIGN KEY (account_id, hash) REFERENCES transaction (account_id, hash)
);
