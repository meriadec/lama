package co.ledger.lama.bitcoin.interpreter.services

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

import co.ledger.lama.bitcoin.common.models.explorer.Block
import co.ledger.lama.bitcoin.common.models.service.BalanceHistory

import co.ledger.lama.bitcoin.interpreter.models.implicits._

import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._

import fs2.Stream

object BalanceQueries {

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
            AND o.belongs = true
            AND i.address IS NULL
      """
        .query[(BigDecimal, Int)]
        .unique

    val receivedAndSentQuery =
      sql"""SELECT
              COALESCE(SUM(CASE WHEN operation_type = 'received' THEN value ELSE 0 END), 0) as received,
              COALESCE(SUM(CASE WHEN operation_type = 'sent' THEN value ELSE 0 END), 0) as sent
            FROM operation
            WHERE account_id = $accountId
         """
        .query[(BigDecimal, BigDecimal)]
        .unique

    for {
      result1 <- balanceAndUtxosQuery
      result2 <- receivedAndSentQuery
    } yield {
      val (balance, utxos) = result1
      val (received, sent) = result2
      BalanceHistory(balance.toBigInt, utxos, received.toBigInt, sent.toBigInt)
    }
  }

  def saveBalanceHistory(accountId: UUID, b: BalanceHistory, blockHeight: Long): ConnectionIO[Int] =
    sql"""INSERT INTO balance_history(account_id, balance, utxos, received, sent, block_height)
          VALUES($accountId, ${b.balance}, ${b.utxos}, ${b.received}, ${b.sent}, $blockHeight)
       """.update.run

  def getBalancesHistory(
      accountId: UUID,
      start: Instant,
      end: Instant
  ): Stream[ConnectionIO, BalanceHistory] = {
    val startTs = Timestamp.from(start)
    val endTs   = Timestamp.from(end)
    sql"""SELECT balance, utxos, received, sent, time
          FROM balance_history
          WHERE account_id = $accountId
          AND time >= $startTs
          AND time <= $endTs
          ORDER BY time
       """.query[BalanceHistory].stream
  }

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