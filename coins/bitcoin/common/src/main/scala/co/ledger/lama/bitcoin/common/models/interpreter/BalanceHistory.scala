package co.ledger.lama.bitcoin.common.models.interpreter

import java.time.Instant

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.utils.TimestampProtoUtils
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

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
      time = Some(TimestampProtoUtils.serialize(time))
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
      time = proto.time.map(TimestampProtoUtils.deserialize).getOrElse(Instant.now)
    )
}
