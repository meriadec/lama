package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.service.{
  AccountAddress,
  AccountBalance,
  External,
  Internal,
  Operation,
  OutputView
}
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

  def computeOperations(
      accountId: UUID,
      addresses: List[AccountAddress]
  )(implicit cs: ContextShift[IO]): IO[Int] =
    for {
      _        <- log.info(s"Flagging inputs and outputs belong to account=$accountId")
      _        <- flagInputsAndOutputs(accountId, addresses)
      _        <- log.info("Computing and saving ops")
      opsCount <- computeAndSaveOperations(accountId)
    } yield opsCount

  private def computeAndSaveOperations(
      accountId: UUID
  )(implicit cs: ContextShift[IO]): IO[Int] =
    OperationQueries
      .fetchTxWithComputedAmount(accountId)
      .transact(db)
      .chunkN(100)
      .parEvalMapUnordered(maxConcurrent) { chunkOps =>
        OperationQueries.saveOperations(chunkOps.toList.flatMap(_.computeOperations())).transact(db)
      }
      .compile
      .fold(0)(_ + _)

  private def flagInputsAndOutputs(
      accountId: UUID,
      addresses: List[AccountAddress]
  )(implicit cs: ContextShift[IO]): IO[Unit] = {

    val (internalAddresses, externalAddresses) = addresses.partition(_.changeType == Internal)

    val flagInputs =
      OperationQueries
        .flagBelongingInputs(
          accountId,
          addresses.map(_.accountAddress)
        )
        .transact(db)

    // Flag outputs with known addresses and update address type (INTERNAL / EXTERNAL)
    val flagInternalOutputs = OperationQueries
      .flagBelongingOutputs(
        accountId,
        internalAddresses.map(_.accountAddress),
        Internal
      )
      .transact(db)

    val flagExternalOutputs = OperationQueries
      .flagBelongingOutputs(
        accountId,
        externalAddresses.map(_.accountAddress),
        External
      )
      .transact(db)

    (flagInputs &> flagInternalOutputs &> flagExternalOutputs).void
  }

  // TODO: optimize (compute the sum in sql)
  def getBalance(accountId: UUID): IO[AccountBalance] =
    for {
      utxoBalances <- OperationQueries.fetchBalance(accountId).transact(db)
      opAmounts    <- OperationQueries.fetchSpendAndReceivedAmount(accountId).transact(db)
    } yield {
      val (count, balance)             = utxoBalances
      val (amountSent, amountReceived) = opAmounts

      AccountBalance(
        balance,
        count,
        amountSent,
        amountReceived
      )
    }

}
