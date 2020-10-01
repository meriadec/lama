package co.ledger.lama.bitcoin.interpreter

import co.ledger.lama.bitcoin.interpreter.models._
import doobie.free.connection.ConnectionIO
import cats.implicits._
import doobie.implicits._
import doobie._
import doobie.implicits.javasql._

object Queries {

  implicit val bigIntType: Meta[BigInt] = Meta.Advanced.other[BigInt]("bigint")

  def upsertBlock(block: Block): ConnectionIO[Int] =
    sql"""INSERT INTO block (
            hash, height, time, deleted
          ) VALUES (
            ${block.hash}::bytea, ${block.height}, ${block.time}, ${block.deleted}
          )
          ON CONFLICT ON CONSTRAINT block_pkey DO NOTHING;
        """.update.run

  def fetchTransactions(
      accountId: String,
      hash: String
  ): ConnectionIO[Option[Transaction]] =
    sql"""SELECT account_id, hash, block_hash, fee
          FROM transaction
          WHERE hash = $hash::bytea
          AND account_id = $accountId
          """
      .query[(String, String, String, Int)]
      .map {
        case (account_id, hash, block_hash, fee) => Transaction(account_id, hash, block_hash, fee)
      }
      .option

  def saveTransaction(tx: Transaction): ConnectionIO[Int] =
    for {

      txStatement <- sql"""INSERT INTO transaction (
            account_id, hash, block_hash, fee
          ) VALUES (
            ${tx.accountId}, ${tx.hash}::bytea, ${tx.blockHash}::bytea, ${tx.fee}
          );
        """.update.run

      _ <- tx.inputs.traverse(prepareInputInsert(tx.accountId, tx.hash, _).update.run)
      _ <- tx.outputs.traverse(prepareOutputInsert(tx.accountId, tx.hash, _).update.run)
      _ <- tx.coinbase.traverse(prepareCoinbaseInsert(tx.accountId, tx.hash, _).update.run)

    } yield {
      txStatement
    }

  private def prepareInputInsert(
      accountId: String,
      txHash: String,
      input: Input
  ) = sql"""INSERT INTO input (
            account_id, tx_hash, output_tx_hash, output_index, address, amount, sequence
          ) VALUES (
            $accountId,
            $txHash::bytea,
            ${input.outputTxHash}::bytea,
            ${input.outputIndex},
            ${input.address},
            ${input.amount},
            ${input.sequence}
          )
        """

  private def prepareOutputInsert(
      accountId: String,
      txHash: String,
      output: Output
  ) = sql"""INSERT INTO output (
            account_id, tx_hash, index, address, amount
          ) VALUES (
            $accountId,
            $txHash::bytea,
            ${output.index},
            ${output.address},
            ${output.amount}
          )
        """

  private def prepareCoinbaseInsert(
      accountId: String,
      txHash: String,
      coinbase: CoinBase
  ) = sql"""INSERT INTO coinbase_input (
            account_id, tx_hash, address, reward, fee
          ) VALUES (
            $accountId,
            $txHash::bytea,
            ${coinbase.address},
            ${coinbase.reward},
            ${coinbase.fee}
          )
        """

}
