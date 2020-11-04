package co.ledger.lama.bitcoin.interpreter

import co.ledger.lama.common.utils.{GrpcServerConfig, PostgresConfig}
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.ExchangeName
import pureconfig.generic.semiauto.deriveReader
import cats.implicits._
import pureconfig.ConfigReader
import pureconfig.module.cats._

case class Config(
    lamaNotificationsExchangeName: ExchangeName,
    rabbit: Fs2RabbitConfig,
    postgres: PostgresConfig,
    grpcServer: GrpcServerConfig,
    maxConcurrent: Int = 50 // TODO : bench [Runtime.getRuntime.availableProcessors() * x]
)

object Config {
  implicit val exchangeNameConfigReader: ConfigReader[ExchangeName] =
    ConfigReader.fromString(str => Right(ExchangeName(str)))
  implicit val rabbitNodeConfigReader: ConfigReader[Fs2RabbitNodeConfig] =
    deriveReader[Fs2RabbitNodeConfig]
  implicit val rabbitConfigReader: ConfigReader[Fs2RabbitConfig] = deriveReader[Fs2RabbitConfig]
  implicit val configReader: ConfigReader[Config]                = deriveReader[Config]
}
