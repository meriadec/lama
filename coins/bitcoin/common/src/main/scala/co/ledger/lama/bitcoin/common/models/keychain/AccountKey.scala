package co.ledger.lama.bitcoin.common.models.keychain

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.utils.HexUtils
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{
  deriveConfiguredCodec,
  deriveConfiguredDecoder,
  deriveConfiguredEncoder
}
import co.ledger.protobuf.bitcoin.keychain
import com.google.protobuf.ByteString
import io.circe.syntax._

sealed trait AccountKey {
  def toProto: keychain.CreateKeychainRequest.Account
}

object AccountKey {
  final case class Xpub(extendedPublicKey: String) extends AccountKey {
    def toProto: keychain.CreateKeychainRequest.Account =
      keychain.CreateKeychainRequest.Account.ExtendedPublicKey(extendedPublicKey)
  }

  final case class ChainCode(chainCode: String, publicKey: String, index: Int) extends AccountKey {
    def toProto: keychain.CreateKeychainRequest.Account =
      keychain.CreateKeychainRequest.Account.FromChainCode(
        keychain.FromChainCode(
          chainCode = ByteString.copyFrom(HexUtils.valueOf(chainCode)),
          publicKey = ByteString.copyFrom(HexUtils.valueOf(publicKey)),
          accountIndex = index
        )
      )
  }

  implicit val decoder: Decoder[AccountKey] =
    deriveConfiguredCodec[Xpub]
      .map[AccountKey](identity)
      .or(
        deriveConfiguredDecoder[ChainCode]
          .map[AccountKey](identity)
      )

  implicit val encoder: Encoder[AccountKey] =
    Encoder.instance {
      case x: Xpub      => x.asJson(deriveConfiguredEncoder[Xpub])
      case c: ChainCode => c.asJson(deriveConfiguredEncoder[ChainCode])
    }

}
