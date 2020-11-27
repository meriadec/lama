package co.ledger.lama.bitcoin.common.services

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.transactor.{CoinSelectionStrategy, PrepareTxOutput}
import co.ledger.lama.bitcoin.transactor.protobuf
import co.ledger.lama.bitcoin.transactor.protobuf.CreateTransactionRequest
import co.ledger.lama.common.utils.UuidUtils
import io.grpc.Metadata

trait TransactorClientService {

  def createTransaction(
      accountId: UUID,
      coinSelection: CoinSelectionStrategy,
      outputs: List[PrepareTxOutput]
  ): IO[String]

}

class TransactorGrpcClientService(
    grpcClient: protobuf.BitcoinTransactorServiceFs2Grpc[IO, Metadata]
) extends TransactorClientService {

  def createTransaction(
      accountId: UUID,
      coinSelection: CoinSelectionStrategy,
      outputs: List[PrepareTxOutput]
  ): IO[String] =
    grpcClient
      .createTransaction(
        new CreateTransactionRequest(
          UuidUtils.uuidToBytes(accountId),
          coinSelection.toProto,
          outputs.map(_.toProto)
        ),
        new Metadata
      )
      .map(_.hex)

}
