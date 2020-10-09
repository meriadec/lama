package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.Explorer.{Block, Transaction}
import co.ledger.lama.bitcoin.common.models.Service.Operation
import doobie.util.transactor.Transactor
import doobie.implicits._

object QueryUtils {

  def saveBlock(db: Transactor[IO], block: Block) = {
    TransactionQueries
      .upsertBlock(block)
      .transact(db)
  }

  def fetchTx(db: Transactor[IO], accountId: UUID, hash: String) = {
    OperationQueries
      .fetchTx(accountId, hash)
      .transact(db)
  }

  def saveTx(db: Transactor[IO], transaction: Transaction, accountId: UUID) = {
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

  def saveOp(db: Transactor[IO], operation: Operation) = {
    OperationQueries
      .saveOperation(operation)
      .transact(db)
      .void
  }

}
