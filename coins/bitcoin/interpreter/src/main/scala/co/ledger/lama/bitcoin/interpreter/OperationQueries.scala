package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.data.NonEmptyList
import cats.implicits._
import cats.free.Free
import co.ledger.lama.bitcoin.common.models.service._
import co.ledger.lama.common.logging.IOLogging
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import co.ledger.lama.bitcoin.interpreter.models.implicits._
import co.ledger.lama.common.models.Sort
import doobie.free.connection

object OperationQueries extends IOLogging {

  def fetchTx(
      accountId: UUID,
      hash: String
  ): Free[connection.ConnectionOp, Option[TransactionView]] = {
    log.logger.debug(s"Fetching transaction for accountId $accountId and hash $hash")
    for {
      tx <- fetchTxAndBlock(accountId, hash)
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

  def fetchTxsWithoutOperations(
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
          WHERE o.account_id = $accountId
            AND o.belongs = true
            AND i.address IS NULL
      """ ++ limitF ++ offsetF
    query.query[OutputView].stream
  }

  private def fetchTxAndBlock(
      accountId: UUID,
      hash: String
  ): ConnectionIO[Option[TransactionView]] =
    sql"""SELECT tx.id, tx.hash, tx.received_at, tx.lock_time, tx.fees, tx.block_hash, tx.confirmations, bk.height, bk.time
          FROM transaction tx INNER JOIN block bk ON tx.block_hash = bk.hash
          WHERE tx.hash = $hash
          AND tx.account_id = $accountId
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
      sort: Sort = Sort.Descending,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ): fs2.Stream[doobie.ConnectionIO, Operation] = {
    val orderF  = Fragment.const(s"ORDER BY time $sort")
    val limitF  = limit.map(l => fr"LIMIT $l").getOrElse(Fragment.empty)
    val offsetF = offset.map(o => fr"OFFSET $o").getOrElse(Fragment.empty)

    val query = sql"""SELECT account_id, hash, operation_type, value, time
          FROM operation
          WHERE account_id = $accountId
          """ ++ orderF ++ limitF ++ offsetF
    query.query[Operation].stream
  }

  def saveOperations(operation: List[Operation]): ConnectionIO[Int] = {
    val query = """INSERT INTO operation (
            account_id, hash, operation_type, value, time
          ) VALUES (?, ?, ?, ?, ?)
          ON CONFLICT ON CONSTRAINT operation_pkey DO NOTHING
        """
    Update[Operation](query).updateMany(operation)
  }

  def flagInputs(accountId: UUID, addresses: List[String]): ConnectionIO[Int] = {
    val query = sql"""UPDATE input
          SET belongs = true
          WHERE account_id = $accountId
          AND belongs = false
          AND """ ++ Fragments.in(fr"address", NonEmptyList.fromListUnsafe(addresses))
    query.update.run
  }

  def flagOutputsForAddress(accountId: UUID, address: AccountAddress): ConnectionIO[Int] = {
    sql"""UPDATE output
          SET belongs = true, change_type = ${address.changeType}
          WHERE account_id = $accountId
          AND belongs = false
          AND address = ${address.accountAddress}
       """.update.run
  }
}
