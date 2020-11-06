package co.ledger.lama.bitcoin.interpreter

import java.time.Instant
import java.util.UUID

import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.common.models.service._
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import co.ledger.lama.bitcoin.interpreter.services.{
  BalanceService,
  FlaggingService,
  OperationService
}

class BalanceIT extends AnyFlatSpecLike with Matchers with TestResources {

  private val time: Instant = Instant.parse("2019-04-04T10:03:22Z")

  val block1: Block = Block(
    "block1",
    500153,
    time
  )

  val block2: Block = Block(
    "block2",
    570153,
    time
  )

  val accountId: UUID = UUID.fromString("b723c553-3a9a-4130-8883-ee2f6c2f9201")

  val address1: AccountAddress = AccountAddress("address1", External)
  val address2: AccountAddress = AccountAddress("address2", External)
  val address3: AccountAddress = AccountAddress("address3", Internal)

  val notBelongingAddress: AccountAddress = AccountAddress("fakeLama", External)

  val tx1: ConfirmedTransaction =
    ConfirmedTransaction(
      "txId1",
      "txId1",
      time,
      0,
      0,
      List(DefaultInput("txId0", 0, 0, 60000, "anotherAccount", "script", List(), 1L)),
      List(Output(0, 60000, address1.accountAddress, "script")),
      block1,
      1
    )

  val outputs = List(
    Output(0, 30000, address2.accountAddress, "script"),
    Output(1, 20000, notBelongingAddress.accountAddress, "script"),
    Output(2, 9434, address3.accountAddress, "script")
  )

  val inputs = List(
    DefaultInput(
      "txId1",
      0,
      0,
      60000,
      address1.accountAddress,
      "script",
      List(),
      4294967295L
    )
  )

  val tx2: ConfirmedTransaction =
    ConfirmedTransaction(
      "txId2",
      "txId2",
      time,
      0,
      566,
      inputs,
      outputs,
      block2,
      1
    )

  it should "have the correct balance" in IOAssertion {
    setup() *>
      appResources.use { db =>
        val operationService = new OperationService(db, conf.maxConcurrent)
        val balanceService   = new BalanceService(db)
        val flaggingService  = new FlaggingService(db)

        val now   = Instant.now()
        val start = now.minusSeconds(86400)
        val end   = now.plusSeconds(86400)

        for {
          _ <- QueryUtils.saveTx(db, tx1, accountId)
          _ <- QueryUtils.saveTx(db, tx2, accountId)
          _ <- flaggingService.flagInputsAndOutputs(
            accountId,
            List(address2, address3, address1)
          )
          _            <- operationService.compute(accountId)
          savedBalance <- balanceService.compute(accountId)
          current      <- balanceService.getBalance(accountId)
          balances     <- balanceService.getBalancesHistory(accountId, start, end)
        } yield {
          savedBalance.balance shouldBe BigInt(39434)
          savedBalance.utxos shouldBe 2
          savedBalance.received shouldBe BigInt(90000)
          savedBalance.sent shouldBe BigInt(50566)

          current.balance shouldBe BigInt(39434)
          current.utxos shouldBe 2
          current.received shouldBe BigInt(90000)
          current.sent shouldBe BigInt(50566)

          balances should have size 1
          balances.head.balance shouldBe current.balance
          balances.head.utxos shouldBe current.utxos
          balances.head.received shouldBe current.received
          balances.head.sent shouldBe current.sent
        }
      }
  }

}
