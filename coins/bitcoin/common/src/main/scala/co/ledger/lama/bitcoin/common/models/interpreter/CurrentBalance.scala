package co.ledger.lama.bitcoin.common.models.interpreter

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class BlockchainBalance(
    balance: BigInt,
    utxos: Int,
    received: BigInt,
    sent: BigInt
)

case class CurrentBalance(
    balance: BigInt,
    utxos: Int,
    received: BigInt,
    sent: BigInt,
    unconfirmedBalance: BigInt
) {
  def toProto: protobuf.CurrentBalance =
    protobuf.CurrentBalance(
      balance = balance.toString,
      utxos = utxos,
      received = received.toString,
      sent = sent.toString,
      unconfirmedBalance = unconfirmedBalance.toString
    )
}

object CurrentBalance {
  implicit val encoder: Encoder[CurrentBalance] = deriveConfiguredEncoder[CurrentBalance]
  implicit val decoder: Decoder[CurrentBalance] = deriveConfiguredDecoder[CurrentBalance]

  def fromProto(proto: protobuf.CurrentBalance): CurrentBalance =
    CurrentBalance(
      balance = BigInt(proto.balance),
      utxos = proto.utxos,
      received = BigInt(proto.received),
      sent = BigInt(proto.sent),
      unconfirmedBalance = BigInt(proto.unconfirmedBalance)
    )
}
