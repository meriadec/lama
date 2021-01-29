package co.ledger.lama.bitcoin.interpreter.services

import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.explorer.{
  Block,
  ConfirmedTransaction,
  UnconfirmedTransaction
}
import doobie.Transactor
import doobie.implicits._
import fs2._

class TransactionService(
    db: Transactor[IO],
    maxConcurrent: Int
) {

  def saveTransactions(
      accountId: UUID,
      transactions: List[ConfirmedTransaction]
  )(implicit cs: ContextShift[IO]): IO[Int] = {
    Stream
      .emits[IO, ConfirmedTransaction](transactions)
      .parEvalMapUnordered(maxConcurrent) { tx =>
        TransactionQueries
          .saveTransaction(tx, accountId)
          .transact(db)
      }
      .compile
      .fold(0)(_ + _)
  }

  def fetchUnconfirmedTransactions(
      accountId: UUID
  )(implicit cs: ContextShift[IO]): IO[List[UnconfirmedTransaction]] =
    TransactionQueries
      .fetchUnconfirmedTransactions(accountId)
      .transact(db)
      .compile
      .toList
      .map(_.flatten)

  def deleteUnconfirmedTransaction(accountId: UUID): IO[Int] =
    TransactionQueries
      .deleteUnconfirmedTransactions(accountId)
      .transact(db)

  def saveUnconfirmedTransactions(
      accountId: UUID,
      transactions: List[UnconfirmedTransaction]
  )(implicit cs: ContextShift[IO]): IO[Int] = {
    if (transactions.nonEmpty)
      TransactionQueries
        .saveUnconfirmedTransactions(accountId, transactions)
        .transact(db)
    else IO.pure(0)
  }

  def removeFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    TransactionQueries.removeFromCursor(accountId, blockHeight).transact(db)

  def getLastBlocks(accountId: UUID): Stream[IO, Block] =
    TransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

}
