package co.ledger.lama.bitcoin.common.models.explorer

import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class GetTransactionsResponse(truncated: Boolean, txs: Seq[Transaction])

object GetTransactionsResponse {
  implicit val encoder: Encoder[GetTransactionsResponse] =
    deriveConfiguredEncoder[GetTransactionsResponse]
  implicit val decoder: Decoder[GetTransactionsResponse] =
    deriveConfiguredDecoder[GetTransactionsResponse]
}
