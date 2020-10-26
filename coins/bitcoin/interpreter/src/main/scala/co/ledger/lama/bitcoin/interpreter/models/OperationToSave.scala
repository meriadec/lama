package co.ledger.lama.bitcoin.interpreter.models

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.service.{OperationType, Received, Sent}

case class OperationToSave(
    accountId: UUID,
    hash: String,
    operationType: OperationType,
    value: BigInt,
    time: String,
    blockHash: String,
    blockHeight: Long
)

case class OperationFull(
    accountId: UUID,
    hash: String,
    blockHash: String,
    blockHeight: Long,
    blockTime: String,
    inputAmount: BigInt,
    outputAmount: BigInt,
    changeAmount: BigInt
) {

  def computeOperations(): List[OperationToSave] = {
    (inputAmount > 0, outputAmount > 0) match {
      // only input, consider changeAmount as deducted from spent
      case (true, false) => List(makeOperation(inputAmount - changeAmount, Sent))
      // only output, consider changeAmount as received
      case (false, true) => List(makeOperation(outputAmount + changeAmount, Received))
      // both input and output, consider change as deducted from spend
      case (true, true) =>
        List(makeOperation(inputAmount - changeAmount, Sent), makeOperation(outputAmount, Received))
      case _ => Nil
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
