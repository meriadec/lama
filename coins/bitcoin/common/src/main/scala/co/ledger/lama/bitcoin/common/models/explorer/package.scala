package co.ledger.lama.bitcoin.common.models

import co.ledger.lama.bitcoin.common.models.worker.Transaction
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}
import co.ledger.lama.common.models.implicits._

package object explorer {

  case class GetTransactionsResponse(truncated: Boolean, txs: Seq[Transaction])

  object GetTransactionsResponse {
    implicit val encoder: Encoder[GetTransactionsResponse] =
      deriveConfiguredEncoder[GetTransactionsResponse]
    implicit val decoder: Decoder[GetTransactionsResponse] =
      deriveConfiguredDecoder[GetTransactionsResponse]
  }

}
