package co.ledger.lama.bitcoin.interpreter

import java.time.Instant
import java.util.UUID

import cats.implicits._
import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.common.models.service._
import co.ledger.lama.bitcoin.interpreter.services.{
  BalanceService,
  FlaggingService,
  OperationService,
  TransactionService
}
import co.ledger.lama.common.models.Sort
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class InterpreterIT extends AnyFlatSpecLike with Matchers with TestResources {

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
  val insertTx: ConfirmedTransaction =
    ConfirmedTransaction(
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
    insertTx.inputs.collect { case i: DefaultInput =>
      i.value
    }.sum,
    block.time
  )

  "data in db" should "be deleted from cursor" in IOAssertion {
    setup() *>
      appResources.use { db =>
        val operationService   = new OperationService(db, conf.maxConcurrent)
        val transactionService = new TransactionService(db, conf.maxConcurrent)
        val flaggingService    = new FlaggingService(db)
        val balanceService     = new BalanceService(db)

        val block2 = Block(
          "0000000000000000000cc9cc204cf3b314d106e69afbea68f2ae7f9e5047ba74",
          block.height + 1,
          "time"
        )
        val block3 = Block(
          "0000000000000000000bf68b57eacbff287ceafecb54a30dc3fd19630c9a3883",
          block.height + 2,
          "time"
        )

        // intentionally disordered
        val blocksToSave = List(block2, block, block3)

        for {
          _ <- QueryUtils.saveTx(db, insertTx, accountId)
          _ <- QueryUtils.saveTx(db, insertTx.copy(hash = "toto", block = block2), accountId)
          _ <- QueryUtils.saveTx(db, insertTx.copy(hash = "tata", block = block3), accountId)

          blocks <- transactionService.getLastBlocks(accountId).compile.toList

          _ <- flaggingService.flagInputsAndOutputs(accountId, List(inputAddress, outputAddress2))
          _ <- operationService.compute(accountId)

          // Compute twice to have 2 balances history
          _ <- balanceService.compute(accountId)
          _ <- balanceService.compute(accountId)

          resOpsBeforeDeletion <- operationService.getOperations(
            accountId,
            blockHeight = 0L,
            limit = 20,
            offset = 0,
            Sort.Ascending
          )

          (opsBeforeDeletion, opsBeforeDeletionTrunc) = resOpsBeforeDeletion

          now   = Instant.now()
          start = now.minusSeconds(86400)
          end   = now.plusSeconds(86400)
          balancesBeforeDeletion <- balanceService.getBalancesHistory(accountId, start, end)

          _ <- transactionService.removeFromCursor(accountId, block.height)
          _ <- balanceService.removeBalancesHistoryFromCursor(accountId, block.height)

          resOpsAfterDeletion <- operationService.getOperations(
            accountId,
            blockHeight = 0L,
            limit = 20,
            offset = 0,
            Sort.Ascending
          )

          (opsAfterDeletion, opsAfterDeletionTrunc) = resOpsAfterDeletion

          balancesAfterDeletion <- balanceService.getBalancesHistory(accountId, start, end)

          blocksAfterDelete <- transactionService.getLastBlocks(accountId).compile.toList
        } yield {
          blocks should be(blocksToSave.sortBy(_.height)(Ordering[Long].reverse))

          opsBeforeDeletion should have size 3
          opsBeforeDeletionTrunc shouldBe false

          balancesBeforeDeletion should have size 2

          opsAfterDeletion shouldBe empty
          opsAfterDeletionTrunc shouldBe false

          balancesAfterDeletion shouldBe empty

          blocksAfterDelete shouldBe empty
        }
      }
  }
}
