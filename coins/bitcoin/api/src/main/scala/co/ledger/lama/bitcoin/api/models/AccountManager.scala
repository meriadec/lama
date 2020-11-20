package co.ledger.lama.bitcoin.api.models

import java.util.UUID

import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._
import co.ledger.lama.bitcoin.common.models.service.{Operation, Utxo}
import co.ledger.lama.common.models.SyncEvent
import sttp.tapir.{Schema, Validator}

case class GetOperationsResult(
    truncated: Boolean,
    operations: Seq[Operation],
    size: Int
)

object GetOperationsResult {
  implicit val sGetOperationsResult: Schema[GetOperationsResult] =
    Schema.derive
  implicit val getOperationsResultValidator: Validator[GetOperationsResult] = Validator.derive

  implicit val getOperationsResultDecoder: Decoder[GetOperationsResult] =
    deriveConfiguredDecoder[GetOperationsResult]

  implicit val getOperationsResultEncoder: Encoder[GetOperationsResult] =
    deriveConfiguredEncoder[GetOperationsResult]
}

case class GetUTXOsResult(
    truncated: Boolean,
    utxos: Seq[Utxo],
    size: Int
)

object GetUTXOsResult {
  implicit val sGetUTXOsResult: Schema[GetUTXOsResult] =
    Schema.derive
  implicit val getUTXOsResultValidator: Validator[GetUTXOsResult] = Validator.derive

  implicit val getUTXOsResultDecoder: Decoder[GetUTXOsResult] =
    deriveConfiguredDecoder[GetUTXOsResult]

  implicit val getUTXOsResultEncoder: Encoder[GetUTXOsResult] =
    deriveConfiguredEncoder[GetUTXOsResult]
}

case class AccountInfo(
    accountId: UUID,
    syncFrequency: Long,
    lastSyncEvent: Option[SyncEvent],
    balance: BigInt,
    utxos: Int,
    received: BigInt,
    sent: BigInt
)

object AccountInfo {
  implicit val sAccountInfo: Schema[AccountInfo]           = Schema.derive
  implicit val validateAccountInfo: Validator[AccountInfo] = Validator.derive

  implicit val getAccountInfoDecoder: Decoder[AccountInfo] =
    deriveConfiguredDecoder[AccountInfo]

  implicit val getAccountInfoEncoder: Encoder[AccountInfo] =
    deriveConfiguredEncoder[AccountInfo]
}

case class AccountRegistered(accountId: UUID, syncId: UUID, syncFrequency: Long)

object AccountRegistered {
  implicit val sAccountRegistered: Schema[AccountRegistered]            = Schema.derive
  implicit val accountRegisteredValidator: Validator[AccountRegistered] = Validator.derive

  implicit val accountRegisteredDecoder: Decoder[AccountRegistered] =
    deriveConfiguredDecoder[AccountRegistered]
  implicit val accountRegisteredEncoder: Encoder[AccountRegistered] =
    deriveConfiguredEncoder[AccountRegistered]
}
