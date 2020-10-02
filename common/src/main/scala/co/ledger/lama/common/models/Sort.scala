package co.ledger.lama.common.models

import io.circe.{Decoder, Encoder}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

abstract class Sort(val name: String)

object Sort {
  case object Ascending extends Sort("ASC")

  case object Descending extends Sort("DESC")

  val all: Map[String, Sort] = Map(Ascending.name -> Ascending, Descending.name -> Descending)

  def fromKey(key: String): Option[Sort] = all.get(key)

  implicit val encoder: Encoder[Sort] = Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[Sort] =
    Decoder.decodeString.emap(fromKey(_).toRight("unable to decode sort"))

  implicit val configReader: ConfigReader[Sort] =
    ConfigReader.fromString(str => fromKey(str).toRight(CannotConvert(str, "Sort", "unknown")))
}
