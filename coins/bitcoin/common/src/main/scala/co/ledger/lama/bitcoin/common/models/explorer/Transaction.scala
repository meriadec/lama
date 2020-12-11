package co.ledger.lama.bitcoin.common.models.explorer

import java.time.Instant

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.utils.TimestampProtoUtils
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

sealed trait Transaction {
  val id: String
  val hash: String
  val receivedAt: Instant
  val lockTime: Long
  val fees: BigInt
  val inputs: Seq[Input]
  val outputs: Seq[Output]
  val confirmations: Int
}

object Transaction {
  implicit val encoder: Encoder[Transaction] =
    Encoder.instance {
      case confirmedTx: ConfirmedTransaction     => confirmedTx.asJson
      case unconfirmedTx: UnconfirmedTransaction => unconfirmedTx.asJson
    }

  implicit val decoder: Decoder[Transaction] = Decoder[ConfirmedTransaction]
    .map[Transaction](identity)
    .or(Decoder[UnconfirmedTransaction].map[Transaction](identity))
}

case class ConfirmedTransaction(
    id: String,
    hash: String,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[Input],
    outputs: Seq[Output],
    block: Block,
    confirmations: Int
) extends Transaction {
  def toProto: protobuf.Transaction =
    protobuf.Transaction(
      id,
      hash,
      Some(TimestampProtoUtils.serialize(receivedAt)),
      lockTime,
      fees.toString,
      inputs.map(_.toProto),
      outputs.map(_.toProto),
      Some(block.toProto),
      confirmations
    )
}

object ConfirmedTransaction {
  implicit val encoder: Encoder[ConfirmedTransaction] =
    deriveConfiguredEncoder[ConfirmedTransaction]

  implicit val decoder: Decoder[ConfirmedTransaction] =
    deriveConfiguredDecoder[ConfirmedTransaction]

  def fromProto(proto: protobuf.Transaction): ConfirmedTransaction =
    ConfirmedTransaction(
      proto.id,
      proto.hash,
      proto.receivedAt.map(TimestampProtoUtils.deserialize).getOrElse(Instant.now),
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

case class UnconfirmedTransaction(
    id: String,
    hash: String,
    receivedAt: Instant,
    lockTime: Long,
    fees: BigInt,
    inputs: Seq[Input],
    outputs: Seq[Output],
    confirmations: Int
) extends Transaction

object UnconfirmedTransaction {
  implicit val encoder: Encoder[UnconfirmedTransaction] =
    deriveConfiguredEncoder[UnconfirmedTransaction]

  implicit val decoder: Decoder[UnconfirmedTransaction] =
    deriveConfiguredDecoder[UnconfirmedTransaction]
}
