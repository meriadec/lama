package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.common.grpc.HealthService
import co.ledger.lama.common.utils.ResourceUtils.{grpcServer, postgresTransactor}
import pureconfig.ConfigSource
import fs2.Stream

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      db <- postgresTransactor(conf.postgres)

      serviceDefinitions = List(
        new DbInterpreter(db, conf.maxConcurrent).definition,
        new HealthService().definition
      )

      grpcServer <- grpcServer(conf.grpcServer, serviceDefinitions)

    } yield grpcServer

    Stream
      .resource(resources)
      .evalMap(server => IO(server.start())) // start server
      .evalMap(_ => IO.never)
      .compile
      .drain
      .as(ExitCode.Success)

  }
}
