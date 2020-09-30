package co.ledger.lama.bitcoin.worker

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.interpreter.protobuf.BitcoinInterpreterServiceFs2Grpc
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.services._
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import io.grpc.{ManagedChannel}
import org.http4s.client.Client
import pureconfig.ConfigSource

object App extends IOApp {

  case class WorkerResources(
      rabbitClient: RabbitClient[IO],
      httpClient: Client[IO],
      managedChannel: ManagedChannel
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      rabbitClient       <- Clients.rabbit(conf.rabbit)
      httpClient         <- Clients.htt4s
      interpreterChannel <- grpcManagedChannel(conf.interpreter)
    } yield WorkerResources(rabbitClient, httpClient, interpreterChannel)

    resources.use {
      case (workerResources: WorkerResources) =>
        val syncEventService = new SyncEventService(
          workerResources.rabbitClient,
          conf.queueName(conf.workerEventsExchangeName),
          conf.lamaEventsExchangeName,
          conf.routingKey
        )

        val keychainService = new KeychainServiceMock

        val explorerService = new ExplorerService(workerResources.httpClient, conf.explorer)

        val grpcInterpreterService =
          BitcoinInterpreterServiceFs2Grpc.stub[IO](workerResources.managedChannel)

        val interpreterService = new InterpreterGrpcClientService(grpcInterpreterService)

        val worker = new Worker(
          syncEventService,
          keychainService,
          explorerService,
          interpreterService,
          conf.maxConcurrent
        )

        worker.run.compile.lastOrError.as(ExitCode.Success)
    }
  }

}
