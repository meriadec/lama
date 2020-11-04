package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.lama.common.models._
import co.ledger.lama.common.services.RabbitNotificationService
import co.ledger.lama.common.utils.{IOAssertion, RabbitUtils}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import fs2.Stream
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

class NotificationServiceIT extends AnyFlatSpecLike with Matchers {

  def consumeNotification[T <: Notification](consumer: Stream[IO, T]): IO[T] =
    consumer.take(1).compile.last.map(_.get)

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config                           = ConfigSource.default.loadOrThrow[Config]
  val rabbit: Resource[IO, RabbitClient[IO]] = RabbitUtils.createClient(conf.rabbit)

  IOAssertion {
    rabbit
      .use { rabbitClient =>
        val notificationService =
          new RabbitNotificationService(rabbitClient, conf.lamaNotificationsExchangeName)

        val account: AccountIdentifier =
          AccountIdentifier("testKey", CoinFamily.Bitcoin, Coin.Btc)
        val computedOperations = 4
        val operationsComputedNotification =
          OperationsComputedNotification(account, computedOperations)

        val consumer = RabbitUtils
          .createAutoAckConsumer[OperationsComputedNotification](
            rabbitClient,
            notificationService.queueName(account)
          )

        for {
          _     <- notificationService.deleteQueue(account)
          _     <- notificationService.createQueue(account)
          _     <- notificationService.notify(operationsComputedNotification)
          notif <- consumeNotification[OperationsComputedNotification](consumer)
        } yield {
          it should "contain the pushed notification" in {
            notif.account shouldBe account
            notif.operationsCount shouldBe computedOperations
          }
        }
      }
  }
}
