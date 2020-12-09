package co.ledger.lama.bitcoin.common.grpc

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.transactor.{
  BroadcastTransactionResponse,
  CoinSelectionStrategy,
  CreateTransactionResponse,
  PrepareTxOutput
}
import co.ledger.lama.bitcoin.transactor.protobuf
import co.ledger.lama.common.models.Coin
import co.ledger.lama.common.utils.UuidUtils
import io.grpc.Metadata

trait TransactorClientService {

  def createTransaction(
      accountId: UUID,
      keychainId: UUID,
      coinSelection: CoinSelectionStrategy,
      outputs: List[PrepareTxOutput],
      coin: Coin
  ): IO[CreateTransactionResponse]

  def broadcastTransaction(
      accountId: UUID,
      keychainId: UUID,
      coinId: String,
      rawTransaction: CreateTransactionResponse,
      privKey: String
  ): IO[BroadcastTransactionResponse]
}

class TransactorGrpcClientService(
    grpcClient: protobuf.BitcoinTransactorServiceFs2Grpc[IO, Metadata]
) extends TransactorClientService {

  def createTransaction(
      accountId: UUID,
      keychainId: UUID,
      coinSelection: CoinSelectionStrategy,
      outputs: List[PrepareTxOutput],
      coin: Coin
  ): IO[CreateTransactionResponse] =
    grpcClient
      .createTransaction(
        new protobuf.CreateTransactionRequest(
          UuidUtils.uuidToBytes(accountId),
          UuidUtils.uuidToBytes(keychainId),
          coinSelection.toProto,
          outputs.map(_.toProto),
          coin.name
        ),
        new Metadata
      )
      .map(CreateTransactionResponse.fromProto)

  def broadcastTransaction(
      accountId: UUID,
      keychainId: UUID,
      coinId: String,
      rawTransaction: CreateTransactionResponse,
      privKey: String
  ): IO[BroadcastTransactionResponse] = {
    grpcClient
      .broadcastTransaction(
        protobuf.BroadcastTransactionRequest(
          UuidUtils.uuidToBytes(accountId),
          UuidUtils.uuidToBytes(keychainId),
          coinId,
          Some(rawTransaction.toProto),
          privKey: String
        ),
        new Metadata
      )
      .map(BroadcastTransactionResponse.fromProto)
  }
}
