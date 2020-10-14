package co.ledger.lama.bitcoin.common.models

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.bitcoin.interpreter.protobuf
import io.circe.generic.extras.semiauto._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

object explorer {

  case class Block(
      hash: String,
      height: Long,
      time: String,
      txs: Option[Seq[String]] = None
  ) {
    def toProto: protobuf.Block =
      protobuf.Block(
        hash,
        height,
        time
      )
  }

  object Block {
    implicit val encoder: Encoder[Block] = deriveConfiguredEncoder[Block]
    implicit val decoder: Decoder[Block] = deriveConfiguredDecoder[Block]

    def fromProto(proto: protobuf.Block): Block =
      Block(proto.hash, proto.height, proto.time)
  }

  sealed trait Input {
    def toProto: protobuf.Input
  }

  case class DefaultInput(
      outputHash: String,
      outputIndex: Long,
      inputIndex: Long,
      value: BigInt,
      address: String,
      scriptSignature: String,
      txinwitness: Seq[String],
      sequence: BigInt
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
            sequence.toString
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
        proto.txinwitness,
        BigInt(proto.sequence)
      )

    implicit val encoder: Encoder[DefaultInput] = deriveConfiguredEncoder[DefaultInput]
    implicit val decoder: Decoder[DefaultInput] = deriveConfiguredDecoder[DefaultInput]
  }

  case class CoinbaseInput(
      coinbase: String,
      inputIndex: Long,
      sequence: BigInt
  ) extends Input {
    def toProto: protobuf.Input =
      protobuf.Input(
        protobuf.Input.Value.Coinbase(
          protobuf.CoinbaseInput(
            coinbase,
            inputIndex,
            sequence.toString
          )
        )
      )
  }

  object CoinbaseInput {
    def fromProto(proto: protobuf.CoinbaseInput): CoinbaseInput =
      CoinbaseInput(
        proto.coinbase,
        proto.inputIndex,
        BigInt(proto.sequence)
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

  case class Output(
      outputIndex: Long,
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

  case class Transaction(
      id: String,
      hash: String,
      receivedAt: String,
      lockTime: Long,
      fees: BigInt,
      inputs: Seq[Input],
      outputs: Seq[Output],
      block: Block,
      confirmations: Int
  ) {
    def toProto: protobuf.Transaction =
      protobuf.Transaction(
        id,
        hash,
        receivedAt,
        lockTime,
        fees.toString,
        inputs.map(_.toProto),
        outputs.map(_.toProto),
        Some(block.toProto),
        confirmations
      )
  }

  object Transaction {
    implicit val encoder: Encoder[Transaction] = deriveConfiguredEncoder[Transaction]
    implicit val decoder: Decoder[Transaction] = deriveConfiguredDecoder[Transaction]

    def fromProto(proto: protobuf.Transaction): Transaction =
      Transaction(
        proto.id,
        proto.hash,
        proto.receivedAt,
        proto.lockTime,
        BigInt(proto.fees),
        proto.inputs.map(Input.fromProto),
        proto.outputs.map(Output.fromProto),
        Block.fromProto(
          proto.getBlock
        ), // block should never be missing, it's because of protobuf cc generator
        proto.confirmations
      )
  }

}
