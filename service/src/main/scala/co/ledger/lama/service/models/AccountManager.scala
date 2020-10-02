package co.ledger.lama.service.models

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.Transaction

case class GetAccountManagerInfoResult(
    accountId: UUID,
    keychainId: String,
    syncFrequency: Long,
    status: Option[String]
)

case class GetTransactionsResult(
    truncated: Boolean,
    transaction: Seq[Transaction]
)

case class AccountRegistered(accountId: UUID, syncId: UUID, syncFrequency: Long)
