package co.ledger.lama.bitcoin.common.models

import co.ledger.lama.bitcoin.interpreter.protobuf
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.generic.semiauto._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

object explorer {

  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

  @ConfiguredJsonCodec case class Block(
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
    implicit val encoder: Encoder[Block] = deriveEncoder[Block]
    implicit val decoder: Decoder[Block] = deriveDecoder[Block]

    def fromProto(proto: protobuf.Block): Block =
      Block(proto.hash, proto.height, proto.time)
  }

  sealed trait Input {
    def toProto: protobuf.Input
  }

  @ConfiguredJsonCodec case class DefaultInput(
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
  }

  @ConfiguredJsonCodec case class CoinbaseInput(
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

  @ConfiguredJsonCodec case class Output(
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
    implicit val encoder: Encoder[Output] = deriveEncoder[Output]
    implicit val decoder: Decoder[Output] = deriveDecoder[Output]

    def fromProto(proto: protobuf.Output): Output =
      Output(
        proto.outputIndex,
        BigInt(proto.value),
        proto.address,
        proto.scriptHex
      )
  }

  @ConfiguredJsonCodec case class Transaction(
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
    implicit val encoder: Encoder[Transaction] = deriveEncoder[Transaction]
    implicit val decoder: Decoder[Transaction] = deriveDecoder[Transaction]

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
