package co.ledger.lama.common.models

import java.util.UUID

import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.extras.semiauto._
import io.circe.syntax.EncoderOps

sealed trait SyncEvent extends WithKey[UUID] {
  def accountId: UUID
  def syncId: UUID
  def status: Status
  def payload: SyncEvent.Payload
  def key: UUID = accountId

  override def toString: String = s"accountId: $accountId - syncId: $syncId - status: $status"
}

trait WithKey[K] { def key: K }

object SyncEvent {

  implicit val encoder: Encoder[SyncEvent] =
    Encoder.instance {
      case re: ReportableEvent  => re.asJson
      case we: WorkableEvent    => we.asJson
      case te: TriggerableEvent => te.asJson
      case ne: FlaggedEvent     => ne.asJson
    }

  implicit val decoder: Decoder[SyncEvent] =
    Decoder[ReportableEvent]
      .map[SyncEvent](identity)
      .or(Decoder[WorkableEvent].map[SyncEvent](identity))
      .or(Decoder[TriggerableEvent].map[SyncEvent](identity))
      .or(Decoder[FlaggedEvent].map[SyncEvent](identity))

  def apply(
      accountId: UUID,
      syncId: UUID,
      status: Status,
      payload: SyncEvent.Payload
  ): SyncEvent =
    status match {
      case ws: WorkableStatus    => WorkableEvent(accountId, syncId, ws, payload)
      case rs: ReportableStatus  => ReportableEvent(accountId, syncId, rs, payload)
      case ts: TriggerableStatus => TriggerableEvent(accountId, syncId, ts, payload)
      case ns: FlaggedStatus     => FlaggedEvent(accountId, syncId, ns, payload)
    }

  case class Payload(
      account: AccountIdentifier,
      data: Json = Json.obj() // TODO: type it per coin and type of event?
  )

  object Payload {
    implicit val encoder: Encoder[Payload] = deriveConfiguredEncoder[Payload]
    implicit val decoder: Decoder[Payload] = deriveConfiguredDecoder[Payload]
  }

}

case class WorkableEvent(
    accountId: UUID,
    syncId: UUID,
    status: WorkableStatus,
    payload: SyncEvent.Payload
) extends SyncEvent {
  def asPublished: FlaggedEvent =
    FlaggedEvent(accountId, syncId, Status.Published, payload)

  def reportSuccess(data: Json): ReportableEvent =
    ReportableEvent(accountId, syncId, status.success, payload.copy(data = data.deepDropNullValues))

  def reportFailure(data: Json): ReportableEvent = {
    val updatedPayloadData = payload.data.deepMerge(data).deepDropNullValues
    ReportableEvent(accountId, syncId, status.failure, payload.copy(data = updatedPayloadData))
  }
}

object WorkableEvent {
  implicit val encoder: Encoder[WorkableEvent] = deriveConfiguredEncoder[WorkableEvent]
  implicit val decoder: Decoder[WorkableEvent] = deriveConfiguredDecoder[WorkableEvent]
}

case class ReportableEvent(
    accountId: UUID,
    syncId: UUID,
    status: ReportableStatus,
    payload: SyncEvent.Payload
) extends SyncEvent

object ReportableEvent {
  implicit val encoder: Encoder[ReportableEvent] = deriveConfiguredEncoder[ReportableEvent]
  implicit val decoder: Decoder[ReportableEvent] = deriveConfiguredDecoder[ReportableEvent]
}

case class TriggerableEvent(
    accountId: UUID,
    syncId: UUID,
    status: TriggerableStatus,
    payload: SyncEvent.Payload
) extends SyncEvent {
  def nextWorkable: WorkableEvent =
    WorkableEvent(accountId, UUID.randomUUID(), status.nextWorkable, payload)
}

object TriggerableEvent {
  implicit val encoder: Encoder[TriggerableEvent] = deriveConfiguredEncoder[TriggerableEvent]
  implicit val decoder: Decoder[TriggerableEvent] = deriveConfiguredDecoder[TriggerableEvent]
}

case class FlaggedEvent(
    accountId: UUID,
    syncId: UUID,
    status: FlaggedStatus,
    payload: SyncEvent.Payload
) extends SyncEvent

object FlaggedEvent {
  implicit val encoder: Encoder[FlaggedEvent] = deriveConfiguredEncoder[FlaggedEvent]
  implicit val decoder: Decoder[FlaggedEvent] = deriveConfiguredDecoder[FlaggedEvent]
}
