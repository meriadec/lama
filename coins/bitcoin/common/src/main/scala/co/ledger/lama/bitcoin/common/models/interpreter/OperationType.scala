package co.ledger.lama.bitcoin.common.models.interpreter

import co.ledger.lama.bitcoin.interpreter.protobuf
import io.circe.{Decoder, Encoder}

sealed trait OperationType {
  val name: String
  def toProto: protobuf.OperationType
}

object OperationType {

  case object Sent extends OperationType {
    val name = "sent"
    def toProto: protobuf.OperationType = {
      protobuf.OperationType.SENT
    }
  }

  case object Received extends OperationType {
    val name = "received"
    def toProto: protobuf.OperationType = {
      protobuf.OperationType.RECEIVED
    }
  }

  implicit val encoder: Encoder[OperationType] = Encoder.encodeString.contramap(_.name)
  implicit val decoder: Decoder[OperationType] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as operation type"))

  val all: Map[String, OperationType] = Map(Sent.name -> Sent, Received.name -> Received)

  def fromKey(key: String): Option[OperationType] = all.get(key)

  def fromProto(proto: protobuf.OperationType): OperationType = {
    proto match {
      case protobuf.OperationType.SENT => Sent
      case _                           => Received

    }
  }
}
