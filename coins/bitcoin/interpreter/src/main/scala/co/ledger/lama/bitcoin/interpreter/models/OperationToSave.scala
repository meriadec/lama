package co.ledger.lama.bitcoin.interpreter.models

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.service.{OperationType, Received, Sent}
import fs2.Chunk

case class OperationToSave(
    accountId: UUID,
    hash: String,
    operationType: OperationType,
    value: BigInt,
    time: String,
    blockHash: String,
    blockHeight: Long
)

case class TransactionAmounts(
    accountId: UUID,
    hash: String,
    blockHash: String,
    blockHeight: Long,
    blockTime: String,
    inputAmount: BigInt,
    outputAmount: BigInt,
    changeAmount: BigInt
) {

  def computeOperations(): Chunk[OperationToSave] = {
    (inputAmount > 0, outputAmount > 0) match {
      // only input, consider changeAmount as deducted from spent
      case (true, false) => Chunk(makeOperation(inputAmount - changeAmount, Sent))
      // only output, consider changeAmount as received
      case (false, true) => Chunk(makeOperation(outputAmount + changeAmount, Received))
      // both input and output, consider change as deducted from spend
      case (true, true) =>
        Chunk(
          makeOperation(inputAmount - changeAmount, Sent),
          makeOperation(outputAmount, Received)
        )
      case _ => Chunk.empty
    }
  }

  private def makeOperation(amount: BigInt, operationType: OperationType) = {
    OperationToSave(
      accountId = accountId,
      hash = hash,
      operationType = operationType,
      value = amount,
      time = blockTime,
      blockHash = blockHash,
      blockHeight = blockHeight
    )
  }
}
