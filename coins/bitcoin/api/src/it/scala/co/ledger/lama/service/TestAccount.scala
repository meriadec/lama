package co.ledger.lama.service

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.service.routes.AccountController.CreationRequest
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._

case class TestAccount(
    registerRequest: CreationRequest,
    expected: AccountExpectedResult
)

object TestAccount {
  implicit val decoder: Decoder[TestAccount] = deriveConfiguredDecoder[TestAccount]
  implicit val encoder: Encoder[TestAccount] = deriveConfiguredEncoder[TestAccount]
}

case class AccountExpectedResult(
    opsSize: Int,
    utxosSize: Int,
    lastTxHash: String,
    balance: Long,
    amountReceived: Long,
    amountSent: Long
)

object AccountExpectedResult {
  implicit val decoder: Decoder[AccountExpectedResult] =
    deriveConfiguredDecoder[AccountExpectedResult]
  implicit val encoder: Encoder[AccountExpectedResult] =
    deriveConfiguredEncoder[AccountExpectedResult]

}
