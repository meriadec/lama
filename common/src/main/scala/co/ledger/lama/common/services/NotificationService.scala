package co.ledger.lama.common.services

import cats.effect.{ContextShift, IO}
import io.circe.syntax._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{AccountIdentifier, Notification}
import co.ledger.lama.common.utils.RabbitUtils
import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, ExchangeType, QueueName, RoutingKey}
import fs2.Stream

trait NotificationService {
  def notify(notification: Notification): IO[Unit]
  def createQueue(account: AccountIdentifier): IO[Unit]
  def deleteQueue(account: AccountIdentifier)(implicit cs: ContextShift[IO]): IO[Unit]
}

class RabbitNotificationService(
    rabbitClient: RabbitClient[IO],
    exchangeName: ExchangeName
) extends IOLogging
    with NotificationService {

  def createQueue(account: AccountIdentifier): IO[Unit] =
    rabbitClient.createConnectionChannel.use { implicit channel =>
      rabbitClient.declareExchange(exchangeName, ExchangeType.Topic) *>
        rabbitClient.declareQueue(DeclarationQueueConfig.default(queueName(account))) *>
        rabbitClient.bindQueue(queueName(account), exchangeName, routingKey(account))
    }.void

  def deleteQueue(account: AccountIdentifier)(implicit
      cs: ContextShift[IO]
  ): IO[Unit] =
    RabbitUtils.deleteBindings(rabbitClient, List(queueName(account)))

  private def publisher(routingKey: RoutingKey): Stream[IO, Notification => IO[Unit]] =
    RabbitUtils
      .createPublisher[Notification](rabbitClient, exchangeName, routingKey)

  def queueName(account: AccountIdentifier): QueueName =
    QueueName(
      s"${exchangeName.value}.${routingKey(account).value}"
    )

  def routingKey(account: AccountIdentifier): RoutingKey =
    RoutingKey(s"${account.coinFamily}.${account.coin}.${account.id}")

  def notify(notification: Notification): IO[Unit] =
    publisher(routingKey(notification.account))
      .evalMap(p =>
        p(notification) *> log.info(s"Published notification: ${notification.asJson.toString}")
      )
      .compile
      .drain
}
