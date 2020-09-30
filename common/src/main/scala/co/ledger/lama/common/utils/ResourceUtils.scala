package co.ledger.lama.common.utils

import cats.effect.{Async, Blocker, ContextShift, IO, Resource, Timer}
import io.grpc.{
  ManagedChannel,
  ManagedChannelBuilder,
  Server,
  ServerBuilder,
  ServerServiceDefinition
}
import org.lyranthe.fs2_grpc.java_runtime.implicits._
import doobie.hikari.HikariTransactor

import scala.concurrent.duration._
import doobie.ExecutionContexts
import fs2.{Pure, Stream}

object ResourceUtils {

  def retriableResource[F[_], O](
      resource: Resource[F, O],
      policy: RetryPolicy = RetryPolicy.linear()
  )(implicit
      T: Timer[F],
      F: Async[F]
  ): Resource[F, O] =
    Stream
      .resource(resource)
      .attempts(policy)
      .evalTap {
        case Left(value) => F.delay(println(s"Resource acquisition Failed : $value"))
        case Right(_)    => F.unit
      }
      .collectFirst {
        case Right(res) => res
      }
      .compile
      .resource
      .lastOrError

  type RetryPolicy = Stream[Pure, FiniteDuration]

  object RetryPolicy {
    def linear(delay: FiniteDuration = 1.second, maxRetry: Int = 20): RetryPolicy =
      Stream.emit(delay).repeatN(maxRetry)
    def exponential(
        initial: FiniteDuration = 50.millisecond,
        factor: Long = 2,
        maxElapsedTime: FiniteDuration = 2.minute
    ): RetryPolicy = Stream.iterate(initial)(_ * factor).takeWhile(_ < maxElapsedTime)
  }

  def postgresTransactor(
      conf: PostgresConfig
  )(implicit contextShift: ContextShift[IO], timer: Timer[IO]): Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](conf.poolSize)

      te <- ExecutionContexts.cachedThreadPool[IO]

      db <- retriableResource(
        HikariTransactor.newHikariTransactor[IO](
          conf.driver,                     // driver classname
          conf.url,                        // connect URL
          conf.user,                       // username
          conf.password,                   // password
          ce,                              // await connection here
          Blocker.liftExecutionContext(te) // execute JDBC operations here
        )
      )
    } yield db

  def grpcServer(
      conf: GrpcServerConfig,
      services: List[ServerServiceDefinition]
  ): Resource[IO, Server] =
    services
      .foldLeft(ServerBuilder.forPort(conf.port)) {
        case (builder, service) =>
          builder.addService(service)
      }
      .resource[IO]

  def grpcManagedChannel(conf: GrpcClientConfig): Resource[IO, ManagedChannel] =
    ManagedChannelBuilder
      .forAddress(conf.host, conf.port)
      .resource[IO]
}
