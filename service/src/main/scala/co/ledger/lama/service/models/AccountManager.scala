package co.ledger.lama.service.models

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.Operation

case class GetAccountManagerInfoResult(
    accountId: UUID,
    keychainId: String,
    syncFrequency: Long,
    status: Option[String]
)

case class GetOperationsResult(
    truncated: Boolean,
    operations: Seq[Operation]
)

case class AccountRegistered(accountId: UUID, syncId: UUID, syncFrequency: Long)
