package co.ledger.lama.bitcoin.api.routes

import cats.effect.IO
import co.ledger.lama.common.protobuf._
import co.ledger.lama.common.protobuf.HealthCheckResponse._
import io.grpc.Metadata
import org.http4s.rho.RhoRoutes
import org.http4s.rho.swagger.SwaggerSyntax

class HealthController(
    accountManagerHealthClient: HealthFs2Grpc[IO, Metadata],
    interpreterHealthClient: HealthFs2Grpc[IO, Metadata],
    broadcasterHealthClient: HealthFs2Grpc[IO, Metadata],
    swaggerSyntax: SwaggerSyntax[IO]
) extends RhoRoutes[IO] {

  import swaggerSyntax._

  "Returns GRPC health status" **
    List("utils") @@
    GET |>>
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
    }).flatMap(_ => Ok(()))
}
