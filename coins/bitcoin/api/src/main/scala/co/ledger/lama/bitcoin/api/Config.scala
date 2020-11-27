package co.ledger.lama.bitcoin.api

import co.ledger.lama.common.utils.GrpcClientConfig
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.ExchangeName
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader
import cats.implicits._
import pureconfig.module.cats._

object Config {
  case class Config(
      lamaNotificationsExchangeName: ExchangeName,
      rabbit: Fs2RabbitConfig,
      server: ServerConfig,
      bitcoin: BitcoinServicesConfig,
      accountManager: GrpcClientConfig
  ) {
    val maxConcurrent: Int = 50 // TODO : bench [Runtime.getRuntime.availableProcessors() * x]
  }

  case class BitcoinServicesConfig(
      keychain: GrpcClientConfig,
      interpreter: GrpcClientConfig,
      transactor: GrpcClientConfig
  )

  case class ServerConfig(
      host: String,
      port: Int
  )

  object Config {
    implicit val exchangeNameConfigReader: ConfigReader[ExchangeName] =
      ConfigReader.fromString(str => Right(ExchangeName(str)))
    implicit val rabbitNodeConfigReader: ConfigReader[Fs2RabbitNodeConfig] =
      deriveReader[Fs2RabbitNodeConfig]
    implicit val rabbitConfigReader: ConfigReader[Fs2RabbitConfig] = deriveReader[Fs2RabbitConfig]
    implicit val apisConfigReader: ConfigReader[BitcoinServicesConfig] =
      deriveReader[BitcoinServicesConfig]
    implicit val serverConfigReader: ConfigReader[ServerConfig] = deriveReader[ServerConfig]
    implicit val configReader: ConfigReader[Config]             = deriveReader[Config]
  }
}
