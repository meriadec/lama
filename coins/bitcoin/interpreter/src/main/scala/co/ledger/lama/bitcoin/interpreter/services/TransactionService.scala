package co.ledger.lama.bitcoin.interpreter.services

import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.worker.{Block, ConfirmedTransaction}
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

  def removeFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    TransactionQueries.removeFromCursor(accountId, blockHeight).transact(db)

  def getLastBlocks(accountId: UUID): Stream[IO, Block] =
    TransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

}
