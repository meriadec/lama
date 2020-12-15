package co.ledger.lama.common.models

import java.util.UUID

import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.manager.protobuf
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{Decoder, Encoder}

case class AccountRegistered(accountId: UUID, syncId: UUID, syncFrequency: Long) {
  def toProto: protobuf.RegisterAccountResult =
    protobuf.RegisterAccountResult(
      UuidUtils.uuidToBytes(accountId),
      UuidUtils.uuidToBytes(syncId),
      syncFrequency
    )
}

object AccountRegistered {
  implicit val decoder: Decoder[AccountRegistered] =
    deriveConfiguredDecoder[AccountRegistered]
  implicit val encoder: Encoder[AccountRegistered] =
    deriveConfiguredEncoder[AccountRegistered]

  def fromProto(proto: protobuf.RegisterAccountResult): AccountRegistered =
    AccountRegistered(
      accountId = UuidUtils.bytesToUuid(proto.accountId).get,
      syncId = UuidUtils.bytesToUuid(proto.syncId).get,
      syncFrequency = proto.syncFrequency
    )
}
