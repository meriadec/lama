package co.ledger.lama.bitcoin.api

import co.ledger.lama.common.utils.GrpcClientConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

object Config {
  case class Config(
      server: ServerConfig,
      bitcoin: BitcoinServicesConfig,
      accountManager: GrpcClientConfig
  )

  case class BitcoinServicesConfig(
      keychain: GrpcClientConfig,
      interpreter: GrpcClientConfig,
      broadcaster: GrpcClientConfig
  )

  case class ServerConfig(
      host: String,
      port: Int
  )

  object Config {
    implicit val apisConfigReader: ConfigReader[BitcoinServicesConfig] =
      deriveReader[BitcoinServicesConfig]
    implicit val serverConfigReader: ConfigReader[ServerConfig] = deriveReader[ServerConfig]
    implicit val configReader: ConfigReader[Config]             = deriveReader[Config]
  }
}
