package co.ledger.lama.bitcoin.common.models.explorer

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.syntax._

sealed trait Input {
  def toProto: protobuf.Input
}

case class DefaultInput(
    outputHash: String,
    outputIndex: Int,
    inputIndex: Int,
    value: BigInt,
    address: String,
    scriptSignature: String,
    txinwitness: List[String],
    sequence: Long
) extends Input {
  def toProto: protobuf.Input =
    protobuf.Input(
      protobuf.Input.Value.Default(
        protobuf.DefaultInput(
          outputHash,
          outputIndex,
          inputIndex,
          value.toString,
          address,
          scriptSignature,
          txinwitness,
          sequence
        )
      )
    )
}

object DefaultInput {
  def fromProto(proto: protobuf.DefaultInput): DefaultInput =
    DefaultInput(
      proto.outputHash,
      proto.outputIndex,
      proto.inputIndex,
      BigInt(proto.value),
      proto.address,
      proto.scriptSignature,
      proto.txinwitness.toList,
      proto.sequence
    )

  implicit val encoder: Encoder[DefaultInput] = deriveConfiguredEncoder[DefaultInput]
  implicit val decoder: Decoder[DefaultInput] = deriveConfiguredDecoder[DefaultInput]
}

case class CoinbaseInput(
    coinbase: String,
    inputIndex: Int,
    sequence: Long
) extends Input {
  def toProto: protobuf.Input =
    protobuf.Input(
      protobuf.Input.Value.Coinbase(
        protobuf.CoinbaseInput(
          coinbase,
          inputIndex,
          sequence
        )
      )
    )
}

object CoinbaseInput {
  def fromProto(proto: protobuf.CoinbaseInput): CoinbaseInput =
    CoinbaseInput(
      proto.coinbase,
      proto.inputIndex,
      proto.sequence
    )

  implicit val encoder: Encoder[CoinbaseInput] = deriveConfiguredEncoder[CoinbaseInput]
  implicit val decoder: Decoder[CoinbaseInput] = deriveConfiguredDecoder[CoinbaseInput]
}

object Input {
  implicit val encoder: Encoder[Input] =
    Encoder.instance {
      case defaultInput: DefaultInput   => defaultInput.asJson
      case coinbaseInput: CoinbaseInput => coinbaseInput.asJson
    }

  implicit val decoder: Decoder[Input] =
    Decoder[DefaultInput]
      .map[Input](identity)
      .or(Decoder[CoinbaseInput].map[Input](identity))

  def fromProto(proto: protobuf.Input): Input =
    if (proto.value.isDefault)
      DefaultInput.fromProto(proto.getDefault)
    else
      CoinbaseInput.fromProto(proto.getCoinbase)
}
