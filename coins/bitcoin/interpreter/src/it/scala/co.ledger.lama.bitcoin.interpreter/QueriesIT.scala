package co.ledger.lama.bitcoin.interpreter

import java.sql.Timestamp

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models._
import co.ledger.lama.common.utils.IOAssertion
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class QueriesIT extends AnyFlatSpecLike with Matchers with TestResources {

  "transaction saved in db" should "be returned by fetch" in IOAssertion {
    setup() *>
      appResources.use { db =>
        val block = Block(
          "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379",
          570153,
          Timestamp.valueOf("2019-04-04 10:03:22")
        )

        val accountId = "b723c553-3a9a-4130-8883-ee2f6c2f9201"

        val outputs = List(
          Output(0, "1DtwACvd338XtHBFYJRVKRLxviD7YtYADa", 50000),
          Output(1, "1LK8UbiRwUzC8KFEbMKvgbvriM9zLMce3C", 9434)
        )
        val inputs = List(
          Input(
            "0f38e5f1b12078495a9e80c6e0d77af3d674cfe6096bb6e7909993a53b6e8386",
            0,
            "1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ",
            80000,
            BigInt("4294967295")
          )
        )
        val transactionToInsert =
          Transaction(
            accountId,
            "a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1f",
            block.hash,
            20566,
            None,
            inputs,
            outputs,
            List()
          )

        saveBlock(db, block) *>
          saveTx(db, transactionToInsert) *>
          fetchTx(db, accountId, transactionToInsert.hash)
            .map { transaction =>
              transaction.map(_.accountId) should contain(transactionToInsert.accountId)
            }

      }
  }

  private def saveBlock(db: Transactor[IO], block: Block) = {
    Queries
      .upsertBlock(block)
      .transact(db)
  }

  private def fetchTx(db: Transactor[IO], accountId: String, hash: String) = {
    Queries
      .fetchTransactions(accountId, hash)
      .transact(db)
  }

  private def saveTx(db: Transactor[IO], transaction: Transaction) = {
    Queries
      .saveTransaction(transaction)
      .transact(db)
      .void
  }

}
