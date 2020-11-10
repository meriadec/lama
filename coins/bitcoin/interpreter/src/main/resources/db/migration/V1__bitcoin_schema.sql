CREATE TYPE operation_type as ENUM(
    'sent',
    'received'
);

CREATE TYPE change_type as ENUM(
    'internal',
    'external'
);

CREATE TABLE transaction (
    account_id UUID NOT NULL,
    id VARCHAR NOT NULL,
    hash VARCHAR NOT NULL,
    block_hash VARCHAR NOT NULL,
    block_height BIGINT NOT NULL,
    block_time TIMESTAMP NOT NULL,
    received_at TIMESTAMP,
    lock_time BIGINT,
    fees NUMERIC(30, 0),
    confirmations INTEGER,

    PRIMARY KEY (account_id, hash)
);

CREATE TABLE input (
    account_id UUID NOT NULL,
    hash VARCHAR NOT NULL,
    output_hash VARCHAR NOT NULL,
    output_index INTEGER NOT NULL,
    input_index INTEGER NOT NULL,
    value NUMERIC(30, 0) NOT NULL,
    address VARCHAR NOT NULL,
    script_signature VARCHAR,
    txinwitness VARCHAR[],
    sequence BIGINT NOT NULL,
    belongs BOOLEAN,

    PRIMARY KEY (account_id, hash, output_hash, output_index),
    FOREIGN KEY (account_id, hash) REFERENCES transaction (account_id, hash) ON DELETE CASCADE
);

CREATE INDEX on input(address);

CREATE TABLE output (
    account_id UUID NOT NULL,
    hash VARCHAR NOT NULL,
    output_index INTEGER NOT NULL,
    value NUMERIC(30, 0) NOT NULL,
    address VARCHAR NOT NULL,
    script_hex VARCHAR,
    belongs BOOLEAN NOT NULL,
    change_type CHANGE_TYPE,

    PRIMARY KEY (account_id, hash, output_index),
    FOREIGN KEY (account_id, hash) REFERENCES transaction (account_id, hash) ON DELETE CASCADE
);

CREATE INDEX on output(address);

CREATE TABLE operation (
    account_id UUID NOT NULL,
    hash VARCHAR NOT NULL,
    operation_type operation_type NOT NULL,
    value NUMERIC(30, 0) NOT NULL,
    fees  NUMERIC(30, 0) NOT NULL,
    time TIMESTAMP NOT NULL,
    block_hash VARCHAR NOT NULL,
    block_height BIGINT NOT NULL,

    PRIMARY KEY (account_id, hash, operation_type),
    FOREIGN KEY (account_id, hash) REFERENCES transaction (account_id, hash) ON DELETE CASCADE
);

CREATE TABLE balance_history (
    id SERIAL PRIMARY KEY,
    account_id UUID NOT NULL,
    balance NUMERIC(30, 0) NOT NULL,
    utxos INTEGER NOT NULL,
    received NUMERIC(30, 0) NOT NULL,
    sent NUMERIC(30, 0) NOT NULL,
    block_height BIGINT NOT NULL,
    time TIMESTAMP NOT NULL DEFAUlT CURRENT_TIMESTAMP
);

CREATE INDEX balance_history_account_id_index ON balance_history(account_id);
CREATE INDEX balance_history_time_index ON balance_history(time);

-- View for SpentAmount et ReceivedAmount computation
CREATE VIEW transaction_amount AS
WITH inputs AS (
    SELECT account_id, hash, SUM(value) AS input_amount
    FROM input
    WHERE belongs
    GROUP BY account_id, hash
),

outputs AS (
    SELECT account_id,
        hash,
        SUM(CASE WHEN change_type = 'external' THEN value ELSE 0 END) AS output_amount,
        SUM(CASE WHEN change_type = 'internal' THEN value ELSE 0 END) AS change_amount
    FROM output
    WHERE belongs
    GROUP BY account_id, hash
)

SELECT t.account_id,
    t.hash,
    t.block_hash,
    t.block_height,
    t.block_time,
    t.fees,
    i.input_amount,
    o.output_amount,
    o.change_amount
FROM transaction as t
    LEFT JOIN inputs  AS i ON t.account_id = i.account_id AND t.hash = i.hash
    LEFT JOIN outputs AS o ON t.account_id = o.account_id AND t.hash = o.hash;

-- in case we decide to compute spent and received amounts in the view :
--    (CASE WHEN i.input_amount = 0
--        THEN 0
--        ELSE i.input_amount - o.change_amount
--        END
--    ) as sent_amount,
--    (CASE WHEN i.input_amount = 0
--        THEN o.output_amount + o.change_amount
--        ELSE o.output_amount
--        END
--    ) as received_amount
