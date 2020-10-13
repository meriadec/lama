package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.IO
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.service.{AccountAddress, Operation}
import doobie.Transactor
import doobie.implicits._

class OperationInterpreter(db: Transactor[IO]) {

  def getOperations(accountId: UUID, limit: Int, offset: Int): IO[(List[Operation], Boolean)] = {

    for {

      // We fetch limit + 1 operations to know if there's more to fetch.
      ops <-
        OperationQueries
          .fetchOperations(accountId, limit + 1, offset)
          .transact(db)
          .compile
          .toList

      // We fetch the transaction for each operation
      opsWithTx <- ops.traverse(op =>
        OperationQueries
          .fetchTx(op.accountId, op.hash)
          .transact(db)
          .map(tx => op.copy(transaction = tx))
      )

      truncated = opsWithTx.size > limit
    } yield {
      val protoOperations = opsWithTx.slice(0, limit)
      (protoOperations, truncated)
    }

    // TODO deal with ordering

  }

  def computeOperations(accountId: UUID, addresses: List[AccountAddress]): IO[Int] =
    for {

      _ <- flagInputsAndOutputs(accountId, addresses)

      hashs <-
        OperationQueries
          .fetchTxsWithoutOperations(accountId)
          .transact(db)
          .compile
          .toList

      txsO <- hashs.traverse { hash =>
        OperationQueries
          .fetchTx(accountId, hash)
          .transact(db)
      }

      txs <- IO.pure(txsO collect {
        case Some(tx) => tx
      })

      ops <-
        txs
          .flatMap { transaction =>
            OperationComputer.compute(transaction, accountId, addresses)
          }
          .traverse { operation =>
            OperationQueries
              .saveOperation(operation) // TODO use updateMany instead of map
          }
          .transact(db)

    } yield ops.sum

  private def flagInputsAndOutputs(accountId: UUID, addresses: List[AccountAddress]): IO[Int] = {
    val query = for {
      // Flag inputs with known addresses
      inputs <- OperationQueries.flagInputs(accountId, addresses.map(_.accountAddress))

      // Flag outputs with known addresses and update address type (INTERNAL / EXTERNAL)
      outputs <-
        addresses
          .traverse { address =>
            OperationQueries.flagOutputsForAddress(accountId, address)
          }
    } yield (inputs :: outputs).sum

    query.transact(db)
  }

}
