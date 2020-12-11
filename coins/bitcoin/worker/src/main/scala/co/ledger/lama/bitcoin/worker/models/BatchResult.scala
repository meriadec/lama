package co.ledger.lama.bitcoin.worker.models

import co.ledger.lama.bitcoin.common.models.explorer.ConfirmedTransaction
import co.ledger.lama.bitcoin.common.models.interpreter.AccountAddress

case class BatchResult(
    addresses: List[AccountAddress],
    transactions: List[ConfirmedTransaction],
    continue: Boolean
)
