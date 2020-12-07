package co.ledger.lama.bitcoin.interpreter.services

import java.time.Instant
import java.util.UUID
import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.interpreter.{BalanceHistory, OperationType}
import co.ledger.lama.common.logging.IOLogging
import doobie.Transactor
import doobie.implicits._

class BalanceService(db: Transactor[IO]) extends IOLogging {

  def getBalanceHistories(accountId: UUID): IO[List[BalanceHistory]] =
    for {
      operations <- BalanceQueries
        .getOperationsForBalanceHistory(accountId)
        .transact(db)
        .compile
        .toList

      balanceHistories = operations.foldLeft[List[BalanceHistory]](List()) { case (acc, op) =>
        acc match {
          case Nil => BalanceHistory(op.value, utxos = 1, op.value, 0, op.time) :: acc
          case head :: xs =>
            val (newValue, received, sent) = op.operationType match {
              case OperationType.Sent =>
                (head.balance - op.value, head.received, head.sent + op.value)
              case OperationType.Received =>
                (head.balance + op.value, head.received + op.value, head.sent)
            }
            val utxosCount     = if (head.time == op.time) head.utxos + 1 else 1
            val listToAppendTo = if (head.time == op.time) xs else acc
            BalanceHistory(newValue, utxosCount, received, sent, op.time) :: listToAppendTo
        }
      }
    } yield balanceHistories

  def compute(accountId: UUID): IO[BalanceHistory] =
    for {
      currentBalance <- getBalance(accountId)
      block          <- BalanceQueries.getLastBlock(accountId).transact(db)
      savedBalanceHistory <- BalanceQueries
        .saveBalanceHistory(accountId, currentBalance, block.height)
        .transact(db)
    } yield savedBalanceHistory

  def getBalance(accountId: UUID): IO[BalanceHistory] =
    BalanceQueries.getCurrentBalance(accountId).transact(db)

  def getBalancesHistory(accountId: UUID, start: Instant, end: Instant): IO[Seq[BalanceHistory]] =
    BalanceQueries.getBalancesHistory(accountId, start, end).transact(db).compile.toList

  def getBalancesHistoryCount(accountId: UUID): IO[Int] =
    BalanceQueries.getBalancesHistoryCount(accountId).transact(db)

  def removeBalancesHistoryFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    BalanceQueries.removeBalancesHistoryFromCursor(accountId, blockHeight).transact(db)

}
