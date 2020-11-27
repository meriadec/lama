package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.lama.bitcoin.common.services.{
  ExplorerClient,
  InterpreterClientService,
  KeychainClientService
}
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.services.{CursorStateService, SyncEventService}
import co.ledger.lama.common.models.{
  AccountIdentifier,
  Coin,
  CoinFamily,
  ReportableEvent,
  Status,
  SyncEvent,
  WorkableEvent
}
import co.ledger.lama.common.utils.RabbitUtils
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.ExchangeType
import fs2.Stream
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

trait WorkerResources {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config                                   = ConfigSource.default.loadOrThrow[Config]
  def rabbitResource: Resource[IO, RabbitClient[IO]] = Clients.rabbit(conf.rabbit)

  val keychainId: UUID = UUID.randomUUID()
  val account: AccountIdentifier =
    AccountIdentifier(keychainId.toString, CoinFamily.Bitcoin, Coin.Btc)
  val syncId: UUID = UUID.randomUUID()

  val registeredEvent: WorkableEvent =
    WorkableEvent(account.id, syncId, Status.Registered, SyncEvent.Payload(account))

  def runWorkerWorkflow(
      keychainClient: KeychainClientService,
      explorerClient: ExplorerClient,
      interpreterClient: InterpreterClientService,
      cursorStateService: CursorStateService
  ): IO[Option[ReportableEvent]] =
    rabbitResource.use { rabbitClient =>
      setupRabbit(rabbitClient) *> {
        val syncEventService = new SyncEventService(
          rabbitClient,
          conf.queueName(conf.workerEventsExchangeName),
          conf.lamaEventsExchangeName,
          conf.routingKey
        )

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

        Stream
          .eval {
            accountManager.publishWorkableEvent(registeredEvent) *>
              accountManager.consumeReportableEvent
          }
          .concurrently(worker.run)
          .take(1)
          .compile
          .last
      }
    }

  def setupRabbit(rabbitClient: RabbitClient[IO]): IO[Unit] =
    for {
      _ <- RabbitUtils.deleteBindings(
        rabbitClient,
        List(
          conf.queueName(conf.workerEventsExchangeName),
          conf.queueName(conf.lamaEventsExchangeName)
        )
      )
      _ <- RabbitUtils.deleteExchanges(
        rabbitClient,
        List(conf.workerEventsExchangeName, conf.lamaEventsExchangeName)
      )
      _ <- RabbitUtils.declareExchanges(
        rabbitClient,
        List(
          (conf.workerEventsExchangeName, ExchangeType.Topic),
          (conf.lamaEventsExchangeName, ExchangeType.Topic)
        )
      )
      res <- RabbitUtils.declareBindings(
        rabbitClient,
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
