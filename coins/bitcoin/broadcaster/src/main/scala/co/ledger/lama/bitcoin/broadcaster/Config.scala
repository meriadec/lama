package co.ledger.lama.bitcoin.broadcaster

import co.ledger.lama.common.utils.GrpcServerConfig
import pureconfig.ConfigReader
import pureconfig.generic.semiauto.deriveReader

case class Config(grpcServer: GrpcServerConfig)

object Config {
  implicit val configReader: ConfigReader[Config] = deriveReader[Config]
}
