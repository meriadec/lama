package co.ledger.lama.bitcoin.broadcaster

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.common.grpc.HealthService
import co.ledger.lama.common.utils.ResourceUtils
import pureconfig.ConfigSource
import fs2._

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val serviceDefinitions = List(new HealthService().definition)

    Stream
      .resource(ResourceUtils.grpcServer(conf.grpcServer, serviceDefinitions))
      .evalMap(server => IO(server.start()))
      .evalMap(_ => IO.never)
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
