package co.ledger.lama.bitcoin.interpreter

import java.time.Instant
import java.util.UUID

import cats.data.NonEmptyList
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.worker._
import co.ledger.lama.bitcoin.common.models.interpreter._
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

  private val outputAddress1 =
    AccountAddress("1DtwACvd338XtHBFYJRVKRLxviD7YtYADa", ChangeType.External, NonEmptyList.of(1, 0))
  private val outputAddress2 =
    AccountAddress("1LK8UbiRwUzC8KFEbMKvgbvriM9zLMce3C", ChangeType.Internal, NonEmptyList.of(0, 0))
  private val inputAddress =
    AccountAddress("1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ", ChangeType.External, NonEmptyList.of(1, 1))

  val block: Block = Block(
    "00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379",
    570153,
    Instant.parse("2019-04-04T10:03:22Z")
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
      List(),
      4294967295L
    )
  )
  val insertTx: ConfirmedTransaction =
    ConfirmedTransaction(
      "txId",
      "a8a935c6bc2bd8b3a7c20f107a9eb5f10a315ce27de9d72f3f4e27ac9ec1eb1f",
      Instant.parse("2019-04-04T10:03:22Z"),
      0,
      20566,
      inputs,
      outputs,
      block,
      1
    )

  "a transaction" should "have a full lifecycle" in IOAssertion {
    setup() *>
      appResources.use { db =>
        val operationService   = new OperationService(db, conf.maxConcurrent)
        val transactionService = new TransactionService(db, conf.maxConcurrent)
        val flaggingService    = new FlaggingService(db)
        val balanceService     = new BalanceService(db)

        val block2 = Block(
          "0000000000000000000cc9cc204cf3b314d106e69afbea68f2ae7f9e5047ba74",
          block.height + 1,
          Instant.parse("2019-04-04T10:03:22Z")
        )
        val block3 = Block(
          "0000000000000000000bf68b57eacbff287ceafecb54a30dc3fd19630c9a3883",
          block.height + 2,
          Instant.parse("2019-04-04T10:03:22Z")
        )

        // intentionally disordered
        val blocksToSave = List(block2, block, block3)

        for {
          _ <- QueryUtils.saveTx(db, insertTx, accountId)
          _ <- QueryUtils.saveTx(db, insertTx.copy(hash = "toto", block = block2), accountId)
          _ <- QueryUtils.saveTx(db, insertTx.copy(hash = "tata", block = block3), accountId)

          blocks <- transactionService.getLastBlocks(accountId).compile.toList

          _ <- flaggingService.flagInputsAndOutputs(accountId, List(inputAddress, outputAddress2))
          _ <- operationService
            .compute(accountId)
            .through(operationService.saveOperationSink)
            .compile
            .toList

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

          (opsBeforeDeletion, opsBeforeDeletionTotal, opsBeforeDeletionTrunc) = resOpsBeforeDeletion

          resUtxoBeforeDeletion <- operationService.getUTXOs(
            accountId,
            Sort.Ascending,
            limit = 20,
            offset = 0
          )

          (utxosBeforeDeletion, utxosBeforeDeletionTotal, utxosBeforeDeletionTrunc) =
            resUtxoBeforeDeletion

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

          (opsAfterDeletion, opsAfterDeletionTotal, opsAfterDeletionTrunc) = resOpsAfterDeletion

          balancesAfterDeletion <- balanceService.getBalancesHistory(accountId, start, end)

          blocksAfterDelete <- transactionService.getLastBlocks(accountId).compile.toList
        } yield {
          blocks should be(blocksToSave.sortBy(_.height)(Ordering[Long].reverse))

          opsBeforeDeletion should have size 3
          opsBeforeDeletionTotal shouldBe 3
          opsBeforeDeletionTrunc shouldBe false

          utxosBeforeDeletion should have size 3
          utxosBeforeDeletionTotal shouldBe 3
          utxosBeforeDeletionTrunc shouldBe false

          balancesBeforeDeletion should have size 2

          opsAfterDeletion shouldBe empty
          opsAfterDeletionTotal shouldBe 0
          opsAfterDeletionTrunc shouldBe false

          balancesAfterDeletion shouldBe empty

          blocksAfterDelete shouldBe empty
        }
      }
  }
}
