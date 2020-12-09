package co.ledger.lama.bitcoin.common.models

import java.util.UUID

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

package object keychain {

  case class KeychainInfo(
      keychainId: UUID,
      externalDescriptor: String,
      internalDescriptor: String,
      extendedPublicKey: String,
      slip32ExtendedPublicKey: String,
      lookaheadSize: Int,
      scheme: Scheme,
      network: BitcoinNetwork
  ) {
    def toProto: keychain.KeychainInfo =
      keychain.KeychainInfo(
        UuidUtils.uuidToBytes(keychainId),
        externalDescriptor,
        internalDescriptor,
        extendedPublicKey,
        slip32ExtendedPublicKey,
        lookaheadSize,
        scheme.toProto,
        network.toKeychainProto
      )
  }

  object KeychainInfo {

    implicit val encoder: Encoder[KeychainInfo] = deriveConfiguredEncoder[KeychainInfo]
    implicit val decoder: Decoder[KeychainInfo] = deriveConfiguredDecoder[KeychainInfo]

    def fromProto(proto: keychain.KeychainInfo): KeychainInfo =
      KeychainInfo(
        UuidUtils.unsafeBytesToUuid(proto.keychainId),
        proto.externalDescriptor,
        proto.internalDescriptor,
        proto.extendedPublicKey,
        proto.slip32ExtendedPublicKey,
        proto.lookaheadSize,
        Scheme.fromProto(proto.scheme),
        BitcoinNetwork.fromKeychainProto(proto.network)
      )
  }

}
