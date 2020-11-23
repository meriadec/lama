package co.ledger.lama.bitcoin.common.config

import cats.implicits._
import org.http4s.Uri
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto.deriveReader

import scala.concurrent.duration.FiniteDuration

case class ExplorerConfig(
    uri: Uri,
    addressesSize: Int,
    txsBatchSize: Int,
    timeout: FiniteDuration
)

object ExplorerConfig {
  implicit val configReader: ConfigReader[ExplorerConfig] = deriveReader[ExplorerConfig]
  implicit val uriReader: ConfigReader[Uri] = ConfigReader[String].emap { s =>
    Uri.fromString(s).leftMap(t => CannotConvert(s, "Uri", t.getMessage))
  }
}
