DROP VIEW transaction_amount;

ALTER TABLE input ADD COLUMN derivation INTEGER[];
ALTER TABLE input DROP COLUMN belongs;

ALTER TABLE output ADD COLUMN derivation INTEGER[];
ALTER TABLE output DROP COLUMN belongs;

-- View for SpentAmount et ReceivedAmount computation
CREATE VIEW transaction_amount AS
WITH inputs AS (
    SELECT account_id, hash, SUM(value) AS input_amount
    FROM input
    WHERE derivation IS NOT NULL
    GROUP BY account_id, hash
),

outputs AS (
    SELECT account_id,
        hash,
        SUM(CASE WHEN change_type = 'external' THEN value ELSE 0 END) AS output_amount,
        SUM(CASE WHEN change_type = 'internal' THEN value ELSE 0 END) AS change_amount
    FROM output
    WHERE derivation IS NOT NULL
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