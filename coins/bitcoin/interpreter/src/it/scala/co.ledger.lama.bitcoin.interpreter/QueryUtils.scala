package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.explorer.ConfirmedTransaction
import co.ledger.lama.bitcoin.interpreter.models.OperationToSave
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Chunk

object QueryUtils {

  def fetchTx(db: Transactor[IO], accountId: UUID, hash: String) = {
    OperationQueries
      .fetchTransaction(accountId, hash)
      .transact(db)
  }

  def saveTx(db: Transactor[IO], transaction: ConfirmedTransaction, accountId: UUID) = {
    TransactionQueries
      .saveTransaction(transaction, accountId)
      .transact(db)
      .void
  }

  def fetchOps(db: Transactor[IO], accountId: UUID) = {
    OperationQueries
      .fetchOperations(accountId)
      .transact(db)
      .compile
      .toList
  }

  def saveOp(db: Transactor[IO], operation: OperationToSave) = {
    OperationQueries
      .saveOperations(Chunk(operation))
      .transact(db)
      .void
  }

}
