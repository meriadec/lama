package co.ledger.lama.service.models

import java.util.UUID

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import co.ledger.lama.bitcoin.common.models.service.{Operation, OutputView}
import co.ledger.lama.common.models.SyncEvent

case class GetOperationsResult(
    truncated: Boolean,
    operations: Seq[Operation],
    size: Int
)

object GetOperationsResult {
  implicit val getOperationsResultDecoder: Decoder[GetOperationsResult] =
    deriveDecoder[GetOperationsResult]
}

case class GetUTXOsResult(
    truncated: Boolean,
    utxos: Seq[OutputView],
    size: Int
)

object GetUTXOsResult {
  implicit val getUTXOsResultDecoder: Decoder[GetUTXOsResult] =
    deriveDecoder[GetUTXOsResult]
}

case class AccountInfo(
    accountId: UUID,
    syncFrequency: Long,
    syncEvent: Option[SyncEvent],
    balance: BigInt,
    utxoCount: Int,
    amountSpent: BigInt,
    amountReceived: BigInt
)

object AccountInfo {
  implicit val getAccountInfoDecoder: Decoder[AccountInfo] =
    deriveDecoder[AccountInfo]
}

case class AccountRegistered(accountId: UUID, syncId: UUID, syncFrequency: Long)

object AccountRegistered {
  implicit val accountRegisteredDecoder: Decoder[AccountRegistered] =
    deriveDecoder[AccountRegistered]

}
