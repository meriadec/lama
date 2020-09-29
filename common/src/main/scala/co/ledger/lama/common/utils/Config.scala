package co.ledger.lama.common.utils

import pureconfig.ConfigReader

import pureconfig.generic.semiauto.deriveReader

case class PostgresConfig(
    url: String,
    user: String,
    password: String
) {
  val driver: String = "org.postgresql.Driver"
  val poolSize: Int  = Runtime.getRuntime.availableProcessors() * 2
}

object PostgresConfig {
  implicit val configReader: ConfigReader[PostgresConfig] = deriveReader[PostgresConfig]
}

case class GrpcServerConfig(port: Int)

object GrpcServerConfig {
  implicit val configReader: ConfigReader[GrpcServerConfig] = deriveReader[GrpcServerConfig]

}
