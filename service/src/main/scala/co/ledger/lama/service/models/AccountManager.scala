package co.ledger.lama.service.models

import java.util.UUID

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import co.ledger.lama.bitcoin.common.models.Service.Operation

case class GetAccountManagerInfoResult(
    accountId: UUID,
    keychainId: String,
    syncFrequency: Long,
    status: Option[String]
)

object GetAccountManagerInfoResult {
  implicit val getAccountManagerInfoResultDecoder: Decoder[GetAccountManagerInfoResult] =
    deriveDecoder[GetAccountManagerInfoResult]
}

case class GetOperationsResult(
    truncated: Boolean,
    operations: Seq[Operation],
    size: Int
)

object GetOperationsResult {
  implicit val getOperationsResultDecoder: Decoder[GetOperationsResult] =
    deriveDecoder[GetOperationsResult]
}

case class AccountRegistered(accountId: UUID, syncId: UUID, syncFrequency: Long)

object AccountRegistered {
  implicit val accountRegisteredDecoder: Decoder[AccountRegistered] =
    deriveDecoder[AccountRegistered]

}
