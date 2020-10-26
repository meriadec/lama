package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.{ContextShift, IO}
import doobie.Transactor
import doobie.implicits._
import co.ledger.lama.bitcoin.common.models.explorer.{Block, ConfirmedTransaction}
import fs2._

class TransactionInterpreter(
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

  def removeDataFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    TransactionQueries.deleteFromCursor(accountId, blockHeight).transact(db)

  def getLastBlocks(accountId: UUID): Stream[IO, Block] =
    TransactionQueries
      .fetchMostRecentBlocks(accountId)
      .transact(db)

}
