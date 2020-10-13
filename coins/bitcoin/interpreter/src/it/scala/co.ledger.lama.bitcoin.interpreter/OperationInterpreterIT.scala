package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.common.models.service._
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class OperationInterpreterIT extends AnyFlatSpecLike with Matchers with TestResources {

  val accountId: UUID = UUID.fromString("b723c553-3a9a-4130-8883-ee2f6c2f9202")

  private val outputAddress1 = AccountAddress("1DtwACvd338XtHBFYJRVKRLxviD7YtYADa", External)
  private val outputAddress2 = AccountAddress("1LK8UbiRwUzC8KFEbMKvgbvriM9zLMce3C", Internal)
  private val inputAddress   = AccountAddress("1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ", External)

  val block = Block(
    "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379",
    570153,
    "2019-04-04 10:03:22"
  )

  val outputs = List(
    Output(0, 50000, outputAddress1.accountAddress, "script"),
    Output(1, 9434, outputAddress2.accountAddress, "script")
  )
  val inputs = List(
    DefaultInput(
      "0f38e5f1b12078495a9e80c6e0d77af3d674cfe6096bb6e7909993a53b6e8386",
      0,
      0,
      80000,
      inputAddress.accountAddress,
      "script",
      Seq(),
      4294967295L
    )
  )
  val insertTx: Transaction =
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

  val operation: Operation = Operation(
    accountId,
    insertTx.hash,
    None,
    Sent,
    insertTx.inputs.collect {
      case i: DefaultInput => i.value
    }.sum,
    block.time
  )

  "operation saved in db" should "be fetched" in IOAssertion {
    setup() *>
      appResources.use { db =>
        val operationInterpreter = new OperationInterpreter(db)

        for {
          _   <- QueryUtils.saveBlock(db, block)
          _   <- QueryUtils.saveTx(db, insertTx, accountId)
          _   <- operationInterpreter.computeOperations(accountId, List(inputAddress, outputAddress2))
          res <- operationInterpreter.getOperations(accountId, 20, 0)
          (ops, trunc) = res
        } yield {
          ops should have size 1
          trunc shouldBe false

          val op = ops.head
          val tx = op.transaction.get

          op.accountId shouldBe accountId
          op.hash shouldBe insertTx.hash
          op.operationType shouldBe Sent

          tx.fees shouldBe insertTx.fees

          tx.inputs.find(_.belongs).get.address shouldBe inputAddress.accountAddress
          tx.outputs.find(_.belongs).get.address shouldBe outputAddress2.accountAddress
          tx.outputs.find(_.belongs).get.changeType shouldBe Some(Internal)
        }
      }
  }

  it should "have made utxos" in IOAssertion {
    setup() *>
      appResources.use { db =>
        val operationInterpreter = new OperationInterpreter(db)

        for {
          _   <- QueryUtils.saveBlock(db, block)
          _   <- QueryUtils.saveTx(db, insertTx, accountId)
          _   <- operationInterpreter.computeOperations(accountId, List(inputAddress, outputAddress1))
          res <- operationInterpreter.getUTXOs(accountId, 20, 0)
          (utxos, trunc) = res
        } yield {
          utxos should have size 1
          trunc shouldBe false

          val utxo = utxos.head

          utxo.address shouldBe outputAddress1.accountAddress
          utxo.changeType shouldBe Some(outputAddress1.changeType)
        }
      }
  }

}
