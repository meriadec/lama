package co.ledger.lama.manager.utils

import java.time.Instant
import java.util.UUID

import co.ledger.lama.common.models._
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.common.utils.{ProtobufUtils => CommonProtobufUtils}
import co.ledger.lama.manager.protobuf
import io.circe.parser.parse

object ProtobufUtils {

  def from(pb: protobuf.RegisterAccountRequest): AccountIdentifier =
    AccountIdentifier(
      pb.key,
      CommonProtobufUtils.fromCoinFamily(pb.coinFamily),
      CommonProtobufUtils.fromCoin(pb.coin)
    )

  def from(accountId: UUID, pb: protobuf.SyncEvent): Option[SyncEvent] =
    for {
      syncId  <- UuidUtils.bytesToUuid(pb.syncId)
      status  <- Status.fromKey(pb.status)
      payload <- parse(new String(pb.payload.toByteArray)).flatMap(_.as[SyncEvent.Payload]).toOption
    } yield {
      SyncEvent(
        accountId,
        syncId,
        status,
        payload,
        pb.time.map(CommonProtobufUtils.toInstant).getOrElse(Instant.now())
      )
    }
}
