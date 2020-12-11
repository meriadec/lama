package co.ledger.lama.bitcoin.common.models.transactor

import cats.data.NonEmptyList
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.bitcoin.common.models.interpreter.Utxo
import co.ledger.lama.bitcoin.transactor.protobuf
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class RawTransaction(
    hex: String,
    hash: String,
    witnessHash: String,
    utxos: NonEmptyList[Utxo]
) {
  def toProto: protobuf.CreateTransactionResponse =
    protobuf.CreateTransactionResponse(
      hex,
      hash,
      witnessHash,
      utxos.map(_.toProto).toList
    )
}

object RawTransaction {
  implicit val encoder: Encoder[RawTransaction] =
    deriveConfiguredEncoder[RawTransaction]
  implicit val decoder: Decoder[RawTransaction] =
    deriveConfiguredDecoder[RawTransaction]

  def fromProto(proto: protobuf.CreateTransactionResponse): RawTransaction =
    RawTransaction(
      proto.hex,
      proto.hash,
      proto.witnessHash,
      NonEmptyList.fromListUnsafe(proto.utxos.map(Utxo.fromProto).toList)
    )
}
