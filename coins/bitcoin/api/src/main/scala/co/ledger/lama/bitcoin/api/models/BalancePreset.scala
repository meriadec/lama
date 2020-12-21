package co.ledger.lama.bitcoin.api.models

import scala.concurrent.duration.FiniteDuration

sealed abstract class BalancePreset(
    val name: String,
    val durationInSeconds: Long,
    val interval: Int
)

object BalancePreset {

  case object Yearly
      extends BalancePreset(
        name = "yearly",
        durationInSeconds = FiniteDuration(364, "days").toSeconds,
        interval = 52
      )

  case object Monthly
      extends BalancePreset(
        name = "monthly",
        durationInSeconds = FiniteDuration(31, "days").toSeconds,
        interval = 31
      )

  case object Daily
      extends BalancePreset(
        name = "daily",
        durationInSeconds = FiniteDuration(1, "day").toSeconds,
        interval = 24
      )

  val all: Map[String, BalancePreset] =
    Map(Yearly.name -> Yearly, Monthly.name -> Monthly, Daily.name -> Daily)

  // Used by Http4s dsl
  def unapply(key: String): Option[BalancePreset] =
    all.get(key)

}

object BalancePresetVar {}
