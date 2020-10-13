package co.ledger.lama.service

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.interpreter.protobuf.BitcoinInterpreterServiceFs2Grpc
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import co.ledger.lama.manager.protobuf.AccountManagerServiceFs2Grpc
import co.ledger.lama.service.Config.Config
import co.ledger.lama.service.routes.AccountController
import co.ledger.protobuf.bitcoin.KeychainServiceFs2Grpc
import io.grpc.Metadata
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import pureconfig.ConfigSource

object App extends IOApp with IOLogging {

  case class ServiceResources(
      grpcAccountClient: AccountManagerServiceFs2Grpc[IO, Metadata],
      grpcKeychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      grpcBitcoinInterpreterClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata]
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]
    log.info("Instantiating resources")
    val resources = for {
      grpcAccountManagerClient <-
        grpcManagedChannel(conf.accountManager).map(AccountManagerServiceFs2Grpc.stub[IO](_))

      _ = log.info("Account Manager GRPC client instantiated")

      grpcKeychainClient <-
        grpcManagedChannel(conf.bitcoin.keychain).map(KeychainServiceFs2Grpc.stub[IO](_))

      _ = log.info("Keychain GRPC client instantiated")

      grpcBitcoinInterpreterClient <- grpcManagedChannel(conf.bitcoin.interpreter)
        .map(BitcoinInterpreterServiceFs2Grpc.stub[IO](_))

      _ = log.info("Bitcoin GRPC client instantiated")
    } yield ServiceResources(
      grpcAccountManagerClient,
      grpcKeychainClient,
      grpcBitcoinInterpreterClient
    )

    resources.use { serviceResources =>
      log.info("Instantiating routes")

      val httpRoutes = Router[IO](
        "/" -> AccountController.routes(
          serviceResources.grpcKeychainClient,
          serviceResources.grpcAccountClient,
          serviceResources.grpcBitcoinInterpreterClient
        )
      ).orNotFound

      log.info("Instantiating server")

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
