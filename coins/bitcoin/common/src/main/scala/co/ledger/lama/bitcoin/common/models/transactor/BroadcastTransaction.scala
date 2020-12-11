package co.ledger.lama.bitcoin.common.models.transactor

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.bitcoin.transactor.protobuf
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class BroadcastTransaction(
    hex: String,
    hash: String,
    witnessHash: String
) {
  def toProto: protobuf.BroadcastTransactionResponse =
    protobuf.BroadcastTransactionResponse(
      hex,
      hash,
      witnessHash
    )
}

object BroadcastTransaction {
  implicit val encoder: Encoder[BroadcastTransaction] =
    deriveConfiguredEncoder[BroadcastTransaction]
  implicit val decoder: Decoder[BroadcastTransaction] =
    deriveConfiguredDecoder[BroadcastTransaction]

  def fromProto(proto: protobuf.BroadcastTransactionResponse): BroadcastTransaction =
    BroadcastTransaction(
      proto.hex,
      proto.hash,
      proto.witnessHash
    )
}
