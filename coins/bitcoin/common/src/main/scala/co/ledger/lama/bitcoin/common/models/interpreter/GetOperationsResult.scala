package co.ledger.lama.bitcoin.common.models.interpreter

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class GetOperationsResult(
    truncated: Boolean,
    operations: List[Operation],
    size: Int,
    total: Int
)

object GetOperationsResult {
  implicit val decoder: Decoder[GetOperationsResult] =
    deriveConfiguredDecoder[GetOperationsResult]
  implicit val encoder: Encoder[GetOperationsResult] =
    deriveConfiguredEncoder[GetOperationsResult]

  def fromProto(proto: protobuf.GetOperationsResult): GetOperationsResult =
    GetOperationsResult(
      proto.truncated,
      proto.operations.map(Operation.fromProto).toList,
      proto.operations.size,
      proto.total
    )
}
