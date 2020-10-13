package co.ledger.lama.service

import co.ledger.lama.service.routes.AccountController.CreationRequest
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class TestAccount(
    registerRequest: CreationRequest,
    expected: AccountExpectedResult
)

object TestAccount {
  implicit val decoder: Decoder[TestAccount] = deriveDecoder[TestAccount]
  implicit val encoder: Encoder[TestAccount] = deriveEncoder[TestAccount]
}

case class AccountExpectedResult(opsSize: Int, utxosSize: Int, lastTxHash: String)

object AccountExpectedResult {
  implicit val decoder: Decoder[AccountExpectedResult] = deriveDecoder[AccountExpectedResult]
  implicit val encoder: Encoder[AccountExpectedResult] = deriveEncoder[AccountExpectedResult]

}
