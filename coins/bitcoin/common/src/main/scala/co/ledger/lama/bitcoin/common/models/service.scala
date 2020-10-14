package co.ledger.lama.bitcoin.common.models

import java.util.UUID

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.utils.UuidUtils
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

object service {

  implicit val config: Configuration = Configuration.default.withSnakeCaseMemberNames

  @ConfiguredJsonCodec case class BlockView(
      hash: String,
      height: Long,
      time: String
  ) {
    def toProto: protobuf.BlockView =
      protobuf.BlockView(
        hash,
        height,
        time
      )
  }

  object BlockView {
    implicit val encoder: Encoder[BlockView] = deriveEncoder[BlockView]
    implicit val decoder: Decoder[BlockView] = deriveDecoder[BlockView]

    def fromProto(proto: protobuf.BlockView): BlockView =
      BlockView(proto.hash, proto.height, proto.time)
  }

  @ConfiguredJsonCodec case class InputView(
      outputHash: String,
      outputIndex: Long,
      inputIndex: Long,
      value: BigInt,
      address: String,
      scriptSignature: String,
      txinwitness: Seq[String],
      sequence: BigInt,
      belongs: Boolean
  ) {
    def toProto: protobuf.InputView =
      protobuf.InputView(
        outputHash,
        outputIndex,
        inputIndex,
        value.toString,
        address,
        scriptSignature,
        txinwitness,
        sequence.toString,
        belongs
      )
  }

  object InputView {
    def fromProto(proto: protobuf.InputView): InputView =
      InputView(
        proto.outputHash,
        proto.outputIndex,
        proto.inputIndex,
        BigInt(proto.value),
        proto.address,
        proto.scriptSignature,
        proto.txinwitness,
        BigInt(proto.sequence),
        proto.belongs
      )
  }

  sealed trait ChangeType {
    def toProto: protobuf.ChangeType
  }
  final case object Internal extends ChangeType {
    def toProto: protobuf.ChangeType = {
      protobuf.ChangeType.INTERNAL
    }
  }
  final case object External extends ChangeType {
    def toProto: protobuf.ChangeType = {
      protobuf.ChangeType.EXTERNAL
    }
  }

  object ChangeType {
    implicit val encoder: Encoder[ChangeType] = deriveEncoder[ChangeType]
    implicit val decoder: Decoder[ChangeType] = deriveDecoder[ChangeType]

    def fromKey(key: String): Option[ChangeType] = {
      key match {
        case "internal" => Some(Internal)
        case "external" => Some(External)
        case _          => None
      }
    }

    def fromProto(proto: protobuf.ChangeType): ChangeType = {
      proto match {
        case protobuf.ChangeType.INTERNAL => Internal
        case _                            => External

      }
    }
  }

  @ConfiguredJsonCodec case class OutputView(
      outputIndex: Long,
      value: BigInt,
      address: String,
      scriptHex: String,
      belongs: Boolean,
      changeType: Option[ChangeType]
  ) {
    def toProto: protobuf.OutputView =
      protobuf.OutputView(
        outputIndex,
        value.toString,
        address,
        scriptHex,
        belongs,
        changeType.getOrElse(External).toProto
      )
  }

  object OutputView {
    implicit val encoder: Encoder[OutputView] = deriveEncoder[OutputView]
    implicit val decoder: Decoder[OutputView] = deriveDecoder[OutputView]

    def fromProto(proto: protobuf.OutputView): OutputView =
      OutputView(
        proto.outputIndex,
        BigInt(proto.value),
        proto.address,
        proto.scriptHex,
        proto.belongs,
        Some(ChangeType.fromProto(proto.changeType))
      )
  }

  @ConfiguredJsonCodec case class TransactionView(
      id: String,
      hash: String,
      receivedAt: String,
      lockTime: Long,
      fees: BigInt,
      inputs: Seq[InputView],
      outputs: Seq[OutputView],
      block: BlockView,
      confirmations: Int
  ) {
    def toProto: protobuf.TransactionView =
      protobuf.TransactionView(
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

  object TransactionView {
    implicit val encoder: Encoder[TransactionView] = deriveEncoder[TransactionView]
    implicit val decoder: Decoder[TransactionView] = deriveDecoder[TransactionView]

    def fromProto(proto: protobuf.TransactionView): TransactionView =
      TransactionView(
        proto.id,
        proto.hash,
        proto.receivedAt,
        proto.lockTime,
        BigInt(proto.fees),
        proto.inputs.map(InputView.fromProto),
        proto.outputs.map(OutputView.fromProto),
        BlockView.fromProto(
          proto.getBlock
        ), // block should never be missing, it's because of protobuf cc generator
        proto.confirmations
      )
  }

  sealed trait OperationType {
    def toProto: protobuf.OperationType
  }
  final case object Sent extends OperationType {
    def toProto: protobuf.OperationType = {
      protobuf.OperationType.SENT
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
        case "sent"     => Some(Sent)
        case "received" => Some(Received)
        case _          => None
      }
    }

    def fromProto(proto: protobuf.OperationType): OperationType = {
      proto match {
        case protobuf.OperationType.SENT => Sent
        case _                           => Received

      }
    }
  }

  @ConfiguredJsonCodec case class Operation(
      accountId: UUID,
      hash: String,
      transaction: Option[TransactionView],
      operationType: OperationType,
      value: BigInt,
      time: String
  ) {
    def toProto: protobuf.Operation = {
      protobuf.Operation(
        UuidUtils.uuidToBytes(accountId),
        hash,
        transaction.map(_.toProto),
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
        proto.transaction.map(TransactionView.fromProto),
        OperationType.fromProto(proto.operationType),
        BigInt(proto.value),
        proto.time
      )
    }
  }

  @ConfiguredJsonCodec case class AccountAddress(
      accountAddress: String,
      changeType: ChangeType
  ) {
    def toProto: protobuf.AccountAddress = {
      protobuf.AccountAddress(accountAddress, changeType.toProto)
    }
  }

  object AccountAddress {
    implicit val encoder: Encoder[AccountAddress] = deriveEncoder[AccountAddress]
    implicit val decoder: Decoder[AccountAddress] = deriveDecoder[AccountAddress]

    def fromProto(proto: protobuf.AccountAddress): AccountAddress = {
      AccountAddress(
        proto.accountAddress,
        ChangeType.fromProto(proto.changeType)
      )
    }
  }

  case class AccountBalance(
      balance: BigInt,
      utxoCount: Int,
      amountSpent: BigInt,
      amountReceived: BigInt
  ) {
    def toProto: protobuf.GetBalanceResult = {
      protobuf.GetBalanceResult(
        balance.toLong,
        utxoCount,
        amountSpent.toLong,
        amountReceived.toLong
      )
    }
  }

}
