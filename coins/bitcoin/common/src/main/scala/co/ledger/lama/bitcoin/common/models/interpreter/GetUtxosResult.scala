package co.ledger.lama.bitcoin.common.models.interpreter

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class GetUtxosResult(
    utxos: List[Utxo],
    total: Int,
    truncated: Boolean
) {
  def toProto: protobuf.GetUTXOsResult =
    protobuf.GetUTXOsResult(
      utxos.map(_.toProto),
      total,
      truncated
    )
}

object GetUtxosResult {
  implicit val getUTXOsResultDecoder: Decoder[GetUtxosResult] =
    deriveConfiguredDecoder[GetUtxosResult]
  implicit val encoder: Encoder[GetUtxosResult] =
    deriveConfiguredEncoder[GetUtxosResult]

  def fromProto(proto: protobuf.GetUTXOsResult): GetUtxosResult =
    GetUtxosResult(
      proto.utxos.map(Utxo.fromProto).toList,
      proto.total,
      proto.truncated
    )
}
