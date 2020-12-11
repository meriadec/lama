package co.ledger.lama.bitcoin.common.models.interpreter

import cats.data.NonEmptyList
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import co.ledger.protobuf.bitcoin.keychain
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class AccountAddress(
    accountAddress: String,
    changeType: ChangeType,
    derivation: NonEmptyList[Int]
) {
  def toProto: protobuf.AccountAddress = {
    protobuf.AccountAddress(accountAddress, changeType.toProto, derivation.toList)
  }
}

object AccountAddress {
  implicit val encoder: Encoder[AccountAddress] = deriveConfiguredEncoder[AccountAddress]
  implicit val decoder: Decoder[AccountAddress] = deriveConfiguredDecoder[AccountAddress]

  def fromProto(proto: protobuf.AccountAddress): AccountAddress = {
    AccountAddress(
      proto.accountAddress,
      ChangeType.fromProto(proto.changeType),
      NonEmptyList.fromListUnsafe(proto.derivation.toList)
    )
  }

  def fromKeychainProto(proto: keychain.AddressInfo): AccountAddress =
    AccountAddress(
      proto.address,
      ChangeType.fromKeychainProto(proto.change),
      NonEmptyList.fromListUnsafe(proto.derivation.toList)
    )
}
