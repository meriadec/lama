package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.implicits._
import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.interpreter.models.implicits._
import doobie.Update
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

object TransactionQueries {

  def upsertBlock(block: Block): ConnectionIO[Int] =
    sql"""INSERT INTO block (
            hash, height, time
          ) VALUES (
            ${block.hash}, ${block.height}, ${block.time}
          )
          ON CONFLICT ON CONSTRAINT block_pkey DO NOTHING;
        """.update.run

  def saveTransaction(tx: Transaction, accountId: UUID): ConnectionIO[Int] =
    for {

      txStatement <- insertTx(accountId, tx)

      _ <- insertInputs(
        accountId,
        tx.hash,
        tx.inputs.toList.collect {
          case input: DefaultInput => input
        }
      )

      _ <- insertOutpus(accountId, tx.hash, tx.outputs.toList)

    } yield {
      txStatement
    }

  private def insertTx(
      accountId: UUID,
      tx: Transaction
  ) = {
    sql"""INSERT INTO transaction (
            account_id, id, hash, received_at, lock_time, fees, block_hash, confirmations
          ) VALUES (
            $accountId,
            ${tx.id},
            ${tx.hash},
            ${tx.receivedAt},
            ${tx.lockTime},
            ${tx.fees},
            ${tx.block.hash},
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

  private def insertOutpus(
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

  def deleteTransactions(accountId: UUID, blockHeight: Long): ConnectionIO[Int] =
    sql"""DELETE from transaction t
          USING block b
          WHERE t.block_hash = b.hash
          AND t.account_id = $accountId
          AND b.height >= $blockHeight""".update.run
}
