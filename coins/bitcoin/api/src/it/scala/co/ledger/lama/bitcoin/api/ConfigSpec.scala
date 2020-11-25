package co.ledger.lama.bitcoin.api

import co.ledger.lama.common.models.Coin
import co.ledger.lama.manager.config.CoinConfig
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.ExchangeName
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import pureconfig.generic.semiauto.deriveReader
import cats.implicits._
import pureconfig.module.cats._
import pureconfig.configurable._

object ConfigSpec {

  case class ConfigSpec(
      server: ServerConfig,
      eventsConfig: EventsConfig
  )

  case class ServerConfig(
      host: String,
      port: Int
  )

  case class EventsConfig(
      lamaEventsExchangeName: ExchangeName,
      rabbit: Fs2RabbitConfig,
      coins: Map[Coin, CoinConfig]
  )

  object ConfigSpec {

    implicit val exchangeNameConfigReader: ConfigReader[ExchangeName] =
      ConfigReader.fromString(str => Right(ExchangeName(str)))
    implicit val rabbitNodeConfigReader: ConfigReader[Fs2RabbitNodeConfig] =
      deriveReader[Fs2RabbitNodeConfig]

    implicit val coinConfig: ConfigReader[CoinConfig] = deriveReader[CoinConfig]
    implicit val mapCoins: ConfigReader[Map[Coin, CoinConfig]] =
      genericMapReader(
        c => Coin.fromKey(c).toRight(CannotConvert(c, toType = "Coin", "Unknown coin")))
    implicit val rabbitConfigReader: ConfigReader[Fs2RabbitConfig] = deriveReader[Fs2RabbitConfig]
    implicit val coinReader: ConfigReader[Coin] =
      ConfigReader.fromString(c =>
        Coin.fromKey(c).toRight(CannotConvert(c, toType = "Coin", "Unknown coin")))

    implicit val eventsConfigReader: ConfigReader[EventsConfig] = deriveReader[EventsConfig]

    implicit val serverConfigReader: ConfigReader[ServerConfig] = deriveReader[ServerConfig]

    implicit val configReader: ConfigReader[ConfigSpec] = deriveReader[ConfigSpec]
  }

}
