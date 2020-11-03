package co.ledger.lama.bitcoin.api.routes

import cats.effect.IO
import co.ledger.lama.common.protobuf._
import co.ledger.lama.common.protobuf.HealthCheckResponse._
import co.ledger.lama.common.logging.IOLogging
import io.grpc.Metadata
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object HealthController extends Http4sDsl[IO] with IOLogging {

  def routes(
      accountManagerHealthClient: HealthFs2Grpc[IO, Metadata],
      interpreterHealthClient: HealthFs2Grpc[IO, Metadata],
      broadcasterHealthClient: HealthFs2Grpc[IO, Metadata]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root =>
      (for {
        interpreterHealth <- interpreterHealthClient.check(new HealthCheckRequest(), new Metadata)
        accountManagerHealth <-
          accountManagerHealthClient.check(new HealthCheckRequest(), new Metadata)
        broadcasterHealth <-
          broadcasterHealthClient.check(new HealthCheckRequest(), new Metadata)
      } yield {
        accountManagerHealth.status == ServingStatus.SERVING &&
        interpreterHealth.status == ServingStatus.SERVING &&
        broadcasterHealth.status == ServingStatus.SERVING
      }).flatMap(_ => Ok())
    }
}
