package co.ledger.lama.bitcoin.api

import co.ledger.lama.common.services.RabbitNotificationService
import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.api.middlewares.LoggingMiddleware._
import co.ledger.lama.bitcoin.interpreter.protobuf.BitcoinInterpreterServiceFs2Grpc
import co.ledger.lama.common.protobuf.HealthFs2Grpc
import co.ledger.lama.common.utils.RabbitUtils
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import co.ledger.lama.manager.protobuf.AccountManagerServiceFs2Grpc
import Config.Config
import co.ledger.lama.bitcoin.api.routes.{AccountController, HealthController}
import co.ledger.protobuf.bitcoin.keychain.KeychainServiceFs2Grpc
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import io.grpc.Metadata
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

object App extends IOApp {

  case class ServiceResources(
      rabbitClient: RabbitClient[IO],
      grpcAccountManagerHealthClient: HealthFs2Grpc[IO, Metadata],
      grpcBitcoinInterpreterHealthClient: HealthFs2Grpc[IO, Metadata],
      grpcBitcoinBroadcasterHealthClient: HealthFs2Grpc[IO, Metadata],
      grpcAccountClient: AccountManagerServiceFs2Grpc[IO, Metadata],
      grpcKeychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      grpcBitcoinInterpreterClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata]
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {

      rabbitClient <- RabbitUtils.createClient(conf.rabbit)

      grpcAccountManagerClient <-
        grpcManagedChannel(conf.accountManager).map(AccountManagerServiceFs2Grpc.stub[IO](_))

      grpcAccountManagerHealthClient <-
        grpcManagedChannel(conf.accountManager).map(HealthFs2Grpc.stub[IO](_))

      grpcKeychainClient <-
        grpcManagedChannel(conf.bitcoin.keychain).map(KeychainServiceFs2Grpc.stub[IO](_))

      grpcBitcoinInterpreterClient <- grpcManagedChannel(conf.bitcoin.interpreter)
        .map(BitcoinInterpreterServiceFs2Grpc.stub[IO](_))

      grpcBitcoinInterpreterHealthClient <- grpcManagedChannel(conf.bitcoin.interpreter)
        .map(HealthFs2Grpc.stub[IO](_))

      grpcBitcoinBroadcasterHealthClient <- grpcManagedChannel(conf.bitcoin.broadcaster)
        .map(HealthFs2Grpc.stub[IO](_))

    } yield ServiceResources(
      rabbitClient = rabbitClient,
      grpcAccountManagerHealthClient = grpcAccountManagerHealthClient,
      grpcBitcoinInterpreterHealthClient = grpcBitcoinInterpreterHealthClient,
      grpcBitcoinBroadcasterHealthClient = grpcBitcoinBroadcasterHealthClient,
      grpcAccountClient = grpcAccountManagerClient,
      grpcKeychainClient = grpcKeychainClient,
      grpcBitcoinInterpreterClient = grpcBitcoinInterpreterClient
    )

    resources.use { serviceResources =>
      val notificationService = new RabbitNotificationService(
        serviceResources.rabbitClient,
        conf.lamaNotificationsExchangeName,
        conf.maxConcurrent
      )

      val httpRoutes = Router[IO](
        "accounts" -> loggingMiddleWare(
          AccountController.routes(
            notificationService,
            serviceResources.grpcKeychainClient,
            serviceResources.grpcAccountClient,
            serviceResources.grpcBitcoinInterpreterClient
          )
        ),
        "_health" -> HealthController.routes(
          serviceResources.grpcAccountManagerHealthClient,
          serviceResources.grpcBitcoinInterpreterHealthClient,
          serviceResources.grpcBitcoinBroadcasterHealthClient
        )
      ).orNotFound

      BlazeServerBuilder[IO](ExecutionContext.global)
        .bindHttp(conf.server.port, conf.server.host)
        .withHttpApp(httpRoutes)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    }
  }

}
