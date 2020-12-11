package co.ledger.lama.bitcoin.common.models.transactor

import co.ledger.lama.bitcoin.common.models.interpreter.Utxo

case class RawTransactionAndUtxos(
    hex: String,
    hash: String,
    witnessHash: String,
    utxos: List[Utxo]
)
