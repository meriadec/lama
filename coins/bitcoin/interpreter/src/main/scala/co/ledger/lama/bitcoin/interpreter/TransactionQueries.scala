package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.implicits._
import co.ledger.lama.bitcoin.common.models.Explorer._
import co.ledger.lama.bitcoin.interpreter.models.implicits._
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

      txStatement <- prepareTxInsert(accountId, tx).update.run

      _ <- tx.inputs.toList.collect {
        case input: DefaultInput => prepareInputInsert(accountId, tx.hash, input).update.run
      }.sequence

      _ <- tx.outputs.toList.traverse(prepareOutputInsert(accountId, tx.hash, _).update.run)

    } yield {
      txStatement
    }

  private def prepareTxInsert(
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
        """
  }

  private def prepareInputInsert(
      accountId: UUID,
      txHash: String,
      input: DefaultInput
  ) = sql"""INSERT INTO input (
            account_id, hash, output_hash, output_index, input_index, value, address, script_signature, txinwitness, sequence, belongs
          ) VALUES (
            $accountId,
            $txHash,
            ${input.outputHash},
            ${input.outputIndex},
            ${input.inputIndex},
            ${input.value},
            ${input.address},
            ${input.scriptSignature},
            NULL,
            ${input.sequence},
            false -- until we know all known addresses, we consider all new transaction inputs as not belonging
          ) ON CONFLICT ON CONSTRAINT input_pkey DO NOTHING
        """

  private def prepareOutputInsert(
      accountId: UUID,
      txHash: String,
      output: Output
  ) = sql"""INSERT INTO output (
            account_id, hash, output_index, value, address, script_hex, belongs, change_type
          ) VALUES (
            $accountId,
            $txHash,
            ${output.outputIndex},
            ${output.value},
            ${output.address},
            ${output.scriptHex},
            false, -- until we know all known addresses, we consider all new transaction outputs as not belonging
            NULL   -- and so there is no changeType
          ) ON CONFLICT ON CONSTRAINT output_pkey DO NOTHING
        """

}
