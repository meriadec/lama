package co.ledger.lama.bitcoin.common.models.explorer

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class Output(
    outputIndex: Int,
    value: BigInt,
    address: String,
    scriptHex: String
) {
  def toProto: protobuf.Output =
    protobuf.Output(
      outputIndex,
      value.toString,
      address,
      scriptHex
    )
}

object Output {
  implicit val encoder: Encoder[Output] = deriveConfiguredEncoder[Output]
  implicit val decoder: Decoder[Output] = deriveConfiguredDecoder[Output]

  def fromProto(proto: protobuf.Output): Output =
    Output(
      proto.outputIndex,
      BigInt(proto.value),
      proto.address,
      proto.scriptHex
    )
}
