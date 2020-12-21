package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.common.services.RabbitNotificationService
import co.ledger.lama.common.services.grpc.HealthService
import co.ledger.lama.common.utils.{DbUtils, RabbitUtils}
import co.ledger.lama.common.utils.ResourceUtils.{grpcServer, postgresTransactor}
import pureconfig.ConfigSource
import fs2.Stream

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      rabbit <- RabbitUtils.createClient(conf.rabbit)

      notificationService =
        new RabbitNotificationService(
          rabbit,
          conf.lamaNotificationsExchangeName,
          conf.maxConcurrent
        )

      // create the db transactor
      db <- postgresTransactor(conf.postgres)

      // define rpc service definitions
      serviceDefinitions = List(
        new InterpreterGrpcService(
          new Interpreter(notificationService, db, conf.maxConcurrent)
        ).definition,
        new HealthService().definition
      )

      // create the grpc server
      grpcServer <- grpcServer(conf.grpcServer, serviceDefinitions)
    } yield grpcServer

    Stream
      .resource(resources)
      .evalMap { server =>
        // migrate db then start server
        DbUtils.flywayMigrate(conf.postgres) *> IO(server.start())
      }
      .evalMap(_ => IO.never)
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
