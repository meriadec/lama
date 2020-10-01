package co.ledger.lama.bitcoin.common

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import co.ledger.lama.bitcoin.interpreter.protobuf
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}

package object models {

  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

  type BlockHash   = String
  type BlockHeight = Long

  @ConfiguredJsonCodec case class Block(
      hash: BlockHash,
      height: BlockHeight,
      time: String,
      txs: Option[Seq[BlockHash]] = None
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

  @ConfiguredJsonCodec case class Input(
      outputHash: String,
      outputIndex: Int,
      inputIndex: Int,
      value: Long,
      address: String,
      scriptSignature: String,
      txinwitness: Seq[String],
      sequence: Long
  ) {
    def toProto: protobuf.Input =
      protobuf.Input(
        outputHash,
        outputIndex,
        inputIndex,
        value,
        address,
        scriptSignature,
        txinwitness,
        sequence
      )
  }

  object Input {
    implicit val encoder: Encoder[Input] = deriveEncoder[Input]
    implicit val decoder: Decoder[Input] = deriveDecoder[Input]

    def fromProto(proto: protobuf.Input): Input =
      Input(
        proto.outputHash,
        proto.outputIndex,
        proto.inputIndex,
        proto.value,
        proto.address,
        proto.scriptSignature,
        proto.txinwitness,
        proto.sequence
      )
  }

  @ConfiguredJsonCodec case class Output(
      outputIndex: Int,
      value: Long,
      address: String,
      scriptHex: String
  ) {
    def toProto: protobuf.Output =
      protobuf.Output(
        outputIndex,
        value,
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
        proto.value,
        proto.address,
        proto.scriptHex
      )
  }

  @ConfiguredJsonCodec case class Transaction(
      id: String,
      hash: String,
      receivedAt: String,
      lockTime: Long,
      fees: Long,
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
        fees,
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
        proto.fees,
        proto.inputs.map(Input.fromProto),
        proto.outputs.map(Output.fromProto),
        Block.fromProto(proto.block.get),
        proto.confirmations
      )
  }

}
