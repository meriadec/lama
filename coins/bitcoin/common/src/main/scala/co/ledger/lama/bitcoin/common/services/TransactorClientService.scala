package co.ledger.lama.bitcoin.common.services

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.transactor.{CoinSelectionStrategy, PrepareTxOutput}
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
  ): IO[String]

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
  ): IO[String] =
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
      .map(_.hex) //TODO return whole reponse

}
