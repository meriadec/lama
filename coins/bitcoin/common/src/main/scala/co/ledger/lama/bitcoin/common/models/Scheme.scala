package co.ledger.lama.bitcoin.common.models

import co.ledger.protobuf.bitcoin.keychain
import io.circe.{Decoder, Encoder}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

sealed abstract class Scheme(val name: String) {
  def toProto: keychain.Scheme
}

object Scheme {

  case object Bip44 extends Scheme("BIP44") {
    def toProto: keychain.Scheme = keychain.Scheme.SCHEME_BIP44
  }
  case object Bip49 extends Scheme("BIP49") {
    def toProto: keychain.Scheme = keychain.Scheme.SCHEME_BIP49
  }
  case object Bip84 extends Scheme("BIP84") {
    def toProto: keychain.Scheme = keychain.Scheme.SCHEME_BIP84
  }
  case object Unspecified extends Scheme("Unspecified") {
    def toProto: keychain.Scheme = keychain.Scheme.SCHEME_UNSPECIFIED
  }

  def fromProto(proto: keychain.Scheme): Scheme =
    proto match {
      case keychain.Scheme.SCHEME_BIP44 => Scheme.Bip44
      case keychain.Scheme.SCHEME_BIP49 => Scheme.Bip49
      case keychain.Scheme.SCHEME_BIP84 => Scheme.Bip84
      case _                            => Scheme.Unspecified
    }

  val all: Map[String, Scheme] = Map(Bip44.name -> Bip44, Bip49.name -> Bip49, Bip84.name -> Bip84)

  def fromKey(key: String): Option[Scheme] = all.get(key)

  implicit val encoder: Encoder[Scheme] =
    Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[Scheme] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as scheme"))

  implicit val configReader: ConfigReader[Scheme] =
    ConfigReader.fromString(str => fromKey(str).toRight(CannotConvert(str, "Scheme", "unknown")))

}
