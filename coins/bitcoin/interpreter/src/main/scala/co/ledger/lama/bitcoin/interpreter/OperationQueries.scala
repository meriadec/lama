package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.data.NonEmptyList
import cats.free.Free
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.service._
import co.ledger.lama.bitcoin.interpreter.models.{OperationFull, OperationToSave}
import co.ledger.lama.common.logging.IOLogging
import doobie.{ConnectionIO, _}
import doobie.implicits._
import doobie.postgres.implicits._
import co.ledger.lama.bitcoin.interpreter.models.implicits._
import co.ledger.lama.common.models.Sort

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
  ): fs2.Stream[doobie.ConnectionIO, String] =
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

  def fetchTxWithComputedAmount(
      accountId: UUID
  ): fs2.Stream[doobie.ConnectionIO, OperationFull] =
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
      .query[OperationFull]
      .stream

  def fetchUTXOs(
      accountId: UUID,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ): fs2.Stream[doobie.ConnectionIO, OutputView] = {
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

  def fetchBalance(
      accountId: UUID
  ): ConnectionIO[(Int, BigInt)] = {
    sql"""SELECT COUNT(o.value), SUM(COALESCE(o.value, 0))
          FROM output o
            LEFT JOIN input i
              ON o.account_id = i.account_id
              AND o.address = i.address
              AND o.output_index = i.output_index
			        AND o.hash = i.output_hash
          WHERE o.account_id = $accountId
            AND o.belongs = true
            AND i.address IS NULL
      """
      .query[(Option[Int], Option[BigDecimal])]
      .map {
        case (count, balance) => (count.getOrElse(0), balance.getOrElse(BigDecimal(0)).toBigInt)
      }
      .unique
  }

  def fetchSpendAndReceivedAmount(
      accountId: UUID
  ): doobie.ConnectionIO[(BigInt, BigInt)] = {
    val query = sql"""SELECT
                  SUM(CASE WHEN operation_type = 'sent' THEN value ELSE 0 END) as sent,
                  SUM(CASE WHEN operation_type = 'received' THEN value ELSE 0 END) as received
          FROM operation
          WHERE account_id = $accountId
          """
    query
      .query[(Option[BigDecimal], Option[BigDecimal])]
      .map {
        case (sent, received) =>
          (sent.getOrElse(BigDecimal(0)).toBigInt, received.getOrElse(BigDecimal(0)).toBigInt)
      }
      .unique
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
  ): fs2.Stream[doobie.ConnectionIO, InputView] = {
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
  ): fs2.Stream[doobie.ConnectionIO, OutputView] =
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
  ): fs2.Stream[doobie.ConnectionIO, Operation] = {
    val orderF  = Fragment.const(s"ORDER BY time $sort, hash $sort")
    val limitF  = limit.map(l => fr"LIMIT $l").getOrElse(Fragment.empty)
    val offsetF = offset.map(o => fr"OFFSET $o").getOrElse(Fragment.empty)

    val query = sql"""SELECT account_id, hash, operation_type, value, time
          FROM operation
          WHERE account_id = $accountId
          AND block_height >= $blockHeight
          """ ++ orderF ++ limitF ++ offsetF
    query.query[Operation].stream
  }

  def saveOperations(operation: List[OperationToSave]): ConnectionIO[Int] = {
    val query = """INSERT INTO operation (
            account_id, hash, operation_type, value, time, block_hash, block_height
          ) VALUES (?, ?, ?, ?, ?, ?, ?)
          ON CONFLICT ON CONSTRAINT operation_pkey DO NOTHING
        """
    Update[OperationToSave](query).updateMany(operation)
  }

  def flagBelongingInputs(accountId: UUID, addresses: List[String]): ConnectionIO[Int] = {
    addresses match {
      case Nil => Free.pure(0)
      case list =>
        val query =
          sql"""UPDATE input
          SET belongs = true
          WHERE account_id = $accountId
          AND """ ++ Fragments.in(fr"address", NonEmptyList.fromListUnsafe(list))
        query.update.run
    }
  }

  def flagBelongingOutputs(
      accountId: UUID,
      addresses: List[String],
      changeType: ChangeType
  ): ConnectionIO[Int] = {
    addresses match {
      case Nil => Free.pure(0)
      case list =>
        val query = sql"""UPDATE output
          SET belongs = true, change_type = $changeType
          WHERE account_id = $accountId
          AND """ ++ Fragments.in(fr"address", NonEmptyList.fromListUnsafe(list))
        query.update.run
    }
  }
}
