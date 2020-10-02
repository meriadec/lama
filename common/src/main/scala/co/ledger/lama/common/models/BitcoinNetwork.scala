package co.ledger.lama.common.models

import io.circe.{Decoder, Encoder}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

abstract class BitcoinNetwork(val name: String)

object BitcoinNetwork {

  case object MainNet     extends BitcoinNetwork("MainNet")
  case object TestNet3    extends BitcoinNetwork("TestNet3")
  case object RegTest     extends BitcoinNetwork("RegTest")
  case object Unspecified extends BitcoinNetwork("Unspecified")

  val all: Map[String, BitcoinNetwork] =
    Map(
      MainNet.name     -> MainNet,
      TestNet3.name    -> TestNet3,
      RegTest.name     -> RegTest,
      Unspecified.name -> Unspecified
    )

  def fromKey(key: String): Option[BitcoinNetwork] = all.get(key)

  implicit val encoder: Encoder[BitcoinNetwork] =
    Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[BitcoinNetwork] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as BitcoinNetwork"))

  implicit val configReader: ConfigReader[BitcoinNetwork] =
    ConfigReader.fromString(str =>
      fromKey(str).toRight(CannotConvert(str, "BitcoinNetwork", "unknown"))
    )
}
