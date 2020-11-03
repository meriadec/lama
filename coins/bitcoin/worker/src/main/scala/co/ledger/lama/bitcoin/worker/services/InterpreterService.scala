package co.ledger.lama.bitcoin.worker.services

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.interpreter.protobuf.{
  AccountAddress,
  BitcoinInterpreterServiceFs2Grpc,
  ComputeRequest,
  DeleteTransactionsRequest,
  GetLastBlocksRequest,
  GetLastBlocksResult,
  GetOperationsRequest,
  GetOperationsResult,
  SaveTransactionsRequest,
  SortingOrder
}
import co.ledger.lama.bitcoin.common.models.explorer.ConfirmedTransaction
import co.ledger.lama.bitcoin.worker.services.SortingEnum.{Ascending, Descending, SortingEnum}
import co.ledger.lama.common.utils.UuidUtils
import io.grpc.Metadata

object SortingEnum extends Enumeration {
  type SortingEnum = Value
  val Ascending, Descending = Value
}

trait InterpreterService {
  def saveTransactions(accountId: UUID, txs: List[ConfirmedTransaction]): IO[Int]

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int]

  def getTransactions(
      accountId: UUID,
      blockHeight: Option[Long],
      limit: Option[Int],
      offset: Option[Int],
      sortingOrder: Option[SortingEnum]
  ): IO[GetOperationsResult]

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult]

  def compute(
      accountId: UUID,
      addresses: Seq[AccountAddress]
  ): IO[Int]
}

class InterpreterGrpcClientService(grpcClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata])
    extends InterpreterService {

  def saveTransactions(accountId: UUID, txs: List[ConfirmedTransaction]): IO[Int] =
    grpcClient
      .saveTransactions(
        new SaveTransactionsRequest(
          accountId = UuidUtils uuidToBytes accountId,
          transactions = txs.map(_.toProto)
        ),
        new Metadata()
      )
      .map(_.count)

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int] =
    grpcClient
      .removeDataFromCursor(
        new DeleteTransactionsRequest(
          UuidUtils uuidToBytes accountId,
          blockHeightCursor.getOrElse(0)
        ),
        new Metadata()
      )
      .map(_.count)

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

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult] =
    grpcClient.getLastBlocks(
      new GetLastBlocksRequest(
        UuidUtils uuidToBytes accountId
      ),
      new Metadata()
    )

  def compute(accountId: UUID, addresses: Seq[AccountAddress]): IO[Int] =
    grpcClient
      .compute(
        new ComputeRequest(
          UuidUtils.uuidToBytes(accountId),
          addresses
        ),
        new Metadata()
      )
      .map(_.count)

}
