package co.ledger.lama.bitcoin.interpreter

import java.time.Instant
import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.common.models.interpreter._
import co.ledger.lama.bitcoin.interpreter.services._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models._
import co.ledger.lama.common.services.NotificationService
import io.circe.syntax._
import doobie.Transactor

class Interpreter(
    notificationService: NotificationService,
    db: Transactor[IO],
    maxConcurrent: Int
)(implicit cs: ContextShift[IO])
    extends IOLogging {

  val transactionService = new TransactionService(db, maxConcurrent)
  val operationService   = new OperationService(db, maxConcurrent)
  val flaggingService    = new FlaggingService(db)
  val balanceService     = new BalanceService(db)

  def saveTransactions(
      accountId: UUID,
      transactions: List[ConfirmedTransaction]
  ): IO[Int] =
    transactionService.saveTransactions(accountId, transactions)

  def getLastBlocks(
      accountId: UUID
  ): IO[List[Block]] =
    transactionService
      .getLastBlocks(accountId)
      .compile
      .toList

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      requestLimit: Int,
      requestOffset: Int,
      sort: Sort
  ): IO[GetOperationsResult] = {
    val limit  = if (requestLimit <= 0) 20 else requestLimit
    val offset = if (requestOffset < 0) 0 else requestOffset
    operationService.getOperations(accountId, blockHeight, limit, offset, sort)
  }

  def getUTXOs(
      accountId: UUID,
      requestLimit: Int,
      requestOffset: Int,
      sort: Sort
  ): IO[GetUtxosResult] = {
    val limit  = if (requestLimit <= 0) 20 else requestLimit
    val offset = if (requestOffset < 0) 0 else requestOffset
    operationService.getUTXOs(accountId, sort, limit, offset)
  }

  def removeDataFromCursor(
      accountId: UUID,
      blockHeight: Long
  ): IO[Int] = {
    for {
      txRes <- transactionService.removeFromCursor(accountId, blockHeight)
      _     <- log.info(s"Deleted $txRes transactions")
      balancesRes <- balanceService.removeBalanceHistoryFromCursor(
        accountId,
        blockHeight
      )
      _ <- log.info(s"Deleted $balancesRes balances history")
    } yield txRes
  }

  def compute(
      accountId: UUID,
      addresses: List[AccountAddress],
      coin: Coin
  ): IO[Int] =
    for {
      balanceHistoryCount <- balanceService.getBalanceHistoryCount(accountId)
      nbSavedOps          <- computeOps(accountId, addresses, coin, balanceHistoryCount > 0)
      _                   <- log.info("Computing balance history")
      _                   <- balanceService.computeNewBalanceHistory(accountId)
      currentBalance      <- balanceService.getCurrentBalance(accountId)
      _ <- notificationService.notify(
        BalanceUpdatedNotification(
          accountId = accountId,
          coinFamily = CoinFamily.Bitcoin,
          coin = coin,
          currentBalance = currentBalance.asJson
        )
      )
    } yield nbSavedOps

  def computeOps(
      accountId: UUID,
      addresses: List[AccountAddress],
      coin: Coin,
      shouldNotify: Boolean
  ): IO[Int] = {

    for {
      _ <- log.info(s"Flagging inputs and outputs belong to account=$accountId")
      _ <- flaggingService.flagInputsAndOutputs(accountId, addresses)

      _ <- log.info("Computing operations")
      nbSavedOps <- operationService
        .compute(accountId)
        .through(operationService.saveOperationSink)
        .parEvalMap(maxConcurrent) { op =>
          if (shouldNotify) {
            notificationService.notify(
              OperationNotification(
                accountId = accountId,
                coinFamily = CoinFamily.Bitcoin,
                coin = coin,
                operation = op.asJson
              )
            )
          } else {
            IO.unit
          }
        }
        .compile
        .toList
        .map(_.length)

    } yield nbSavedOps
  }

  def getBalance(
      accountId: UUID
  ): IO[CurrentBalance] =
    balanceService.getCurrentBalance(accountId)

  def getBalanceHistory(
      accountId: UUID,
      startO: Option[Instant],
      endO: Option[Instant],
      interval: Int
  ): IO[List[BalanceHistory]] = {

    if (startO.forall(start => endO.forall(end => start.isBefore(end))) && interval >= 0)
      balanceService.getBalanceHistory(
        accountId,
        startO,
        endO,
        if (interval > 0) Some(interval) else None
      )
    else
      IO.raiseError(
        new Exception(
          "Invalid parameters : 'start' should not be after 'end' and 'interval' should be positive"
        )
      )
  }

}
