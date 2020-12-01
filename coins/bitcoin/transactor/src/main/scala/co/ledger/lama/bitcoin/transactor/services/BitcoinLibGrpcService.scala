package co.ledger.lama.bitcoin.transactor.services

import cats.effect.IO
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.protobuf.bitcoin.libgrpc
import io.grpc.Metadata

trait BitcoinLibGrpcService {
  def createTransaction(
      transaction: CreateTransactionRequest,
      changeAddress: String,
      estimatedFeeSatPerKb: Long
  ): IO[RawTransactionResponse]
}

class BitcoinLibGrpcClientService(grpcClient: libgrpc.CoinServiceFs2Grpc[IO, Metadata])
    extends BitcoinLibGrpcService
    with IOLogging {

  def createTransaction(
      transaction: CreateTransactionRequest,
      changeAddress: String,
      estimatedFeeSatPerKb: Long
  ): IO[RawTransactionResponse] = {

    // Whenever Bitcoin Lib is ready, pass the estimated fees to createTransaction.
    // For now, we log...
    log.info(s"Estimated fees per bytes : $estimatedFeeSatPerKb")
    log.info(s"change Address : ${changeAddress}")

    grpcClient
      .createTransaction(
        transaction.toProto,
        new Metadata
      )
      .map(RawTransactionResponse.fromProto)
  }
}
