package co.ledger.lama.bitcoin.common.models

import java.time.Instant
import java.util.UUID

import co.ledger.lama.common.models.implicits.defaultCirceConfig
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.utils.{ProtobufUtils, UuidUtils}
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}

object service {

  case class BlockView(
      hash: String,
      height: Long,
      time: Instant
  ) {
    def toProto: protobuf.BlockView =
      protobuf.BlockView(
        hash,
        height,
        Some(ProtobufUtils.fromInstant(time))
      )
  }

  object BlockView {
    implicit val encoder: Encoder[BlockView] = deriveConfiguredEncoder[BlockView]
    implicit val decoder: Decoder[BlockView] = deriveConfiguredDecoder[BlockView]

    def fromProto(proto: protobuf.BlockView): BlockView =
      BlockView(
        proto.hash,
        proto.height,
        proto.time.map(ProtobufUtils.toInstant).getOrElse(Instant.now)
      )
  }

  case class InputView(
      outputHash: String,
      outputIndex: Int,
      inputIndex: Int,
      value: BigInt,
      address: String,
      scriptSignature: String,
      txinwitness: List[String],
      sequence: Long,
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
        sequence,
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
        proto.txinwitness.toList,
        proto.sequence,
        proto.belongs
      )

    implicit val encoder: Encoder[InputView] = deriveConfiguredEncoder[InputView]
    implicit val decoder: Decoder[InputView] = deriveConfiguredDecoder[InputView]
  }

  sealed trait ChangeType {
    val name: String
    def toProto: protobuf.ChangeType
  }
  final case object Internal extends ChangeType {
    val name = "internal"

    def toProto: protobuf.ChangeType = {
      protobuf.ChangeType.INTERNAL
    }
  }
  final case object External extends ChangeType {
    val name = "external"

    def toProto: protobuf.ChangeType = {
      protobuf.ChangeType.EXTERNAL
    }
  }

  object ChangeType {
    implicit val encoder: Encoder[ChangeType] = Encoder.encodeString.contramap(_.name)
    implicit val decoder: Decoder[ChangeType] =
      Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as change type"))

    val all: Map[String, ChangeType] = Map(Internal.name -> Internal, External.name -> External)

    def fromKey(key: String): Option[ChangeType] = all.get(key)

    def fromProto(proto: protobuf.ChangeType): ChangeType = {
      proto match {
        case protobuf.ChangeType.INTERNAL => Internal
        case _                            => External
      }
    }
  }

  case class Utxo(
      outputIndex: Int,
      value: BigInt,
      address: String,
      scriptHex: String,
      belongs: Boolean,
      changeType: Option[ChangeType],
      time: Instant
  ) {
    def toProto: protobuf.Utxo =
      protobuf.Utxo(
        outputIndex,
        value.toString,
        address,
        scriptHex,
        belongs,
        changeType.getOrElse(External).toProto,
        Some(ProtobufUtils.fromInstant(time))
      )
  }

  object Utxo {
    implicit val encoder: Encoder[Utxo] = deriveConfiguredEncoder[Utxo]
    implicit val decoder: Decoder[Utxo] = deriveConfiguredDecoder[Utxo]

    def fromProto(proto: protobuf.Utxo): Utxo =
      Utxo(
        proto.outputIndex,
        BigInt(proto.value),
        proto.address,
        proto.scriptHex,
        proto.belongs,
        Some(ChangeType.fromProto(proto.changeType)),
        proto.time.map(ProtobufUtils.toInstant).getOrElse(Instant.now())
      )
  }

  case class OutputView(
      outputIndex: Int,
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
    implicit val encoder: Encoder[OutputView] = deriveConfiguredEncoder[OutputView]
    implicit val decoder: Decoder[OutputView] = deriveConfiguredDecoder[OutputView]

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

  case class TransactionView(
      id: String,
      hash: String,
      receivedAt: Instant,
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
        Some(ProtobufUtils.fromInstant(receivedAt)),
        lockTime,
        fees.toString,
        inputs.map(_.toProto),
        outputs.map(_.toProto),
        Some(block.toProto),
        confirmations
      )
  }

  object TransactionView {
    implicit val encoder: Encoder[TransactionView] = deriveConfiguredEncoder[TransactionView]
    implicit val decoder: Decoder[TransactionView] = deriveConfiguredDecoder[TransactionView]

    def fromProto(proto: protobuf.TransactionView): TransactionView =
      TransactionView(
        proto.id,
        proto.hash,
        proto.receivedAt.map(ProtobufUtils.toInstant).getOrElse(Instant.now),
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
    val name: String
    def toProto: protobuf.OperationType
  }
  final case object Sent extends OperationType {
    val name = "sent"
    def toProto: protobuf.OperationType = {
      protobuf.OperationType.SENT
    }
  }
  final case object Received extends OperationType {
    val name = "received"
    def toProto: protobuf.OperationType = {
      protobuf.OperationType.RECEIVED
    }
  }

  object OperationType {
    implicit val encoder: Encoder[OperationType] = Encoder.encodeString.contramap(_.name)
    implicit val decoder: Decoder[OperationType] =
      Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as operation type"))

    val all: Map[String, OperationType] = Map(Sent.name -> Sent, Received.name -> Received)

    def fromKey(key: String): Option[OperationType] = all.get(key)

    def fromProto(proto: protobuf.OperationType): OperationType = {
      proto match {
        case protobuf.OperationType.SENT => Sent
        case _                           => Received

      }
    }
  }

  case class Operation(
      accountId: UUID,
      hash: String,
      transaction: Option[TransactionView],
      operationType: OperationType,
      value: BigInt,
      fees: BigInt,
      time: Instant
  ) {
    def toProto: protobuf.Operation = {
      protobuf.Operation(
        UuidUtils.uuidToBytes(accountId),
        hash,
        transaction.map(_.toProto),
        operationType.toProto,
        value.toString,
        fees.toString,
        Some(ProtobufUtils.fromInstant(time))
      )
    }
  }

  object Operation {
    implicit val encoder: Encoder[Operation] = deriveConfiguredEncoder[Operation]
    implicit val decoder: Decoder[Operation] = deriveConfiguredDecoder[Operation]

    def fromProto(proto: protobuf.Operation): Operation = {
      Operation(
        UuidUtils
          .bytesToUuid(proto.accountId)
          .getOrElse(throw UuidUtils.InvalidUUIDException),
        proto.hash,
        proto.transaction.map(TransactionView.fromProto),
        OperationType.fromProto(proto.operationType),
        BigInt(proto.value),
        BigInt(proto.fees),
        proto.time.map(ProtobufUtils.toInstant).getOrElse(Instant.now)
      )
    }
  }

  case class AccountAddress(
      accountAddress: String,
      changeType: ChangeType
  ) {
    def toProto: protobuf.AccountAddress = {
      protobuf.AccountAddress(accountAddress, changeType.toProto)
    }
  }

  object AccountAddress {
    implicit val encoder: Encoder[AccountAddress] = deriveConfiguredEncoder[AccountAddress]
    implicit val decoder: Decoder[AccountAddress] = deriveConfiguredDecoder[AccountAddress]

    def fromProto(proto: protobuf.AccountAddress): AccountAddress = {
      AccountAddress(
        proto.accountAddress,
        ChangeType.fromProto(proto.changeType)
      )
    }
  }

  case class BalanceHistory(
      balance: BigInt,
      utxos: Int,
      received: BigInt,
      sent: BigInt,
      time: Instant = Instant.now()
  ) {
    def toProto: protobuf.BalanceHistory =
      protobuf.BalanceHistory(
        balance = balance.toString,
        utxos = utxos,
        received = received.toString,
        sent = sent.toString,
        time = Some(ProtobufUtils.fromInstant(time))
      )
  }

  object BalanceHistory {
    implicit val encoder: Encoder[BalanceHistory] = deriveConfiguredEncoder[BalanceHistory]
    implicit val decoder: Decoder[BalanceHistory] = deriveConfiguredDecoder[BalanceHistory]

    def fromProto(proto: protobuf.BalanceHistory): BalanceHistory =
      BalanceHistory(
        balance = BigInt(proto.balance),
        utxos = proto.utxos,
        received = BigInt(proto.received),
        sent = BigInt(proto.sent),
        time = proto.time.map(ProtobufUtils.toInstant).getOrElse(Instant.now)
      )

  }

}
