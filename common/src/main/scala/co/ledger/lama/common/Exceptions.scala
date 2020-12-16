package co.ledger.lama.common

object Exceptions {

  abstract class GrpcClientException(t: Throwable, val clientName: String = getClass.getName)
      extends Exception(s"$clientName - ${t.getMessage}", t)

  object GrpcClientException {
    def apply(t: Throwable): GrpcClientException =
      GrpcClientException(t)
  }

  case object MalformedProtobufUuidException extends Exception("Invalid UUID on protobuf request")
}
