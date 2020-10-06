package co.ledger.lama.bitcoin.common

import java.util.UUID

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.utils.UuidUtils
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.syntax.EncoderOps

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

  sealed trait OperationType {
    def toProto: protobuf.OperationType
  }
  final case object Send extends OperationType {
    def toProto: protobuf.OperationType = {
      protobuf.OperationType.SEND
    }
  }
  final case object Received extends OperationType {
    def toProto: protobuf.OperationType = {
      protobuf.OperationType.RECEIVED
    }
  }

  object OperationType {
    implicit val encoder: Encoder[OperationType] = deriveEncoder[OperationType]
    implicit val decoder: Decoder[OperationType] = deriveDecoder[OperationType]

    def fromKey(key: String): Option[OperationType] = {
      key match {
        case "send"     => Some(Send)
        case "received" => Some(Received)
        case _          => None
      }
    }

    def fromProto(proto: protobuf.OperationType): OperationType = {
      proto match {
        case protobuf.OperationType.SEND => Send
        case _                           => Received

      }
    }
  }

  @ConfiguredJsonCodec case class Operation(
      accountId: UUID,
      hash: String,
      operationType: OperationType,
      value: BigInt,
      time: String
  ) {
    def toProto: protobuf.Operation = {
      protobuf.Operation(
        UuidUtils.uuidToBytes(accountId),
        hash,
        operationType.toProto,
        value.toLong,
        time
      )
    }
  }

  object Operation {
    implicit val encoder: Encoder[Operation] = deriveEncoder[Operation]
    implicit val decoder: Decoder[Operation] = deriveDecoder[Operation]

    def fromProto(proto: protobuf.Operation): Operation = {
      Operation(
        UuidUtils
          .bytesToUuid(proto.accountId)
          .getOrElse(throw UuidUtils.InvalidUUIDException),
        proto.hash,
        OperationType.fromProto(proto.operationType),
        BigInt(proto.value),
        proto.time
      )
    }
  }

}
