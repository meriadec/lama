package co.ledger.lama.bitcoin.interpreter.services

import java.time.Instant
import java.util.UUID
import co.ledger.lama.bitcoin.common.models.worker.Block
import co.ledger.lama.bitcoin.common.models.interpreter.BalanceHistory
import co.ledger.lama.bitcoin.interpreter.models.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import fs2.Stream

object BalanceQueries {

  case class OperationToBalanceHistory(time: Instant, value: BigInt, sent: BigInt, received: BigInt)

  def getOperationsForBalanceHistory(
      accountId: UUID
  ): Stream[ConnectionIO, OperationToBalanceHistory] =
    sql"""SELECT
            time,
            SUM( ( case when operation_type = 'sent' then -1 else 1 end ) * value) as value,
            SUM( ( case when operation_type = 'sent' then 1 else 0 end ) * value) as sent,
            SUM( ( case when operation_type = 'sent' then 0 else 1 end ) * value) as received
          FROM operation
          WHERE account_id = $accountId
          GROUP BY time
          ORDER BY time"""
      .query[OperationToBalanceHistory]
      .stream

  def getCurrentBalance(
      accountId: UUID
  ): ConnectionIO[BalanceHistory] = {
    val balanceAndUtxosQuery =
      sql"""SELECT COALESCE(SUM(o.value), 0), COALESCE(COUNT(o.value), 0)
          FROM output o
            LEFT JOIN input i
              ON o.account_id = i.account_id
              AND o.address = i.address
              AND o.output_index = i.output_index
			        AND o.hash = i.output_hash
          WHERE o.account_id = $accountId
            AND o.derivation IS NOT NULL
            AND i.address IS NULL
      """
        .query[(BigInt, Int)]
        .unique

    val receivedAndSentQuery =
      sql"""SELECT
              COALESCE(SUM(CASE WHEN operation_type = 'received' THEN value ELSE 0 END), 0) as received,
              COALESCE(SUM(CASE WHEN operation_type = 'sent' THEN value ELSE 0 END), 0) as sent
            FROM operation
            WHERE account_id = $accountId
         """
        .query[(BigInt, BigInt)]
        .unique

    for {
      result1 <- balanceAndUtxosQuery
      result2 <- receivedAndSentQuery
    } yield {
      val (balance, utxos) = result1
      val (received, sent) = result2
      BalanceHistory(balance, utxos, received, sent)
    }
  }

  def saveBalanceHistory(
      accountId: UUID,
      b: BalanceHistory,
      blockHeight: Long
  ): ConnectionIO[BalanceHistory] =
    sql"""INSERT INTO balance_history(account_id, balance, utxos, received, sent, block_height)
          VALUES($accountId, ${b.balance}, ${b.utxos}, ${b.received}, ${b.sent}, $blockHeight)
          RETURNING balance, utxos, received, sent, time ;
       """.query[BalanceHistory].unique

  def getBalancesHistory(
      accountId: UUID,
      start: Instant,
      end: Instant
  ): Stream[ConnectionIO, BalanceHistory] =
    sql"""SELECT balance, utxos, received, sent, time
          FROM balance_history
          WHERE account_id = $accountId
          AND time >= $start
          AND time <= $end
          ORDER BY time
       """.query[BalanceHistory].stream

  def getBalancesHistoryCount(
      accountId: UUID
  ): ConnectionIO[Int] =
    sql"""SELECT COUNT(balance)
          FROM balance_history
          WHERE account_id = $accountId
       """.query[Int].unique

  def removeBalancesHistoryFromCursor(accountId: UUID, blockHeight: Long): ConnectionIO[Int] =
    sql"""DELETE from balance_history
          WHERE account_id = $accountId
          AND block_height >= $blockHeight
       """.update.run

  def getLastBlock(accountId: UUID): ConnectionIO[Block] =
    sql"""SELECT DISTINCT block_hash, block_height, block_time
          FROM transaction
          WHERE account_id = $accountId
          ORDER BY block_height DESC
          LIMIT 1
       """.query[Block].unique
}
