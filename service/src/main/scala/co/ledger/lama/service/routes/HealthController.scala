package co.ledger.lama.service.routes

import cats.effect.IO
import co.ledger.lama.common.health.protobuf.HealthCheckResponse.ServingStatus
import co.ledger.lama.common.health.protobuf._
import co.ledger.lama.common.logging.IOLogging
import io.grpc.Metadata
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object HealthController extends Http4sDsl[IO] with IOLogging {

  def routes(
      accountManagerHealthClient: HealthFs2Grpc[IO, Metadata],
      interpreterHealthClient: HealthFs2Grpc[IO, Metadata]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root =>
        val tmp = for {
          interpreterHealth <- interpreterHealthClient.check(new HealthCheckRequest(), new Metadata)
          accountManagerHealth <-
            accountManagerHealthClient.check(new HealthCheckRequest(), new Metadata)
        } yield interpreterHealth.status == ServingStatus.SERVING && accountManagerHealth.status == ServingStatus.SERVING
        tmp.flatMap(_ => Ok())
    }
}
