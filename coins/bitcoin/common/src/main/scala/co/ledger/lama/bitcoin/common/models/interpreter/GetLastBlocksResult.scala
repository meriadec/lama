package co.ledger.lama.bitcoin.common.models.interpreter

import co.ledger.lama.bitcoin.common.models.explorer.Block
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class GetLastBlocksResult(
    blocks: List[Block]
)

object GetLastBlocksResult {
  implicit val encoder: Encoder[GetLastBlocksResult] =
    deriveConfiguredEncoder[GetLastBlocksResult]
  implicit val decoder: Decoder[GetLastBlocksResult] =
    deriveConfiguredDecoder[GetLastBlocksResult]

  def fromProto(proto: protobuf.GetLastBlocksResult): GetLastBlocksResult = {
    GetLastBlocksResult(
      proto.blocks.map(Block.fromProto).toList
    )
  }
}
