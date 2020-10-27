package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.data.NonEmptyList
import fs2.{Pipe, Stream}
import cats.effect.{ContextShift, IO}
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.service.{
  AccountAddress,
  AccountBalance,
  External,
  Internal,
  Operation,
  OutputView
}
import co.ledger.lama.bitcoin.interpreter.models.{OperationToSave, TransactionAmounts}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.Sort
import doobie.Transactor
import doobie.implicits._

class OperationInterpreter(db: Transactor[IO], maxConcurrent: Int) extends IOLogging {

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Sort
  )(implicit cs: ContextShift[IO]): IO[(List[Operation], Boolean)] =
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

      // we get 1 more than necessary to know if there's more, then we return the correct number
      truncated = opsWithTx.size > limit
    } yield {
      val operations = opsWithTx.slice(0, limit)
      (operations, truncated)
    }

  def getUTXOs(accountId: UUID, limit: Int, offset: Int): IO[(List[OutputView], Boolean)] =
    for {
      utxos <-
        OperationQueries
          .fetchUTXOs(accountId, Some(limit + 1), Some(offset))
          .transact(db)
          .compile
          .toList

      // we get 1 more than necessary to know if there's more, then we return the correct number
      truncated = utxos.size > limit
    } yield (utxos.slice(0, limit), truncated)

  // TODO : Stream this
  def computeOperations(
      accountId: UUID,
      addresses: List[AccountAddress]
  )(implicit cs: ContextShift[IO]): IO[Int] =
    for {
      _ <- log.info(s"Flagging inputs and outputs belong to account=$accountId")
      _ <- flagInputsAndOutputs(accountId, addresses)
      _ <- log.info("Computing and saving ops")
      opsCount <-
        operationSource(accountId)
          .flatMap(op => Stream.chunk(op.computeOperations()))
          .through(saveOperationSink)
          .compile
          .fold(0)(_ + _)
    } yield opsCount

  private def operationSource(accountId: UUID): Stream[IO, TransactionAmounts] =
    OperationQueries
      .fetchTransactionAmounts(accountId)
      .transact(db)

  private def saveOperationSink(implicit cs: ContextShift[IO]): Pipe[IO, OperationToSave, Int] =
    in =>
      in.chunkN(1000) // TODO : in conf
        .prefetch
        .parEvalMapUnordered(maxConcurrent) { batch =>
          OperationQueries.saveOperations(batch).transact(db)
        }

  private def flagInputsAndOutputs(
      accountId: UUID,
      accountAddresses: List[AccountAddress]
  )(implicit cs: ContextShift[IO]): IO[Unit] = {

    val (internalAddresses, externalAddresses) =
      accountAddresses
        .partition(_.changeType == Internal)
        .bimap(_.map(_.accountAddress), _.map(_.accountAddress))

    val flagInputs =
      NonEmptyList
        .fromList(accountAddresses.map(_.accountAddress))
        .map { addresses =>
          OperationQueries
            .flagBelongingInputs(
              accountId,
              addresses
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    // Flag outputs with known addresses and update address type (INTERNAL / EXTERNAL)
    val flagInternalOutputs =
      NonEmptyList
        .fromList(internalAddresses)
        .map { addresses =>
          OperationQueries
            .flagBelongingOutputs(
              accountId,
              addresses,
              Internal
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    val flagExternalOutputs =
      NonEmptyList
        .fromList(externalAddresses)
        .map { addresses =>
          OperationQueries
            .flagBelongingOutputs(
              accountId,
              addresses,
              External
            )
            .transact(db)
        }
        .getOrElse(IO.pure(0))

    (flagInputs, flagInternalOutputs, flagExternalOutputs).parTupled.void
  }

  // TODO: optimize (compute the sum in sql)
  def getBalance(accountId: UUID): IO[AccountBalance] =
    for {
      utxoBalance <- OperationQueries.fetchBalance(accountId).transact(db)
      opAmounts   <- OperationQueries.fetchSpendAndReceivedAmount(accountId).transact(db)
    } yield {
      AccountBalance(
        utxoBalance.balance,
        utxoBalance.utxoCount,
        opAmounts.sent,
        opAmounts.received
      )
    }

}
