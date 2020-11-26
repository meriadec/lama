package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.lama.bitcoin.common.services.ExplorerClientService
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.mock.faulty.FaultyInterpreterClientServiceMock
import co.ledger.lama.bitcoin.worker.mock.KeychainClientServiceMock
import co.ledger.lama.bitcoin.worker.models.GetConfirmedTransactionsFailed
import co.ledger.lama.bitcoin.worker.services.{CursorStateService, SyncEventService}
import co.ledger.lama.common.models.Status.SyncFailed
import co.ledger.lama.common.models._
import co.ledger.lama.common.utils.{IOAssertion, RabbitUtils}
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.ExchangeType
import fs2.Stream
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

class WorkerWithFaultyInterpreterIT extends AnyFlatSpecLike with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  val rabbit: Resource[IO, RabbitClient[IO]] = Clients.rabbit(conf.rabbit)

  val resources = for {
    rabbitClient <- rabbit
    httpClient   <- Clients.htt4s
  } yield (rabbitClient, httpClient)

  IOAssertion {
    setupRabbit() *>
      resources
        .use { case (rabbitClient, httpClient) =>
          val syncEventService = new SyncEventService(
            rabbitClient,
            conf.queueName(conf.workerEventsExchangeName),
            conf.lamaEventsExchangeName,
            conf.routingKey
          )

          val keychainClient = new KeychainClientServiceMock

          val explorerClient = new ExplorerClientService(httpClient, conf.explorer)

          val interpreterClient = new FaultyInterpreterClientServiceMock

          val cursorStateService = new CursorStateService(explorerClient, interpreterClient)

          val worker = new Worker(
            syncEventService,
            keychainClient,
            explorerClient,
            interpreterClient,
            cursorStateService,
            conf
          )

          val accountManager = new SimpleAccountManager(
            rabbitClient,
            conf.queueName(conf.lamaEventsExchangeName),
            conf.workerEventsExchangeName,
            conf.routingKey
          )

          val keychainId = UUID.randomUUID()

          val account = AccountIdentifier(keychainId.toString, CoinFamily.Bitcoin, Coin.Btc)

          val syncId = UUID.randomUUID()

          val registeredEvent =
            WorkableEvent(account.id, syncId, Status.Registered, SyncEvent.Payload(account))

          Stream
            .eval {
              accountManager.publishWorkableEvent(registeredEvent) *>
                accountManager.consumeReportableEvent
            }
            .concurrently(worker.run)
            .take(1)
            .compile
            .last
            .map { reportableEvent =>
              it should "report an inability to fetch confirmed transactions" in {
                for {
                  re <- reportableEvent
                } yield {
                  re.status shouldBe SyncFailed
                  re.payload.data shouldBe GetConfirmedTransactionsFailed(keychainId).errorMessage
                }
              }
            }
        }
  }

  def setupRabbit(): IO[Unit] =
    rabbit.use { client =>
      for {
        _ <- RabbitUtils.deleteBindings(
          client,
          List(
            conf.queueName(conf.workerEventsExchangeName),
            conf.queueName(conf.lamaEventsExchangeName)
          )
        )
        _ <- RabbitUtils.deleteExchanges(
          client,
          List(conf.workerEventsExchangeName, conf.lamaEventsExchangeName)
        )
        _ <- RabbitUtils.declareExchanges(
          client,
          List(
            (conf.workerEventsExchangeName, ExchangeType.Topic),
            (conf.lamaEventsExchangeName, ExchangeType.Topic)
          )
        )
        res <- RabbitUtils.declareBindings(
          client,
          List(
            (
              conf.workerEventsExchangeName,
              conf.routingKey,
              conf.queueName(conf.workerEventsExchangeName)
            ),
            (
              conf.lamaEventsExchangeName,
              conf.routingKey,
              conf.queueName(conf.lamaEventsExchangeName)
            )
          )
        )
      } yield res
    }
}
