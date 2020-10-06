package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models._
import co.ledger.lama.common.utils.IOAssertion
import doobie.implicits._
import doobie.util.transactor.Transactor
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class QueriesIT extends AnyFlatSpecLike with Matchers with TestResources {

  val block = Block(
    "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379",
    570153,
    "2019-04-04 10:03:22"
  )

  val accountId: UUID = UUID.fromString("b723c553-3a9a-4130-8883-ee2f6c2f9201")

  val outputs = List(
    Output(0, 50000, "1DtwACvd338XtHBFYJRVKRLxviD7YtYADa", "script"),
    Output(1, 9434, "1LK8UbiRwUzC8KFEbMKvgbvriM9zLMce3C", "script")
  )
  val inputs = List(
    DefaultInput(
      "0f38e5f1b12078495a9e80c6e0d77af3d674cfe6096bb6e7909993a53b6e8386",
      0,
      0,
      80000,
      "1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ",
      "script",
      Seq(),
      4294967295L
    )
  )
  val transactionToInsert: Transaction =
    Transaction(
      "txId",
      "a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1f",
      "",
      0,
      20566,
      inputs,
      outputs,
      block,
      1
    )

  "transaction saved in db" should "be returned and populated by fetch" in IOAssertion {
    setup() *>
      appResources.use { db =>
        for {
          _           <- saveBlock(db, block)
          _           <- saveBlock(db, block)                       // check upsert (shouldn't throw error)
          _           <- saveTx(db, transactionToInsert, accountId)
          _           <- saveTx(db, transactionToInsert, accountId) // check upsert
          tx          <- fetchTx(db, accountId, transactionToInsert.hash)
          transaction <- Queries.populateTx(tx.get, accountId).transact(db)
        } yield {

          transaction.id shouldBe transactionToInsert.id
          transaction.hash shouldBe transactionToInsert.hash

          transaction.inputs should have size 1
          transaction.inputs.head match {
            case input: DefaultInput => input.value shouldBe 80000
            case _                   => fail("input should be DefaultInput")
          }

          transaction.outputs should have size 2
          transaction.outputs.filter(_.outputIndex == 0).head.value shouldBe 50000

        }
      }
  }

  val operation: Operation = Operation(
    accountId,
    transactionToInsert.hash,
    None,
    Send,
    transactionToInsert.inputs.collect {
      case i: DefaultInput => i.value
    }.sum,
    block.time
  )

  "operation saved in db" should "be fetched" in IOAssertion {
    setup() *>
      appResources.use { db =>
        for {
          _  <- saveBlock(db, block)
          _  <- saveTx(db, transactionToInsert, accountId)
          _  <- saveOp(db, operation)
          op <- fetchOp(db, accountId)
        } yield {
          op should have size 1
          op.head should be(operation)
        }
      }
  }

  private def saveBlock(db: Transactor[IO], block: Block) = {
    Queries
      .upsertBlock(block)
      .transact(db)
  }

  private def fetchTx(db: Transactor[IO], accountId: UUID, hash: String) = {
    Queries
      .fetchTx(accountId, hash)
      .transact(db)
  }

  private def saveTx(db: Transactor[IO], transaction: Transaction, accountId: UUID) = {
    Queries
      .saveTransaction(transaction, accountId)
      .transact(db)
      .void
  }

  private def fetchOp(db: Transactor[IO], accountId: UUID) = {
    Queries
      .fetchOperations(accountId)
      .transact(db)
      .compile
      .toList
  }

  private def saveOp(db: Transactor[IO], operation: Operation) = {
    Queries
      .saveOperation(operation)
      .transact(db)
      .void
  }

}
