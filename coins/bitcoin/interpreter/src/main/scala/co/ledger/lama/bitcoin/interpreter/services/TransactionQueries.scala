package co.ledger.lama.bitcoin.interpreter.services

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.explorer.{
  Block,
  ConfirmedTransaction,
  DefaultInput,
  Output
}

import co.ledger.lama.bitcoin.interpreter.models.implicits._

import doobie._
import doobie.implicits._
import doobie.postgres.implicits._

import fs2.Stream

object TransactionQueries {

  def fetchMostRecentBlocks(accountId: UUID): Stream[ConnectionIO, Block] = {
    sql"""SELECT DISTINCT block_hash, block_height, block_time
          FROM transaction
          WHERE account_id = $accountId
          ORDER BY block_height DESC
          LIMIT 200 -- the biggest reorg that happened on bitcoin was 53 blocks long
       """.query[Block].stream
  }

  def saveTransaction(tx: ConfirmedTransaction, accountId: UUID): ConnectionIO[Int] =
    for {

      txStatement <- insertTx(accountId, tx)

      _ <- insertInputs(
        accountId,
        tx.hash,
        tx.inputs.toList.collect { case input: DefaultInput =>
          input
        }
      )

      _ <- insertOutputs(accountId, tx.hash, tx.outputs.toList)

    } yield {
      txStatement
    }

  private def insertTx(
      accountId: UUID,
      tx: ConfirmedTransaction
  ) = {
    sql"""INSERT INTO transaction (
            account_id, id, hash, block_hash, block_height, block_time, received_at, lock_time, fees, confirmations
          ) VALUES (
            $accountId,
            ${tx.id},
            ${tx.hash},
            ${tx.block.hash},
            ${tx.block.height},
            ${tx.block.time},
            ${tx.receivedAt},
            ${tx.lockTime},
            ${tx.fees},
            ${tx.confirmations}
          ) ON CONFLICT ON CONSTRAINT transaction_pkey DO NOTHING
       """.update.run
  }

  private def insertInputs(
      accountId: UUID,
      txHash: String,
      inputs: List[DefaultInput]
  ) = {
    val query =
      s"""INSERT INTO input (
            account_id, hash, output_hash, output_index, input_index, value, address, script_signature, txinwitness, sequence, belongs
          ) VALUES ('$accountId', '$txHash', ?, ?, ?, ?, ?, ?, ?, ?, false)
          ON CONFLICT ON CONSTRAINT input_pkey DO NOTHING
       """
    Update[DefaultInput](query).updateMany(inputs)
  }

  private def insertOutputs(
      accountId: UUID,
      txHash: String,
      outputs: List[Output]
  ) = {
    val query = s"""INSERT INTO output (
            account_id, hash, output_index, value, address, script_hex, belongs, change_type
          ) VALUES (
            '$accountId',
            '$txHash',
            ?,
            ?,
            ?,
            ?,
            false, -- until we know all known addresses, we consider all new transaction outputs as not belonging
            NULL   -- and so there is no changeType
          ) ON CONFLICT ON CONSTRAINT output_pkey DO NOTHING
        """
    Update[Output](query).updateMany(outputs)
  }

  def removeFromCursor(accountId: UUID, blockHeight: Long): ConnectionIO[Int] =
    sql"""DELETE from transaction
          WHERE account_id = $accountId
          AND block_height >= $blockHeight
       """.update.run
}
