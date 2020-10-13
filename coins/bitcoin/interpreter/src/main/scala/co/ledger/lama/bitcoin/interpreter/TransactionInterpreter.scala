package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.IO
import doobie.Transactor
import doobie.implicits._
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.explorer.Transaction

class TransactionInterpreter(db: Transactor[IO]) {

  def saveTransactions(accountId: UUID, transactions: List[Transaction]): IO[Int] =
    for {
      res <-
        transactions
          .traverse(tx =>
            (
              TransactionQueries.upsertBlock(tx.block),
              TransactionQueries.saveTransaction(tx, accountId)
            ).tupled.transact(db).map(_._2)
          )

    } yield res.sum

}
