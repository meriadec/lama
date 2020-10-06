package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.common.utils.ResourceUtils.{grpcServer, postgresTransactor}
import pureconfig.ConfigSource

object App extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {

      db <- postgresTransactor(conf.postgres)

      serviceDefinitions = List(
        new DbInterpreter(db).definition
      )

      grpcServer <- grpcServer(conf.grpcServer, serviceDefinitions)
    } yield grpcServer

    resources
      .use(server => IO(server.start()))
      .flatMap(_ => IO.never)
      .as(ExitCode.Success)
  }
}
