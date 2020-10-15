package co.ledger.lama.service

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.interpreter.protobuf.BitcoinInterpreterServiceFs2Grpc
import co.ledger.lama.common.health.protobuf.HealthFs2Grpc
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import co.ledger.lama.manager.protobuf.AccountManagerServiceFs2Grpc
import co.ledger.lama.service.Config.Config
import co.ledger.lama.service.routes._
import co.ledger.protobuf.bitcoin.KeychainServiceFs2Grpc
import io.grpc.Metadata
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import pureconfig.ConfigSource

object App extends IOApp {

  case class ServiceResources(
      grpcAccountManagerHealthClient: HealthFs2Grpc[IO, Metadata],
      grpcBitcoinInterpreterHealthClient: HealthFs2Grpc[IO, Metadata],
      grpcAccountClient: AccountManagerServiceFs2Grpc[IO, Metadata],
      grpcKeychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      grpcBitcoinInterpreterClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata]
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {

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

    } yield ServiceResources(
      grpcAccountManagerHealthClient = grpcAccountManagerHealthClient,
      grpcBitcoinInterpreterHealthClient = grpcBitcoinInterpreterHealthClient,
      grpcAccountClient = grpcAccountManagerClient,
      grpcKeychainClient = grpcKeychainClient,
      grpcBitcoinInterpreterClient = grpcBitcoinInterpreterClient
    )

    resources.use { serviceResources =>
      val httpRoutes = Router[IO](
        "accounts" -> AccountController.routes(
          serviceResources.grpcKeychainClient,
          serviceResources.grpcAccountClient,
          serviceResources.grpcBitcoinInterpreterClient
        ),
        "health" -> HealthController.routes(
          serviceResources.grpcAccountManagerHealthClient,
          serviceResources.grpcBitcoinInterpreterHealthClient
        )
      ).orNotFound

      BlazeServerBuilder[IO]
        .bindHttp(conf.server.port, conf.server.host)
        .withHttpApp(httpRoutes)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    }
  }

}
