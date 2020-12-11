package co.ledger.lama.bitcoin.common.models.interpreter

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.protobuf.bitcoin.keychain
import io.circe.{Decoder, Encoder}

sealed trait ChangeType {
  val name: String
  def toProto: protobuf.ChangeType
  def toKeychainProto: keychain.Change
}

object ChangeType {

  case object Internal extends ChangeType {
    val name = "internal"
    def toProto: protobuf.ChangeType = {
      protobuf.ChangeType.INTERNAL
    }

    def toKeychainProto: keychain.Change = {
      keychain.Change.CHANGE_INTERNAL
    }
  }

  case object External extends ChangeType {
    val name = "external"
    def toProto: protobuf.ChangeType = {
      protobuf.ChangeType.EXTERNAL
    }
    def toKeychainProto: keychain.Change = {
      keychain.Change.CHANGE_EXTERNAL
    }
  }

  implicit val encoder: Encoder[ChangeType] = Encoder.encodeString.contramap(_.name)
  implicit val decoder: Decoder[ChangeType] =
    Decoder.decodeString.emap(fromKey(_).toRight("Could not decode as change type"))

  val all: Map[String, ChangeType] = Map(Internal.name -> Internal, External.name -> External)

  def fromKey(key: String): Option[ChangeType] = all.get(key)

  def fromProto(proto: protobuf.ChangeType): ChangeType = {
    proto match {
      case protobuf.ChangeType.INTERNAL => Internal
      case _                            => External
    }
  }

  def fromKeychainProto(proto: keychain.Change): ChangeType = {
    proto match {
      case keychain.Change.CHANGE_INTERNAL => Internal
      case _                               => External
    }
  }

}
