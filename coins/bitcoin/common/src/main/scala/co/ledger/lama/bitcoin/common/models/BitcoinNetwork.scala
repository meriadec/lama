package co.ledger.lama.bitcoin.common.models

import io.circe.{Decoder, Encoder}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import co.ledger.protobuf.bitcoin.keychain

sealed abstract class BitcoinNetwork(val name: String) {
  def toKeychainProto: keychain.BitcoinNetwork
}

object BitcoinNetwork {

  case object MainNet extends BitcoinNetwork("MainNet") {
    def toKeychainProto: keychain.BitcoinNetwork = keychain.BitcoinNetwork.BITCOIN_NETWORK_MAINNET
  }
  case object TestNet3 extends BitcoinNetwork("TestNet3") {
    def toKeychainProto: keychain.BitcoinNetwork = keychain.BitcoinNetwork.BITCOIN_NETWORK_TESTNET3
  }
  case object RegTest extends BitcoinNetwork("RegTest") {
    def toKeychainProto: keychain.BitcoinNetwork = keychain.BitcoinNetwork.BITCOIN_NETWORK_REGTEST
  }
  case object Unspecified extends BitcoinNetwork("Unspecified") {
    def toKeychainProto: keychain.BitcoinNetwork =
      keychain.BitcoinNetwork.BITCOIN_NETWORK_UNSPECIFIED
  }

  def fromKeychainProto(proto: keychain.BitcoinNetwork): BitcoinNetwork =
    proto match {
      case keychain.BitcoinNetwork.BITCOIN_NETWORK_MAINNET  => MainNet
      case keychain.BitcoinNetwork.BITCOIN_NETWORK_TESTNET3 => TestNet3
      case keychain.BitcoinNetwork.BITCOIN_NETWORK_REGTEST  => RegTest
      case _                                                => Unspecified
    }

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
