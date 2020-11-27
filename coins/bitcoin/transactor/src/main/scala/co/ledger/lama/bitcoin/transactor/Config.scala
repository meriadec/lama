package co.ledger.lama.bitcoin.transactor

import co.ledger.lama.common.utils.{GrpcClientConfig, GrpcServerConfig}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class Config(
    grpcServer: GrpcServerConfig,
    interpreter: GrpcClientConfig,
    bitcoinLib: GrpcClientConfig
)

object Config {
  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
}
