package co.ledger.lama.bitcoin.common.models.interpreter

import java.time.Instant
import java.util.UUID

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.utils.{TimestampProtoUtils, UuidUtils}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

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
      Some(TimestampProtoUtils.serialize(time))
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
      proto.time.map(TimestampProtoUtils.deserialize).getOrElse(Instant.now)
    )
  }
}
