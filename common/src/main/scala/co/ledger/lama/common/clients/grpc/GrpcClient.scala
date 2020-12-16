package co.ledger.lama.common.clients.grpc

import co.ledger.lama.common.Exceptions.GrpcClientException
import io.grpc.{CallOptions, ManagedChannel, StatusRuntimeException}

object GrpcClient {
  type Builder[Client] =
    (ManagedChannel, CallOptions, StatusRuntimeException => Option[GrpcClientException]) => Client

  private def onError(e: StatusRuntimeException): Option[GrpcClientException] = {
    Some(GrpcClientException(e))
  }

  def resolveClient[Client](
      f: Builder[Client],
      managedChannel: ManagedChannel
  ): Client = f(managedChannel, CallOptions.DEFAULT, onError)
}
