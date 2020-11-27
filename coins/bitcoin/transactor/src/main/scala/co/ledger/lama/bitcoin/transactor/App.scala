package co.ledger.lama.bitcoin.transactor

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.common.services.InterpreterGrpcClientService
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.bitcoin.transactor.services.BitcoinLibGrpcClientService
import co.ledger.lama.common.grpc.HealthService
import co.ledger.lama.common.utils.ResourceUtils
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import co.ledger.protobuf.bitcoin.libgrpc
import pureconfig.ConfigSource
import fs2._

object App extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {

      grpcBitcoinInterpreterClient <- grpcManagedChannel(conf.interpreter).map(
        protobuf.BitcoinInterpreterServiceFs2Grpc.stub[IO](_)
      )

      grpcBitcoinLibClient <- grpcManagedChannel(conf.bitcoinLib).map(
        libgrpc.CoinServiceFs2Grpc.stub[IO](_)
      )

      interpreterService = new InterpreterGrpcClientService(grpcBitcoinInterpreterClient)
      bitcoinLib         = new BitcoinLibGrpcClientService(grpcBitcoinLibClient)

      serviceDefinitions = List(
        new HealthService().definition
      )

      grcpService <- ResourceUtils.grpcServer(conf.grpcServer, serviceDefinitions)

    } yield grcpService

    Stream
      .resource(resources)
      .evalMap(server => IO(server.start()))
      .evalMap(_ => IO.never)
      .compile
      .drain
      .as(ExitCode.Success)
  }

}
