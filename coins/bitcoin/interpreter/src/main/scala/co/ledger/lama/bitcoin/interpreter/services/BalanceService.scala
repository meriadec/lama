package co.ledger.lama.bitcoin.interpreter.services

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.service.BalanceHistory
import co.ledger.lama.common.logging.IOLogging
import doobie.Transactor
import doobie.implicits._

class BalanceService(db: Transactor[IO]) extends IOLogging {

  def compute(accountId: UUID): IO[BalanceHistory] =
    for {
      currentBalance <- getBalance(accountId)
      block          <- BalanceQueries.getLastBlock(accountId).transact(db)
      _ <- BalanceQueries
        .saveBalanceHistory(accountId, currentBalance, block.height)
        .transact(db)
    } yield currentBalance

  def getBalance(accountId: UUID): IO[BalanceHistory] =
    BalanceQueries.getCurrentBalance(accountId).transact(db)

  def getBalancesHistory(accountId: UUID, start: Instant, end: Instant): IO[Seq[BalanceHistory]] =
    BalanceQueries.getBalancesHistory(accountId, start, end).transact(db).compile.toList

  def getBalancesHistoryCount(accountId: UUID): IO[Int] =
    BalanceQueries.getBalancesHistoryCount(accountId).transact(db)

  def removeBalancesHistoryFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    BalanceQueries.removeBalancesHistoryFromCursor(accountId, blockHeight).transact(db)

}
