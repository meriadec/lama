package co.ledger.lama.bitcoin.transactor.services

import cats.effect.IO
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib._
import co.ledger.protobuf.bitcoin.libgrpc
import io.grpc.Metadata

trait BitcoinLibGrpcService {
  def createTransaction(transaction: CreateTransactionRequest): IO[RawTransactionResponse]
}

class BitcoinLibGrpcClientService(grpcClient: libgrpc.CoinServiceFs2Grpc[IO, Metadata])
    extends BitcoinLibGrpcService {

  def createTransaction(transaction: CreateTransactionRequest): IO[RawTransactionResponse] =
    grpcClient
      .createTransaction(
        transaction.toProto,
        new Metadata
      )
      .map(RawTransactionResponse.fromProto)
}
