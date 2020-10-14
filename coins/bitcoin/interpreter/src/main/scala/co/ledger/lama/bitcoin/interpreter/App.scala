package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.utils.HealthUtils
import co.ledger.lama.common.utils.ResourceUtils.{grpcServer, postgresTransactor}
import pureconfig.ConfigSource
import fs2.Stream

object App extends IOApp with IOLogging {
  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    log.info("Instantiating resources")

    val resources = for {
      db <- postgresTransactor(conf.postgres)

      _ = log.info("DB instantiated")

      serviceDefinitions = List(
        new DbInterpreter(db).definition,
        new HealthUtils().definition
      )

      grpcServer <- grpcServer(conf.grpcServer, serviceDefinitions)

      _ = log.info("GRPC server instantiated")
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
