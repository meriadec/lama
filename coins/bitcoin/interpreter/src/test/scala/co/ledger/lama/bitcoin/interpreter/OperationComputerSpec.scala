package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import co.ledger.lama.bitcoin.common.models._
import co.ledger.lama.bitcoin.interpreter.protobuf.AccountAddress
import co.ledger.lama.bitcoin.interpreter.protobuf.ChangeType._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class OperationComputerSpec extends AnyFlatSpecLike with Matchers {

  private val blocHash  = "blocHash"
  private val blockTime = "now"
  private val accountId = UUID.randomUUID()
  private val txHash    = "txHash"
  private val txId      = "txId"

  private val inputAddress1       = AccountAddress("inputAddress1", EXTERNAL)
  private val inputAddress2       = AccountAddress("inputAddress2", EXTERNAL)
  private val outputChangeAddress = AccountAddress("outputChangeAddress", INTERNAL)
  private val outputAddress1      = AccountAddress("outputAddress1", EXTERNAL)
  private val outputAddress2      = AccountAddress("outputAddress2", EXTERNAL)

  private val block = Block(blocHash, 1, blockTime)

  private val inputs = Seq(
    DefaultInput("outputHash1", 0, 0, 9, inputAddress1.accountAddress, "script", Seq(), 0),
    DefaultInput("outputHash2", 0, 1, 11, inputAddress2.accountAddress, "script", Seq(), 0)
  )

  private val outputs = Seq(
    Output(0, 4, outputChangeAddress.accountAddress, "script"),
    Output(1, 10, outputAddress1.accountAddress, "script"),
    Output(2, 5, outputAddress2.accountAddress, "script")
  )

  private val tx =
    Transaction(txId, txHash, "receivedAt", 0, 1, inputs, outputs, block, 1)

  "a transaction with Input addresses " should "only have a 'send' operation" in {
    val operations = OperationComputer.compute(tx, accountId, List(inputAddress1, inputAddress2))
    operations should have size 1
    operations.head.value shouldBe 20
    operations.head.operationType shouldBe Send
    operations.head.accountId shouldBe accountId
  }

  "a transaction with Input and Change addresses" should "only have a 'send' operation with the correct amount" in {
    val operations = OperationComputer.compute(
      tx,
      accountId,
      List(inputAddress1, inputAddress2, outputChangeAddress)
    )
    operations should have size 1
    operations.head.value shouldBe 16
    operations.head.operationType shouldBe Send
    operations.head.accountId shouldBe accountId
  }

  "a transaction with Output addresses" should "only have a 'received' operation" in {
    val operations = OperationComputer.compute(tx, accountId, List(outputAddress1))
    operations should have size 1
    operations.head.value shouldBe 10
    operations.head.operationType shouldBe Received
    operations.head.accountId shouldBe accountId
  }

  "a transaction with Output and Input addresses" should "have both operations operation" in {
    val operations = OperationComputer.compute(
      tx,
      accountId,
      List(inputAddress1, inputAddress2, outputAddress1, outputAddress2)
    )

    operations should have size 2

    val sendOp = operations.filter(_.operationType == Send)
    sendOp.head.value shouldBe 20
    sendOp.head.operationType shouldBe Send
    sendOp.head.accountId shouldBe accountId

    val receivedOp = operations.filter(_.operationType == Received)
    receivedOp.head.value shouldBe 15
    receivedOp.head.operationType shouldBe Received
    receivedOp.head.accountId shouldBe accountId
  }

  "a transaction with only Change address" should "have a 'received' operation'" in {
    val operations = OperationComputer.compute(tx, accountId, List(outputChangeAddress))
    operations should have size 1
    operations.head.value shouldBe 4
    operations.head.operationType shouldBe Received
    operations.head.accountId shouldBe accountId
  }

}
