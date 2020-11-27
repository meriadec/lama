package co.ledger.lama.bitcoin.api.models

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.transactor.{CoinSelectionStrategy, PrepareTxOutput}
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._

object transactor {

  case class CreateTransactionRequest(
      accountId: UUID,
      coinSelection: CoinSelectionStrategy,
      outputs: List[PrepareTxOutput]
  )

  object CreateTransactionRequest {
    implicit val encoder: Encoder[CreateTransactionRequest] =
      deriveConfiguredEncoder[CreateTransactionRequest]
    implicit val decoder: Decoder[CreateTransactionRequest] =
      deriveConfiguredDecoder[CreateTransactionRequest]
  }

}
