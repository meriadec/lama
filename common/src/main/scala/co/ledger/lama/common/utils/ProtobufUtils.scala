package co.ledger.lama.common.utils

import java.time.Instant

import co.ledger.lama.common.models.{Coin, CoinFamily}

object ProtobufUtils {

  def fromInstant(instant: Instant): com.google.protobuf.timestamp.Timestamp =
    com.google.protobuf.timestamp.Timestamp(
      seconds = instant.getEpochSecond,
      nanos = instant.getNano
    )

  def toInstant(timestamp: com.google.protobuf.timestamp.Timestamp): Instant =
    Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos)

  def toCoinFamily(cf: CoinFamily): co.ledger.lama.manager.protobuf.CoinFamily =
    cf match {
      case CoinFamily.Bitcoin => co.ledger.lama.manager.protobuf.CoinFamily.bitcoin
      case _                  => co.ledger.lama.manager.protobuf.CoinFamily.Unrecognized(-1)
    }

  def toCoin(c: Coin): co.ledger.lama.manager.protobuf.Coin =
    c match {
      case Coin.Btc        => co.ledger.lama.manager.protobuf.Coin.btc
      case Coin.BtcTestnet => co.ledger.lama.manager.protobuf.Coin.btc_testnet
      case _               => co.ledger.lama.manager.protobuf.Coin.Unrecognized(-1)
    }

  val fromCoin: PartialFunction[co.ledger.lama.manager.protobuf.Coin, Coin] = {
    case co.ledger.lama.manager.protobuf.Coin.btc         => Coin.Btc
    case co.ledger.lama.manager.protobuf.Coin.btc_testnet => Coin.BtcTestnet
  }

  val fromCoinFamily: PartialFunction[co.ledger.lama.manager.protobuf.CoinFamily, CoinFamily] = {
    case co.ledger.lama.manager.protobuf.CoinFamily.bitcoin => CoinFamily.Bitcoin
  }

}
