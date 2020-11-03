package co.ledger.lama.bitcoin.interpreter.services

import java.util.UUID

import cats.data.NonEmptyList

import co.ledger.lama.bitcoin.common.models.service._
import co.ledger.lama.bitcoin.interpreter.models.{OperationToSave, TransactionAmounts}
import co.ledger.lama.bitcoin.interpreter.models.implicits._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.Sort

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import fs2.{Chunk, Stream}

object OperationQueries extends IOLogging {

  def fetchTransaction(
      accountId: UUID,
      hash: String
  ): ConnectionIO[Option[TransactionView]] = {
    log.logger.debug(s"Fetching transaction for accountId $accountId and hash $hash")
    for {
      tx <- fetchTx(accountId, hash)
      _ = log.logger.debug(s"Transaction $tx")
      inputs <- fetchInputs(accountId, hash).compile.toList
      _ = log.logger.debug(s"Inputs $inputs")
      outputs <- fetchOutputs(accountId, hash).compile.toList
      _ = log.logger.debug(s"Outputs $outputs")
    } yield {
      tx.map(
        _.copy(
          inputs = inputs,
          outputs = outputs
        )
      )
    }
  }

  def fetchTxHashesWithNoOperations(
      accountId: UUID
  ): Stream[ConnectionIO, String] =
    sql"""SELECT tx.hash
          FROM transaction tx
            LEFT JOIN operation op
              ON op.hash = tx.hash
              AND op.account_id = tx.account_id
          WHERE op.hash IS NULL
          AND tx.account_id = $accountId
       """
      .query[String]
      .stream

  def fetchTransactionAmounts(
      accountId: UUID
  ): Stream[ConnectionIO, TransactionAmounts] =
    sql"""SELECT tx.account_id,
                 tx.hash,
                 tx.block_hash,
                 tx.block_height,
                 tx.block_time,
                 tx.input_amount,
                 tx.output_amount,
                 tx.change_amount
          FROM transaction_amount tx
            LEFT JOIN operation op
              ON op.hash = tx.hash
              AND op.account_id = tx.account_id
          WHERE op.hash IS NULL
          AND tx.account_id = $accountId
       """
      .query[TransactionAmounts]
      .stream

  def fetchUTXOs(
      accountId: UUID,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ): Stream[ConnectionIO, OutputView] = {
    val limitF  = limit.map(l => fr"LIMIT $l").getOrElse(Fragment.empty)
    val offsetF = offset.map(o => fr"OFFSET $o").getOrElse(Fragment.empty)

    val query =
      sql"""SELECT o.output_index, o.value, o.address, o.script_hex, o.belongs, o.change_type
            FROM output o
              LEFT JOIN input i
                ON o.account_id = i.account_id
                AND o.address = i.address
                AND o.output_index = i.output_index
			          AND o.hash = i.output_hash
            WHERE o.account_id = $accountId
              AND o.belongs = true
              AND i.address IS NULL
         """ ++ limitF ++ offsetF
    query.query[OutputView].stream
  }

  def saveOperations(operation: Chunk[OperationToSave]): ConnectionIO[Int] = {
    val query =
      """INSERT INTO operation (
         account_id, hash, operation_type, value, time, block_hash, block_height
       ) VALUES (?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT ON CONSTRAINT operation_pkey DO NOTHING
    """
    Update[OperationToSave](query).updateMany(operation)
  }

  private def fetchTx(
      accountId: UUID,
      hash: String
  ): ConnectionIO[Option[TransactionView]] =
    sql"""SELECT id, hash, block_hash, block_height, block_time, received_at, lock_time, fees, confirmations
          FROM transaction
          WHERE hash = $hash
          AND account_id = $accountId
       """
      .query[TransactionView]
      .option

  private def fetchInputs(
      accountId: UUID,
      hash: String
  ): Stream[ConnectionIO, InputView] = {
    sql"""SELECT output_hash, output_index, input_index, value, address, script_signature, sequence, belongs
          FROM input
          WHERE account_id = $accountId
          AND hash = $hash
       """
      .query[InputView]
      .stream
  }

  private def fetchOutputs(
      accountId: UUID,
      hash: String
  ): Stream[ConnectionIO, OutputView] =
    sql"""SELECT output_index, value, address, script_hex, belongs, change_type
          FROM output
          WHERE account_id = $accountId
          AND hash = $hash
       """
      .query[OutputView]
      .stream

  def fetchOperations(
      accountId: UUID,
      blockHeight: Long = 0L,
      sort: Sort = Sort.Descending,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ): Stream[ConnectionIO, Operation] = {
    val orderF  = Fragment.const(s"ORDER BY time $sort, hash $sort")
    val limitF  = limit.map(l => fr"LIMIT $l").getOrElse(Fragment.empty)
    val offsetF = offset.map(o => fr"OFFSET $o").getOrElse(Fragment.empty)

    val query =
      sql"""SELECT account_id, hash, operation_type, value, time
            FROM operation
            WHERE account_id = $accountId
            AND block_height >= $blockHeight
         """ ++ orderF ++ limitF ++ offsetF
    query.query[Operation].stream
  }

  def flagBelongingInputs(accountId: UUID, addresses: NonEmptyList[String]): ConnectionIO[Int] = {
    val query =
      sql"""UPDATE input
            SET belongs = true
            WHERE account_id = $accountId
            AND """ ++ Fragments.in(fr"address", addresses)
    query.update.run
  }

  def flagBelongingOutputs(
      accountId: UUID,
      addresses: NonEmptyList[String],
      changeType: ChangeType
  ): ConnectionIO[Int] = {
    val query =
      sql"""UPDATE output
            SET belongs = true, change_type = $changeType
            WHERE account_id = $accountId
            AND """ ++ Fragments.in(fr"address", addresses)
    query.update.run
  }
}
