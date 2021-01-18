package co.ledger.lama.bitcoin.common.models.transactor

import co.ledger.lama.bitcoin.transactor.protobuf
import io.circe.{Decoder, Encoder}

abstract class FeeLevel(val name: String) {
  def toProto: protobuf.FeeLevel
}

object FeeLevel {
  case object Slow extends FeeLevel("slow") {
    def toProto: protobuf.FeeLevel = protobuf.FeeLevel.SLOW
  }

  case object Normal extends FeeLevel("normal") {
    def toProto: protobuf.FeeLevel = protobuf.FeeLevel.NORMAL
  }

  case object Fast extends FeeLevel("fast") {
    def toProto: protobuf.FeeLevel = protobuf.FeeLevel.FAST
  }

  val all: Map[String, FeeLevel] = Map(
    Slow.name   -> Slow,
    Normal.name -> Normal,
    Fast.name   -> Fast
  )

  def fromKey(key: String): Option[FeeLevel] = all.get(key)

  implicit val encoder: Encoder[FeeLevel] = Encoder.encodeString.contramap(_.name)

  implicit val decoder: Decoder[FeeLevel] =
    Decoder.decodeString.emap(fromKey(_).toRight("unable to decode fee level"))

  def fromProto(proto: protobuf.FeeLevel): FeeLevel =
    proto match {
      case protobuf.FeeLevel.SLOW => Slow
      case protobuf.FeeLevel.FAST => Fast
      case _                      => Normal
    }
}
