package co.ledger.lama.common.models

import io.circe.{Decoder, Encoder}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

sealed abstract class Coin(val name: String) {
  override def toString: String = name
}

object Coin {
  case object Btc extends Coin("btc")
  case object BtcTestnet extends  Coin("btc_testnet")

  val all: Map[String, Coin] = Map(
    Btc.name -> Btc,
    BtcTestnet.name -> BtcTestnet
  )

  def fromKey(key: String): Option[Coin] = all.get(key)

  implicit val encoder: Encoder[Coin] =
    Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[Coin] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as coin"))

  implicit val configReader: ConfigReader[Coin] =
    ConfigReader.fromString(str => fromKey(str).toRight(CannotConvert(str, "Coin", "unknown")))
}
