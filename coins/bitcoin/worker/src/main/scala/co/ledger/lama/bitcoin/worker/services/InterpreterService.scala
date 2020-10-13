package co.ledger.lama.bitcoin.worker.services

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.interpreter.protobuf.{
  AccountAddress,
  BitcoinInterpreterServiceFs2Grpc,
  ComputeOperationsRequest,
  DeleteTransactionsRequest,
  GetOperationsRequest,
  GetOperationsResult,
  SaveTransactionsRequest,
  SortingOrder
}
import co.ledger.lama.bitcoin.common.models.explorer.Transaction
import co.ledger.lama.bitcoin.worker.services.SortingEnum.{Ascending, Descending, SortingEnum}
import co.ledger.lama.common.utils.UuidUtils
import io.grpc.Metadata

object SortingEnum extends Enumeration {
  type SortingEnum = Value
  val Ascending, Descending = Value
}

trait InterpreterService {
  def saveTransactions(accountId: UUID, txs: List[Transaction]): IO[Unit]

  def removeTransactions(accountId: UUID, blockHeightCursor: Option[Long]): IO[Unit]

  def getTransactions(
      accountId: UUID,
      blockHeight: Option[Long],
      limit: Option[Int],
      offset: Option[Int],
      sortingOrder: Option[SortingEnum]
  ): IO[GetOperationsResult]

  def computeOperations(
      accountId: UUID,
      addresses: Seq[AccountAddress]
  ): IO[Unit]
}

class InterpreterGrpcClientService(grpcClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata])
    extends InterpreterService {

  def saveTransactions(accountId: UUID, txs: List[Transaction]): IO[Unit] =
    grpcClient
      .saveTransactions(
        new SaveTransactionsRequest(
          accountId = UuidUtils uuidToBytes accountId,
          transactions = txs.map(_.toProto)
        ),
        new Metadata()
      )
      .void

  def removeTransactions(accountId: UUID, blockHeightCursor: Option[Long]): IO[Unit] =
    grpcClient
      .deleteTransactions(
        new DeleteTransactionsRequest(
          UuidUtils uuidToBytes accountId,
          blockHeightCursor.getOrElse(0)
        ),
        new Metadata()
      )
      .void

  def getTransactions(
      accountId: UUID,
      blockHeight: Option[Long],
      limit: Option[Int],
      offset: Option[Int],
      sortingOrder: Option[SortingEnum]
  ): IO[GetOperationsResult] = {

    val sort = sortingOrder.getOrElse(SortingEnum.Descending) match {
      case Ascending  => SortingOrder.ASC
      case Descending => SortingOrder.DESC
    }

    grpcClient.getOperations(
      new GetOperationsRequest(
        UuidUtils uuidToBytes accountId,
        blockHeight.getOrElse(0),
        limit.getOrElse(0),
        offset.getOrElse(0),
        sort
      ),
      new Metadata()
    )
  }

  def computeOperations(accountId: UUID, addresses: Seq[AccountAddress]): IO[Unit] =
    grpcClient
      .computeOperations(
        new ComputeOperationsRequest(
          UuidUtils.uuidToBytes(accountId),
          addresses
        ),
        new Metadata()
      )
      .void

}
