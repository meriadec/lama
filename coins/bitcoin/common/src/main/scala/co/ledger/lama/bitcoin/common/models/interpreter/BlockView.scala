package co.ledger.lama.bitcoin.common.models.interpreter

import java.time.Instant

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.utils.TimestampProtoUtils
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class BlockView(
    hash: String,
    height: Long,
    time: Instant
) {
  def toProto: protobuf.BlockView =
    protobuf.BlockView(
      hash,
      height,
      Some(TimestampProtoUtils.serialize(time))
    )
}

object BlockView {
  implicit val encoder: Encoder[BlockView] = deriveConfiguredEncoder[BlockView]
  implicit val decoder: Decoder[BlockView] = deriveConfiguredDecoder[BlockView]

  def fromProto(proto: protobuf.BlockView): BlockView =
    BlockView(
      proto.hash,
      proto.height,
      proto.time.map(TimestampProtoUtils.deserialize).getOrElse(Instant.now)
    )
}
