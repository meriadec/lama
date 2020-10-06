package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.implicits._
import co.ledger.lama.bitcoin.common.models._
import doobie._
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

object Queries {

  implicit val bigIntType: Meta[BigInt] = Meta.Advanced.other[BigInt]("bigint")
  implicit val meta: Meta[OperationType] =
    pgEnumStringOpt("operation_type", OperationType.fromKey, _.toString.toLowerCase())

  def fetchTx(
      accountId: UUID,
      hash: String
  ): ConnectionIO[Option[Transaction]] =
    sql"""SELECT id, hash, received_at, lock_time, fees, block_hash, confirmations
          FROM transaction
          WHERE hash = $hash
          AND account_id = ${accountId.toString}
          """
      .query[(String, String, String, Long, Long, String, Int)]
      .map {
        case (id, hash, receivedAt, lockTime, fees, blockHash, confirmations) =>
          Transaction(
            id = id,
            hash = hash,
            receivedAt = receivedAt,
            lockTime = lockTime,
            fees = fees,
            inputs = Seq(),
            outputs = Seq(),
            block = Block(blockHash, 0, "", None),
            confirmations = confirmations
          )
      }
      .option

  def fetchUncomputedTxs(
      accountId: UUID
  ): fs2.Stream[doobie.ConnectionIO, Transaction] =
    sql"""SELECT id, hash, received_at, lock_time, fees, block_hash, confirmations
          FROM transaction
            LEFT JOIN operation 
              ON operation.hash IS NULL
              AND account_id = ${accountId.toString}
          """
      .query[(String, String, String, Long, Long, String, Int)]
      .map {
        case (id, hash, receivedAt, lockTime, fees, block_hash, confirmations) =>
          Transaction(
            id = id,
            hash = hash,
            receivedAt = receivedAt,
            lockTime = lockTime,
            fees = fees,
            inputs = Seq(),
            outputs = Seq(),
            block = Block(block_hash, 0, "", None),
            confirmations = confirmations
          )
      }
      .stream

  def populateTx(tx: Transaction, accountId: UUID): ConnectionIO[Transaction] = {
    for {
      block   <- fetchBlock(tx.block.hash)
      inputs  <- fetchInputs(accountId, tx.hash).compile.toList
      outputs <- fetchOutputs(accountId, tx.hash).compile.toList
    } yield {
      tx.copy(
        block = block,
        inputs = inputs,
        outputs = outputs
      )
    }
  }

  private def fetchBlock(hash: String): doobie.ConnectionIO[Block] = {
    sql"""SELECT hash, height, time
          FROM block
          WHERE hash = $hash
          """
      .query[(String, Long, String)]
      .map {
        case (hash, height, time) =>
          Block(hash = hash, height = height, time = time)
      }
      .unique
  }

  private def fetchInputs(
      accountId: UUID,
      hash: String
  ): fs2.Stream[doobie.ConnectionIO, DefaultInput] = {
    sql"""SELECT output_hash, output_index, input_index, value, address, script_signature, sequence
          FROM input
          WHERE account_id = ${accountId.toString}
          AND hash = $hash
          """
      .query[(String, Int, Int, Long, String, String, Long)]
      .map {
        case (
              outputHash,
              outputIndex,
              inputIndex,
              value,
              address,
              scriptSignature,
              sequence
            ) =>
          DefaultInput(
            outputHash = outputHash,
            outputIndex = outputIndex,
            inputIndex = inputIndex,
            value = value,
            address = address,
            scriptSignature = scriptSignature,
            txinwitness = Seq(),
            sequence = sequence
          )
      }
      .stream
  }

  private def fetchOutputs(
      accountId: UUID,
      hash: String
  ): fs2.Stream[doobie.ConnectionIO, Output] =
    sql"""SELECT output_index, value, address, script_hex
          FROM output
          WHERE account_id = ${accountId.toString}
          AND hash = $hash
          """
      .query[(Int, Long, String, String)]
      .map {
        case (outputIndex, value, address, scriptHex) =>
          Output(outputIndex = outputIndex, value = value, address = address, scriptHex = scriptHex)
      }
      .stream

  def fetchOperations(
      accountId: UUID,
      limit: Int = 20,
      offset: Int = 0
  ): fs2.Stream[doobie.ConnectionIO, Operation] =
    sql"""SELECT account_id, hash, operation_type, amount, time
          FROM operation
          WHERE account_id = ${accountId.toString}
          LIMIT $limit 
          OFFSET $offset
          """
      .query[(String, String, OperationType, Long, String)]
      .map {
        case (accountId, hash, operationType, value, time) =>
          Operation(UUID.fromString(accountId), hash, None, operationType, value, time)
      }
      .stream

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

      txStatement <- sql"""INSERT INTO transaction (
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

      _ <- tx.inputs.toList.collect {
        case input: DefaultInput => prepareInputInsert(accountId, tx.hash, input).update.run
      }.sequence
      _ <- tx.outputs.toList.traverse(prepareOutputInsert(accountId, tx.hash, _).update.run)

    } yield {
      txStatement
    }

  def saveOperation(operation: Operation): doobie.ConnectionIO[Int] =
    sql"""INSERT INTO operation (
            account_id, hash, operation_type, amount, time
          ) VALUES (
            ${operation.accountId.toString},
            ${operation.hash},
            ${operation.operationType},
            ${operation.value},
            ${operation.time}
          )
        """.update.run

  private def prepareInputInsert(
      accountId: UUID,
      txHash: String,
      input: DefaultInput
  ) = sql"""INSERT INTO input (
            account_id, hash, output_hash, output_index, input_index, value, address, script_signature, txinwitness, sequence
          ) VALUES (
            ${accountId.toString},
            $txHash,
            ${input.outputHash},
            ${input.outputIndex},
            ${input.inputIndex},
            ${input.value},
            ${input.address},
            ${input.scriptSignature},
            NULL,
            ${input.sequence}
          ) ON CONFLICT ON CONSTRAINT input_pkey DO NOTHING
        """

  private def prepareOutputInsert(
      accountId: UUID,
      txHash: String,
      output: Output
  ) = sql"""INSERT INTO output (
            account_id, hash, output_index, value, address, script_hex
          ) VALUES (
            ${accountId.toString},
            $txHash,
            ${output.outputIndex},
            ${output.value},
            ${output.address},
            ${output.scriptHex}
          ) ON CONFLICT ON CONSTRAINT output_pkey DO NOTHING
        """

}
