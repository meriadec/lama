package co.ledger.lama.bitcoin.worker.mock.faulty

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.interpreter
import co.ledger.lama.bitcoin.common.models.interpreter.grpc.{
  GetLastBlocksResult,
  GetOperationsResult
}
import co.ledger.lama.bitcoin.common.models.interpreter.{AccountAddress, grpc}
import co.ledger.lama.bitcoin.common.models.worker.{ConfirmedTransaction, InterpreterServiceError}
import co.ledger.lama.bitcoin.common.services.InterpreterClientService
import co.ledger.lama.common.models.Sort

class FaultyInterpreterClientServiceMock extends InterpreterClientService with FaultyBase {

  def saveTransactions(
      accountId: UUID,
      txs: List[ConfirmedTransaction]
  ): IO[Int] =
    IO.raiseError(
      InterpreterServiceError(
        cause = err,
        errorMessage = s"Failed to save transactions for this account $accountId"
      )
    )

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int] =
    IO.raiseError(
      InterpreterServiceError(
        cause = err,
        errorMessage = s"Failed to remove data from cursor for this account $accountId"
      )
    )

  def compute(accountId: UUID, addresses: List[AccountAddress]): IO[Int] =
    IO.raiseError(
      InterpreterServiceError(
        cause = err,
        errorMessage = s"Failed to compute addresses for this account $accountId"
      )
    )

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult] =
    IO.raiseError(
      InterpreterServiceError(
        cause = err,
        errorMessage = s"Failed to get last blocks for this account $accountId"
      )
    )

  override def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetOperationsResult] =
    IO.raiseError(
      InterpreterServiceError(
        cause = err,
        errorMessage = s"Failed to get operations for this account $accountId"
      )
    )

  override def getUTXOs(
      accountId: UUID,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[grpc.GetUTXOsResult] =
    IO.raiseError(
      InterpreterServiceError(
        cause = err,
        errorMessage = s"Failed to get utxos for this account $accountId"
      )
    )

  override def getBalance(accountId: UUID): IO[interpreter.BalanceHistory] =
    IO.raiseError(
      InterpreterServiceError(
        cause = err,
        errorMessage = s"Failed to get balance for this account $accountId"
      )
    )

  override def getBalanceHistory(
      accountId: UUID,
      start: Option[Instant],
      end: Option[Instant]
  ): IO[grpc.GetBalanceHistoryResult] =
    IO.raiseError(
      InterpreterServiceError(
        cause = err,
        errorMessage = s"Failed to get balance history for this account $accountId"
      )
    )
}
