package co.ledger.lama.bitcoin.common.models.explorer

import co.ledger.lama.common.models.implicits._
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder

case class SendTransactionResult(result: String)

object SendTransactionResult {
  implicit val decoder: Decoder[SendTransactionResult] =
    deriveConfiguredDecoder[SendTransactionResult]
}
