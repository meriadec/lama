package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.data.NonEmptyList
import co.ledger.lama.bitcoin.common.models.Service._
import co.ledger.lama.common.logging.IOLogging
import doobie.implicits._
import doobie.postgres.implicits._
import doobie._

object OperationQueries extends IOLogging {

  implicit val bigIntType: Meta[BigInt] = Meta.Advanced.other[BigInt]("bigint")

  implicit val operationTypeMeta: Meta[OperationType] =
    pgEnumStringOpt("operation_type", OperationType.fromKey, _.toString.toLowerCase())

  implicit val changeTypeMeta: Meta[ChangeType] =
    pgEnumStringOpt("change_type", ChangeType.fromKey, _.toString.toLowerCase())

  def fetchTx(accountId: UUID, hash: String) = {
    log.info(s"Fetching transaction for accountId $accountId and hash $hash")

    for {
      tx <- fetchTxAndBlock(accountId, hash)
      _ = log.debug(s"Transaction $tx")
      inputs <- fetchInputs(accountId, hash).compile.toList
      _ = log.debug(s"Inputs $inputs")
      outputs <- fetchOutputs(accountId, hash).compile.toList
      _ = log.debug(s"Outputs $outputs")
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

  private def fetchTxAndBlock(
      accountId: UUID,
      hash: String
  ): ConnectionIO[Option[TransactionView]] =
    sql"""SELECT tx.id, tx.hash, tx.received_at, tx.lock_time, tx.fees, tx.block_hash, tx.confirmations, bk.height, bk.time
          FROM transaction tx INNER JOIN block bk ON tx.block_hash = bk.hash
          WHERE tx.hash = $hash
          AND tx.account_id = $accountId
          """
      .query[(String, String, String, Long, Long, String, Int, Long, String)]
      .map {
        case (
              id,
              hash,
              receivedAt,
              lockTime,
              fees,
              blockHash,
              confirmations,
              blockHeight,
              blockTime
            ) =>
          TransactionView(
            id = id,
            hash = hash,
            receivedAt = receivedAt,
            lockTime = lockTime,
            fees = fees,
            inputs = Seq(),
            outputs = Seq(),
            block = BlockView(blockHash, blockHeight, blockTime, None),
            confirmations = confirmations
          )
      }
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
      .query[(String, Int, Int, Long, String, String, Long, Boolean)]
      .map {
        case (
              outputHash,
              outputIndex,
              inputIndex,
              value,
              address,
              scriptSignature,
              sequence,
              belongs
            ) =>
          InputView(
            outputHash = outputHash,
            outputIndex = outputIndex,
            inputIndex = inputIndex,
            value = value,
            address = address,
            scriptSignature = scriptSignature,
            txinwitness = Seq(),
            sequence = sequence,
            belongs = belongs
          )
      }
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
      .query[(Int, Long, String, String, Boolean, Option[ChangeType])]
      .map {
        case (outputIndex, value, address, scriptHex, belongs, changeType) =>
          OutputView(
            outputIndex = outputIndex,
            value = value,
            address = address,
            scriptHex = scriptHex,
            belongs = belongs,
            changeType = changeType
          )
      }
      .stream

  def fetchOperations(
      accountId: UUID,
      limit: Int = 20,
      offset: Int = 0
  ): fs2.Stream[doobie.ConnectionIO, Operation] =
    sql"""SELECT account_id, hash, operation_type, value, time
          FROM operation
          WHERE account_id = $accountId
          LIMIT $limit 
          OFFSET $offset
          """
      .query[(UUID, String, OperationType, Long, String)]
      .map {
        case (accountId, hash, operationType, value, time) =>
          Operation(accountId, hash, None, operationType, value, time)
      }
      .stream

  def saveOperation(operation: Operation): doobie.ConnectionIO[Int] =
    sql"""INSERT INTO operation (
            account_id, hash, operation_type, value, time
          ) VALUES (
            ${operation.accountId},
            ${operation.hash},
            ${operation.operationType},
            ${operation.value},
            ${operation.time}
          ) ON CONFLICT ON CONSTRAINT operation_pkey DO NOTHING
        """.update.run

  def flagInputs(accountId: UUID, addresses: List[String]) = {
    val query = sql"""UPDATE input 
          SET belongs = true
          WHERE account_id = $accountId
          AND belongs = false
          AND """ ++ Fragments.in(fr"address", NonEmptyList.fromListUnsafe(addresses))
    query.update.run
  }

  def flagOutputsForAddress(accountId: UUID, address: AccountAddress) = {
    sql"""UPDATE output 
          SET belongs = true, change_type = ${address.changeType}
          WHERE account_id = $accountId
          AND belongs = false
          AND address = ${address.accountAddress}
       """.update.run
  }

}
