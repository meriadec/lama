package co.ledger.lama.common.models

import java.util.UUID

import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.extras.semiauto._
import io.circe.syntax._

trait Notification {
  val accountId: UUID
  val coinFamily: CoinFamily
  val coin: Coin
  val status: Notification.Status
  val payload: Json
}

case class TransactionNotification(
    accountId: UUID,
    coinFamily: CoinFamily,
    coin: Coin,
    transaction: Json
) extends Notification {
  val status: Notification.Status = Notification.Transaction
  val payload: Json               = Json.obj("transaction" -> transaction)
}

object TransactionNotification {
  implicit val encoder: Encoder[TransactionNotification] =
    deriveConfiguredEncoder[TransactionNotification]
  implicit val decoder: Decoder[TransactionNotification] =
    deriveConfiguredDecoder[TransactionNotification]

}

case class OperationsComputedNotification(
    accountId: UUID,
    coinFamily: CoinFamily,
    coin: Coin,
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

case class BalanceUpdatedNotification(
    accountId: UUID,
    coinFamily: CoinFamily,
    coin: Coin,
    balance: BigInt
) extends Notification {
  val status: Notification.Status = Notification.BalanceUpdated
  val payload: Json               = Json.obj("new_balance" -> Json.fromBigInt(balance))
}

object BalanceUpdatedNotification {
  implicit val encoder: Encoder[BalanceUpdatedNotification] =
    deriveConfiguredEncoder[BalanceUpdatedNotification]
  implicit val decoder: Decoder[BalanceUpdatedNotification] =
    deriveConfiguredDecoder[BalanceUpdatedNotification]
}

object Notification {
  implicit val encoder: Encoder[Notification] = Encoder.instance {
    case x: OperationsComputedNotification => x.asJson
    case x: BalanceUpdatedNotification     => x.asJson
    case x: TransactionNotification        => x.asJson
  }
  implicit val decoder: Decoder[Notification] =
    Decoder[OperationsComputedNotification]
      .map[Notification](identity)
      .or(Decoder[BalanceUpdatedNotification].map[Notification](identity))
      .or(Decoder[TransactionNotification].map[Notification](identity))

  abstract class Status(val name: String)

  // OperationsComputed event sent when account operations are computed
  // The payload should be the count of operations computed
  case object OperationsComputed extends Status(name = "operations_computed")
  case object BalanceUpdated     extends Status(name = "balance_updated")
  case object Transaction        extends Status(name = "transaction")

  object Status {
    val all: Map[String, Status] =
      Map(
        OperationsComputed.name -> OperationsComputed,
        BalanceUpdated.name     -> BalanceUpdated,
        Transaction.name        -> Transaction
      )

    def fromKey(key: String): Option[Status] = all.get(key)

    implicit val encoder: Encoder[Status] = Encoder.encodeString.contramap(_.name)

    implicit val decoder: Decoder[Status] =
      Decoder.decodeString.emap(fromKey(_).toRight("unable to decode status"))
  }
}
