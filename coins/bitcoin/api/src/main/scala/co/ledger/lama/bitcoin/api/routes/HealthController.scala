package co.ledger.lama.bitcoin.api.routes

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.api.endpoints.HealthEndpoints.health
import co.ledger.lama.common.protobuf._
import co.ledger.lama.common.protobuf.HealthCheckResponse._
import co.ledger.lama.common.logging.IOLogging
import io.grpc.Metadata
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s._

import scala.concurrent.ExecutionContext

object HealthController extends IOLogging {
  implicit val ec: ExecutionContext           = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO]               = IO.timer(ec)

  def routes(
      accountManagerHealthClient: HealthFs2Grpc[IO, Metadata],
      interpreterHealthClient: HealthFs2Grpc[IO, Metadata],
      broadcasterHealthClient: HealthFs2Grpc[IO, Metadata]
  ): HttpRoutes[IO] =
    health.toRoutes(_ =>
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
      }).map(res => {
        if (res) {
          Right[Unit, Unit]()
        } else {
          Left[Unit, Unit]()
        }
      })
    )

}
