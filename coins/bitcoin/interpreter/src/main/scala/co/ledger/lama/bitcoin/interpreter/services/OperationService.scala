package co.ledger.lama.bitcoin.interpreter.services

import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.interpreter.{
  GetOperationsResult,
  GetUtxosResult,
  TransactionView,
  Utxo
}
import co.ledger.lama.bitcoin.interpreter.models.{OperationToSave, TransactionAmounts}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.Sort
import doobie._
import doobie.implicits._
import fs2._

class OperationService(
    db: Transactor[IO],
    maxConcurrent: Int
) extends IOLogging {

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Sort
  )(implicit cs: ContextShift[IO]): IO[GetOperationsResult] =
    for {
      opsWithTx <-
        OperationQueries
          .fetchOperations(accountId, blockHeight, sort, Some(limit + 1), Some(offset))
          .transact(db)
          .parEvalMap(maxConcurrent) { op =>
            OperationQueries
              .fetchTransaction(op.accountId, op.hash)
              .transact(db)
              .map(tx => op.copy(transaction = tx))
          }
          .compile
          .toList

      total <- OperationQueries.countOperations(accountId, blockHeight).transact(db)

      // we get 1 more than necessary to know if there's more, then we return the correct number
      truncated = opsWithTx.size > limit

    } yield {
      val operations = opsWithTx.slice(0, limit)
      GetOperationsResult(operations, total, truncated)
    }

  def deleteUnconfirmedTransactionView(accountId: UUID): IO[Int] =
    OperationQueries
      .deleteUnconfirmedTransactionsViews(accountId)
      .transact(db)

  def saveUnconfirmedTransactionView(
      accountId: UUID,
      transactions: List[TransactionView]
  ): IO[Int] =
    OperationQueries
      .saveUnconfirmedTransactionView(accountId, transactions)
      .transact(db)

  def deleteUnconfirmedOperations(accountId: UUID): IO[Int] =
    OperationQueries
      .deleteUnconfirmedOperations(accountId)
      .transact(db)

  def getUTXOs(
      accountId: UUID,
      sort: Sort,
      limit: Int,
      offset: Int
  ): IO[GetUtxosResult] =
    for {
      confirmedUtxos <-
        OperationQueries
          .fetchUTXOs(accountId, sort, Some(limit + 1), Some(offset))
          .transact(db)
          .compile
          .toList

      // Flag utxos used in the mempool
      unconfirmedTxs <-
        OperationQueries
          .fetchUnconfirmedTransactionsViews(accountId)
          .transact(db)

      unconfirmedInputs = unconfirmedTxs.flatMap(_.inputs).filter(_.belongs)

      utxos = confirmedUtxos.map(utxo =>
        utxo.copy(
          usedInMempool = unconfirmedInputs.exists(input =>
            input.outputHash == utxo.transactionHash && input.outputIndex == utxo.outputIndex
          )
        )
      )

      total <- OperationQueries.countUTXOs(accountId).transact(db)

      // We get 1 more than necessary to know if there's more, then we return the correct number
      truncated = utxos.size > limit

    } yield GetUtxosResult(utxos.slice(0, limit), total, truncated)

  def getUnconfirmedUtxos(accountId: UUID): IO[List[Utxo]] = {
    for {
      unconfirmedTxs <-
        OperationQueries
          .fetchUnconfirmedTransactionsViews(accountId)
          .transact(db)

      inputs    = unconfirmedTxs.flatMap(_.inputs).filter(_.belongs)
      outputMap = unconfirmedTxs.map(tx => (tx, tx.outputs.filter(_.belongs)))

      unspentOutputs = outputMap
        .flatMap { case (tx, outputs) =>
          outputs
            .filterNot(output =>
              inputs.exists(input =>
                input.outputHash == tx.hash && input.outputIndex == output.outputIndex
              )
            )
            .map(output =>
              Utxo(
                tx.hash,
                output.outputIndex,
                output.value,
                output.address,
                output.scriptHex,
                output.changeType,
                output.derivation.get,
                tx.receivedAt
              )
            )
        }

    } yield unspentOutputs
  }

  def removeFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    OperationQueries.removeFromCursor(accountId, blockHeight).transact(db)

  def compute(accountId: UUID): Stream[IO, OperationToSave] =
    operationSource(accountId)
      .flatMap(op => Stream.chunk(op.computeOperations))

  private def operationSource(accountId: UUID): Stream[IO, TransactionAmounts] =
    OperationQueries
      .fetchTransactionAmounts(accountId)
      .transact(db)

  def saveOperationSink(implicit cs: ContextShift[IO]): Pipe[IO, OperationToSave, OperationToSave] =
    in =>
      in.chunkN(1000) // TODO : in conf
        .prefetch
        .parEvalMapUnordered(maxConcurrent) { batch =>
          OperationQueries.saveOperations(batch).transact(db).map(_ => batch)
        }
        .flatMap(Stream.chunk)

}
