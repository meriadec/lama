package co.ledger.lama.bitcoin.transactor.services

import cats.effect.IO
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib._
import co.ledger.protobuf.bitcoin.libgrpc
import io.grpc.Metadata

trait BitcoinLibGrpcService {
  def createTransaction(transaction: CreateTransactionRequest): IO[CreateTransactionResponse]
}

class BitcoinLibGrpcClientService(grpcClient: libgrpc.CoinServiceFs2Grpc[IO, Metadata])
    extends BitcoinLibGrpcService {

  def createTransaction(transaction: CreateTransactionRequest): IO[CreateTransactionResponse] =
    grpcClient
      .createTransaction(
        transaction.toProto,
        new Metadata
      )
      .map(CreateTransactionResponse.fromProto)
}
