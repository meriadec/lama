package co.ledger.lama.common.models

import io.circe.{Decoder, Encoder}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

abstract class Scheme(val name: String)

object Scheme {

  case object Bip44 extends Scheme("BIP44")
  case object Bip49 extends Scheme("BIP49")
  case object Bip84 extends Scheme("BIP84")

  val all: Map[String, Scheme] = Map(Bip44.name -> Bip44, Bip49.name -> Bip49, Bip84.name -> Bip84)

  def fromKey(key: String): Option[Scheme] = all.get(key)

  implicit val encoder: Encoder[Scheme] =
    Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[Scheme] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as scheme"))

  implicit val configReader: ConfigReader[Scheme] =
    ConfigReader.fromString(str => fromKey(str).toRight(CannotConvert(str, "Scheme", "unknown")))
}
