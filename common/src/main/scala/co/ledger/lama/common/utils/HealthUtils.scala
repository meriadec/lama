package co.ledger.lama.common.utils

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.common.health.protobuf.HealthCheckResponse.ServingStatus
import co.ledger.lama.common.health.protobuf._
import io.grpc.{Metadata, ServerServiceDefinition}

trait Health extends HealthFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    HealthFs2Grpc.bindService(this)
}

class HealthUtils extends Health {
  def check(request: HealthCheckRequest, ctx: Metadata): IO[HealthCheckResponse] =
    IO.pure(HealthCheckResponse(ServingStatus.SERVING))

  def watch(request: HealthCheckRequest, ctx: Metadata): fs2.Stream[IO, HealthCheckResponse] =
    fs2.Stream(HealthCheckResponse(ServingStatus.SERVING))
}
