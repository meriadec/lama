package co.ledger.lama.bitcoin.common.models.interpreter

import java.time.Instant
import java.util.UUID

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.utils.{TimestampProtoUtils, UuidUtils}
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class BalanceHistory(
    accountId: UUID,
    balance: BigInt,
    blockHeight: Long,
    time: Instant
) {
  def toProto: protobuf.BalanceHistory =
    protobuf.BalanceHistory(
      accountId = UuidUtils.uuidToBytes(accountId),
      balance = balance.toString,
      blockHeight = blockHeight,
      time = Some(TimestampProtoUtils.serialize(time))
    )
}

object BalanceHistory {
  implicit val encoder: Encoder[BalanceHistory] = deriveConfiguredEncoder[BalanceHistory]
  implicit val decoder: Decoder[BalanceHistory] = deriveConfiguredDecoder[BalanceHistory]

  def fromProto(proto: protobuf.BalanceHistory): BalanceHistory =
    BalanceHistory(
      accountId = UuidUtils.unsafeBytesToUuid(proto.accountId),
      balance = BigInt(proto.balance),
      blockHeight = proto.blockHeight,
      time = proto.time.map(TimestampProtoUtils.deserialize).getOrElse(Instant.now)
    )
}
