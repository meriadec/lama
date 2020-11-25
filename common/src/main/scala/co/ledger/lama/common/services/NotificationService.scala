package co.ledger.lama.common.services

import java.util.UUID

import cats.effect.{ContextShift, IO}
import io.circe.syntax._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{Coin, CoinFamily, Notification}
import co.ledger.lama.common.utils.RabbitUtils
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import fs2.{Pipe, Stream}

trait NotificationService {
  def notify(notification: Notification): IO[Unit]
  def notifySink(implicit cs: ContextShift[IO]): Pipe[IO, Notification, Unit]
  def createQueue(accountId: UUID, coinFamily: CoinFamily, coin: Coin): IO[Unit]
  def deleteQueue(accountId: UUID, coinFamily: CoinFamily, coin: Coin)(implicit
      cs: ContextShift[IO]
  ): IO[Unit]
}

class RabbitNotificationService(
    val rabbitClient: RabbitClient[IO],
    val exchangeName: ExchangeName,
    maxConcurrent: Int
) extends IOLogging
    with NotificationService {

  def createQueue(accountId: UUID, coinFamily: CoinFamily, coin: Coin): IO[Unit] =
    rabbitClient.createConnectionChannel.use { implicit channel =>
      val queue = RabbitNotificationService.queueName(exchangeName, accountId, coinFamily, coin)
      rabbitClient.declareExchange(exchangeName, ExchangeType.Topic) *>
        rabbitClient.declareQueue(
          DeclarationQueueConfig.default(queue)
        ) *>
        rabbitClient.bindQueue(
          queue,
          exchangeName,
          RabbitNotificationService.routingKey(accountId, coinFamily, coin)
        )
    }.void

  def deleteQueue(accountId: UUID, coinFamily: CoinFamily, coin: Coin)(implicit
      cs: ContextShift[IO]
  ): IO[Unit] =
    RabbitUtils.deleteBindings(rabbitClient, List(RabbitNotificationService.queueName(exchangeName, accountId, coinFamily, coin)))

  private def publisher(routingKey: RoutingKey): Stream[IO, Notification => IO[Unit]] =
    RabbitUtils
      .createPublisher[Notification](rabbitClient, exchangeName, routingKey)

  def notify(notification: Notification): IO[Unit] =
    publisher(RabbitNotificationService.routingKey(notification.accountId, notification.coinFamily, notification.coin))
      .evalMap(p =>
        p(notification) *> log.info(s"Published notification: ${notification.asJson.toString}")
      )
      .compile
      .drain

  def notifySink(implicit cs: ContextShift[IO]): Pipe[IO, Notification, Unit] =
    _.parEvalMap(maxConcurrent)(notify)

}

object RabbitNotificationService {

  def queueName(exchange: ExchangeName, accountId: UUID, coinFamily: CoinFamily, coin: Coin): QueueName =
    QueueName(
      s"${exchange.value}.${routingKey(accountId, coinFamily, coin).value}"
    )

  private def routingKey(accountId: UUID, coinFamily: CoinFamily, coin: Coin): RoutingKey =
    RoutingKey(s"$coinFamily.$coin.$accountId")

}
