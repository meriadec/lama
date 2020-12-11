package co.ledger.lama.bitcoin.common.clients.grpc.mocks

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.explorer.{Block, ConfirmedTransaction, DefaultInput}
import co.ledger.lama.bitcoin.common.models.interpreter._
import co.ledger.lama.bitcoin.common.clients.grpc.InterpreterClientService
import co.ledger.lama.common.models.{Coin, Sort}

import scala.collection.mutable

class InterpreterClientServiceMock extends InterpreterClientService {

  var savedTransactions: mutable.Map[UUID, List[ConfirmedTransaction]] = mutable.Map.empty
  var transactions: mutable.Map[UUID, List[TransactionView]]           = mutable.Map.empty
  var operations: mutable.Map[UUID, List[Operation]]                   = mutable.Map.empty

  def saveTransactions(
      accountId: UUID,
      txs: List[ConfirmedTransaction]
  ): IO[Int] = {
    savedTransactions.update(
      accountId,
      txs ::: savedTransactions.getOrElse(accountId, Nil)
    )

    IO(txs.size)
  }

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int] = {
    savedTransactions.update(
      accountId,
      savedTransactions(accountId)
        .filter(tx => tx.block.height < blockHeightCursor.getOrElse(0L))
    )

    transactions.update(
      accountId,
      transactions(accountId)
        .filter(tx => tx.block.height < blockHeightCursor.getOrElse(0L))
    )

    operations.update(
      accountId,
      operations(accountId)
        .filter(op => op.transaction.get.block.height < blockHeightCursor.getOrElse(0L))
    )

    IO.pure(0)
  }

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult] = {
    val lastBlocks: List[Block] = savedTransactions(accountId)
      .map(_.block)
      .distinct
      .sortBy(_.height)
      .reverse

    IO(GetLastBlocksResult(lastBlocks))
  }

  def compute(accountId: UUID, coin: Coin, addresses: List[AccountAddress]): IO[Int] = {

    val txViews = savedTransactions(accountId).map(tx =>
      TransactionView(
        tx.id,
        tx.hash,
        tx.receivedAt,
        tx.lockTime,
        tx.fees,
        tx.inputs.collect { case i: DefaultInput =>
          InputView(
            i.outputHash,
            i.outputIndex,
            i.inputIndex,
            i.value,
            i.address,
            i.scriptSignature,
            i.txinwitness,
            i.sequence,
            addresses.find(_.accountAddress == i.address).map(_.derivation)
          )

        },
        tx.outputs.map(o =>
          OutputView(
            o.outputIndex,
            o.value,
            o.address,
            o.scriptHex,
            addresses.find(a => a.accountAddress == o.address).map(a => a.changeType),
            addresses.find(_.accountAddress == o.address).map(_.derivation)
          )
        ),
        BlockView(tx.block.hash, tx.block.height, tx.block.time),
        tx.confirmations
      )
    )

    transactions.update(accountId, txViews)

    val operationToSave = transactions(accountId).flatMap { tx =>
      val inputAmount = tx.inputs.filter(_.belongs).map(_.value).sum
      val outputAmount = tx.outputs
        .filter(o => o.belongs && o.changeType.contains(ChangeType.External))
        .map(_.value)
        .sum
      val changeAmount = tx.outputs
        .filter(o => o.belongs && o.changeType.contains(ChangeType.Internal))
        .map(_.value)
        .sum

      (inputAmount > 0, outputAmount > 0) match {
        // only input, consider changeAmount as deducted from spent
        case (true, false) =>
          List(makeOperation(accountId, tx, inputAmount - changeAmount, OperationType.Sent))
        // only output, consider changeAmount as received
        case (false, true) =>
          List(makeOperation(accountId, tx, outputAmount + changeAmount, OperationType.Received))
        // both input and output, consider change as deducted from spend
        case (true, true) =>
          List(
            makeOperation(accountId, tx, inputAmount - changeAmount, OperationType.Sent),
            makeOperation(accountId, tx, outputAmount, OperationType.Received)
          )
        case _ => Nil
      }

    }

    operations.update(
      accountId,
      operationToSave
    )

    IO(operationToSave.size)
  }

  private def makeOperation(
      accountId: UUID,
      tx: TransactionView,
      amount: BigInt,
      operationType: OperationType
  ) = {
    Operation(
      accountId,
      tx.hash,
      Some(tx),
      operationType,
      amount,
      tx.fees,
      tx.block.time
    )
  }

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetOperationsResult] = {

    val ops: List[Operation] = operations(accountId)
      .filter(_.transaction.get.block.height > blockHeight)
      .sortBy(_.transaction.get.block.height)
      .slice(offset, offset + limit)

    val total = operations(accountId).count(_.transaction.get.block.height > blockHeight)

    IO(
      new GetOperationsResult(
        ops.size < operations(accountId).size,
        ops,
        ops.size,
        total
      )
    )
  }

  def getUTXOs(
      accountId: UUID,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetUtxosResult] = {

    val inputs = transactions(accountId)
      .flatMap(_.inputs)
      .filter(_.belongs)

    val utxos = transactions(accountId)
      .flatMap(tx => tx.outputs.map(o => (tx, o)))
      .filter(o =>
        o._2.belongs && !inputs.exists(i =>
          i.outputHash == o._1.hash && i.address == o._2.address && i.outputIndex == o._2.outputIndex
        )
      )
      .map { case (tx, output) =>
        Utxo(
          tx.hash,
          output.outputIndex,
          output.value,
          output.address,
          output.scriptHex,
          output.changeType,
          output.derivation.get,
          tx.block.time
        )
      }

    val total = transactions(accountId).size

    IO(
      new GetUtxosResult(
        false,
        utxos,
        utxos.size,
        total
      )
    )

  }

  def getBalance(accountId: UUID): IO[BalanceHistory] =
    IO.raiseError(new Exception("Not implements Yet"))

  def getBalanceHistory(
      accountId: UUID,
      start: Option[Instant],
      end: Option[Instant]
  ): IO[GetBalanceHistoryResult] =
    IO.raiseError(new Exception("Not implements Yet"))

  def getBalanceHistories(
      accountId: UUID
  ): IO[GetBalanceHistoryResult] =
    IO.raiseError(new Exception("Not implements Yet"))
}
