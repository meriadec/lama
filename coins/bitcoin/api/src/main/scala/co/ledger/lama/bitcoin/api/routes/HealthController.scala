package co.ledger.lama.bitcoin.api.routes

import cats.effect.IO
import co.ledger.lama.common.logging.IOLogging
import co.ledger.protobuf.lama.common.HealthCheckResponse.ServingStatus
import co.ledger.protobuf.lama.common.HealthFs2Grpc
import co.ledger.protobuf.lama.common._
import io.grpc.Metadata
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object HealthController extends Http4sDsl[IO] with IOLogging {

  def routes(
      accountManagerHealthClient: HealthFs2Grpc[IO, Metadata],
      interpreterHealthClient: HealthFs2Grpc[IO, Metadata],
      transactorHealthClient: HealthFs2Grpc[IO, Metadata]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root =>
      (for {
        interpreterHealth <- interpreterHealthClient.check(new HealthCheckRequest(), new Metadata)
        accountManagerHealth <-
          accountManagerHealthClient.check(new HealthCheckRequest(), new Metadata)
        transactorHealth <-
          transactorHealthClient.check(new HealthCheckRequest(), new Metadata)
      } yield {
        accountManagerHealth.status == ServingStatus.SERVING &&
        interpreterHealth.status == ServingStatus.SERVING &&
        transactorHealth.status == ServingStatus.SERVING
      }).flatMap(_ => Ok())
    }
}
