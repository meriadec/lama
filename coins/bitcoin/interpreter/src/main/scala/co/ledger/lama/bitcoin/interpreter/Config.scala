package co.ledger.lama.bitcoin.interpreter

import co.ledger.lama.common.utils.{GrpcServerConfig, PostgresConfig}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class Config(
    postgres: PostgresConfig,
    grpcServer: GrpcServerConfig,
    maxConcurrent: Int = 50 // TODO : bench [Runtime.getRuntime.availableProcessors() * x]
)

object Config {
  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
}
