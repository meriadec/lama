package co.ledger.lama.bitcoin.common.services

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.worker.{ConfirmedTransaction, InterpreterServiceError}
import co.ledger.lama.bitcoin.common.models.interpreter._
import co.ledger.lama.bitcoin.common.models.interpreter.grpc._
import co.ledger.lama.bitcoin.common.utils.BtcProtoUtils._
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.Sort
import co.ledger.lama.common.utils.{ProtobufUtils, UuidUtils}
import io.grpc.Metadata

trait InterpreterClientService {
  def saveTransactions(accountId: UUID, txs: List[ConfirmedTransaction]): IO[Int]

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int]

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult]

  def compute(
      accountId: UUID,
      addresses: List[AccountAddress]
  ): IO[Int]

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetOperationsResult]

  def getUTXOs(
      accountId: UUID,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetUTXOsResult]

  def getBalance(accountId: UUID): IO[BalanceHistory]

  def getBalanceHistory(
      accountId: UUID,
      start: Option[Instant],
      end: Option[Instant]
  ): IO[GetBalanceHistoryResult]
}

class InterpreterGrpcClientService(
    grpcClient: protobuf.BitcoinInterpreterServiceFs2Grpc[IO, Metadata]
) extends InterpreterClientService {

  def saveTransactions(accountId: UUID, txs: List[ConfirmedTransaction]): IO[Int] =
    grpcClient
      .saveTransactions(
        protobuf.SaveTransactionsRequest(
          accountId = UuidUtils uuidToBytes accountId,
          transactions = txs.map(_.toProto)
        ),
        new Metadata()
      )
      .map(_.count)
      .handleErrorWith(err =>
        IO.raiseError(
          InterpreterServiceError(
            rootCause = err,
            errorMessage = s"Failed to save transactions for this account $accountId"
          )
        )
      )

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int] =
    grpcClient
      .removeDataFromCursor(
        protobuf.DeleteTransactionsRequest(
          UuidUtils uuidToBytes accountId,
          blockHeightCursor.getOrElse(0)
        ),
        new Metadata()
      )
      .map(_.count)
      .handleErrorWith(err =>
        IO.raiseError(
          InterpreterServiceError(
            rootCause = err,
            errorMessage = s"Failed to remove data from cursor for this account $accountId"
          )
        )
      )

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult] =
    grpcClient
      .getLastBlocks(
        protobuf.GetLastBlocksRequest(
          UuidUtils uuidToBytes accountId
        ),
        new Metadata()
      )
      .map(GetLastBlocksResult.fromProto)
      .handleErrorWith(err =>
        IO.raiseError(
          InterpreterServiceError(
            rootCause = err,
            errorMessage = s"Failed to get last blocks for this account $accountId"
          )
        )
      )

  def compute(accountId: UUID, addresses: List[AccountAddress]): IO[Int] =
    grpcClient
      .compute(
        protobuf.ComputeRequest(
          UuidUtils.uuidToBytes(accountId),
          addresses.map(_.toProto)
        ),
        new Metadata()
      )
      .map(_.count)
      .handleErrorWith(err =>
        IO.raiseError(
          InterpreterServiceError(
            rootCause = err,
            errorMessage = s"Failed to compute addresses for this account $accountId"
          )
        )
      )

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetOperationsResult] =
    grpcClient
      .getOperations(
        protobuf.GetOperationsRequest(
          accountId = UuidUtils.uuidToBytes(accountId),
          blockHeight = blockHeight,
          limit = limit,
          offset = offset,
          sort = sort.map(_.toProto).getOrElse(protobuf.SortingOrder.DESC)
        ),
        new Metadata
      )
      .map(GetOperationsResult.fromProto)
      .handleErrorWith(err =>
        IO.raiseError(
          InterpreterServiceError(
            rootCause = err,
            errorMessage = s"Failed to get operations for this account $accountId"
          )
        )
      )

  def getUTXOs(
      accountId: UUID,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetUTXOsResult] = {
    grpcClient
      .getUTXOs(
        protobuf.GetUTXOsRequest(
          accountId = UuidUtils.uuidToBytes(accountId),
          limit = limit,
          offset = offset,
          sort = sort.map(_.toProto).getOrElse(protobuf.SortingOrder.DESC)
        ),
        new Metadata
      )
      .map(GetUTXOsResult.fromProto)
      .handleErrorWith(err =>
        IO.raiseError(
          InterpreterServiceError(
            rootCause = err,
            errorMessage = s"Failed to get utxos for this account $accountId"
          )
        )
      )
  }

  def getBalance(accountId: UUID): IO[BalanceHistory] = {
    grpcClient
      .getBalance(
        protobuf.GetBalanceRequest(
          accountId = UuidUtils.uuidToBytes(accountId)
        ),
        new Metadata
      )
      .map(BalanceHistory.fromProto)
      .handleErrorWith(err =>
        IO.raiseError(
          InterpreterServiceError(
            rootCause = err,
            errorMessage = s"Failed to get balance for this account $accountId"
          )
        )
      )
  }

  def getBalanceHistory(
      accountId: UUID,
      start: Option[Instant],
      end: Option[Instant]
  ): IO[GetBalanceHistoryResult] = {
    grpcClient
      .getBalanceHistory(
        protobuf.GetBalanceHistoryRequest(
          accountId = UuidUtils.uuidToBytes(accountId),
          start.map(ProtobufUtils.fromInstant),
          end.map(ProtobufUtils.fromInstant)
        ),
        new Metadata
      )
      .map(GetBalanceHistoryResult.fromProto)
      .handleErrorWith(err =>
        IO.raiseError(
          InterpreterServiceError(
            rootCause = err,
            errorMessage = s"Failed to get balance history for this account $accountId"
          )
        )
      )
  }
}
