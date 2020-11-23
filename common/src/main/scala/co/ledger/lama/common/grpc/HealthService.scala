package co.ledger.lama.common.grpc

import cats.effect.{ConcurrentEffect, IO}
import io.grpc.{Metadata, ServerServiceDefinition}

import co.ledger.protobuf.lama.common._
import co.ledger.protobuf.lama.common.HealthCheckResponse._

class HealthService extends HealthFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    HealthFs2Grpc.bindService(this)

  def check(request: HealthCheckRequest, ctx: Metadata): IO[HealthCheckResponse] =
    IO.pure(HealthCheckResponse(ServingStatus.SERVING))

  def watch(request: HealthCheckRequest, ctx: Metadata): fs2.Stream[IO, HealthCheckResponse] =
    fs2.Stream(HealthCheckResponse(ServingStatus.SERVING))
}
