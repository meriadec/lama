package co.ledger.lama.service

import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

object ConfigSpec {
  case class ConfigSpec(
      server: ServerConfig
  )

  case class ServerConfig(
      host: String,
      port: Int
  )

  object ConfigSpec {
    implicit val serverConfigReader: ConfigReader[ServerConfig] = deriveReader[ServerConfig]
    implicit val configReader: ConfigReader[ConfigSpec]         = deriveReader[ConfigSpec]
  }
}
