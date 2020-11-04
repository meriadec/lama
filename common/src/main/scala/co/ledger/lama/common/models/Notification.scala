package co.ledger.lama.common.models

import co.ledger.lama.common.models.implicits._
import io.circe.{Json, Decoder, Encoder}
import io.circe.generic.extras.semiauto._
import io.circe.syntax._

trait Notification {
  val account: AccountIdentifier
  val status: Notification.Status
  val payload: Json
}

case class OperationsComputedNotification(
    account: AccountIdentifier,
    operationsCount: Int
) extends Notification {
  val status: Notification.Status = Notification.OperationsComputed
  val payload: Json               = Json.obj("operations_count" -> Json.fromInt(operationsCount))
}

object OperationsComputedNotification {
  implicit val encoder: Encoder[OperationsComputedNotification] =
    deriveConfiguredEncoder[OperationsComputedNotification]
  implicit val decoder: Decoder[OperationsComputedNotification] =
    deriveConfiguredDecoder[OperationsComputedNotification]
}

object Notification {
  implicit val encoder: Encoder[Notification] = Encoder.instance {
    case x: OperationsComputedNotification => x.asJson
  }
  implicit val decoder: Decoder[Notification] =
    Decoder[OperationsComputedNotification].map(identity)

  abstract class Status(val name: String)

  // OperationsComputed event sent when account operations are computed
  // The payload should be the count of operations computed
  case object OperationsComputed extends Status(name = "operations_computed")

  object Status {
    val all: Map[String, Status] =
      Map(OperationsComputed.name -> OperationsComputed)

    def fromKey(key: String): Option[Status] = all.get(key)

    implicit val encoder: Encoder[Status] = Encoder.encodeString.contramap(_.name)

    implicit val decoder: Decoder[Status] =
      Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))
  }
}
