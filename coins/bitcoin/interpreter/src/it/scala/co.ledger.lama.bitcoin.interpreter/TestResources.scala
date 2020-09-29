package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.lama.common.utils.ResourceUtils
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

trait TestResources {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  private val dbUrl      = conf.postgres.url
  private val dbUser     = conf.postgres.user
  private val dbPassword = conf.postgres.password

  private val flyway: Flyway = Flyway
    .configure()
    .dataSource(dbUrl, dbUser, dbPassword)
    .locations(s"classpath:/db/migration")
    .load

  def setup(): IO[Unit] = IO(flyway.clean()) *> IO(flyway.migrate())

  def appResources: Resource[IO, Transactor[IO]] =
    ResourceUtils.postgresTransactor(conf.postgres)

}
