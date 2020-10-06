package co.ledger.lama.common

object Exceptions {

  case object MalformedProtobufUuidException extends Exception("Invalid UUID on protobuf request")
}
