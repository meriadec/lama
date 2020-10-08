package co.ledger.lama.service

import co.ledger.lama.service.routes.AccountController.CreationRequest
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

object ConfigSpec {
  case class ConfigSpec(
      server: ServerConfig,
      accounts: List[CreationRequest]
  )

  case class ServerConfig(
      host: String,
      port: Int
  )

  object ConfigSpec {
    implicit val creationRequestReader: ConfigReader[CreationRequest] =
      deriveReader[CreationRequest]
    implicit val serverConfigReader: ConfigReader[ServerConfig] = deriveReader[ServerConfig]
    implicit val configReader: ConfigReader[ConfigSpec]         = deriveReader[ConfigSpec]
  }
}
