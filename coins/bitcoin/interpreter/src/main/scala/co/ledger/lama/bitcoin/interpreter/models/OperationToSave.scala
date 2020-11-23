package co.ledger.lama.bitcoin.interpreter.models

import java.time.Instant
import java.util.UUID

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.bitcoin.common.models.interpreter.{OperationType}
import fs2.Chunk
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._

case class OperationToSave(
    accountId: UUID,
    hash: String,
    operationType: OperationType,
    value: BigInt,
    fees: BigInt,
    time: Instant,
    blockHash: String,
    blockHeight: Long
)

object OperationToSave {
  implicit val encoder: Encoder[OperationToSave] =
    deriveConfiguredEncoder[OperationToSave]
  implicit val decoder: Decoder[OperationToSave] =
    deriveConfiguredDecoder[OperationToSave]
}

case class TransactionAmounts(
    accountId: UUID,
    hash: String,
    blockHash: String,
    blockHeight: Long,
    blockTime: Instant,
    fees: BigInt,
    inputAmount: BigInt,
    outputAmount: BigInt,
    changeAmount: BigInt
) {

  def computeOperations(): Chunk[OperationToSave] = {
    (inputAmount > 0, outputAmount > 0) match {
      // only input, consider changeAmount as deducted from spent
      case (true, false) => Chunk(makeOperation(inputAmount - changeAmount, OperationType.Sent))
      // only output, consider changeAmount as received
      case (false, true) =>
        Chunk(makeOperation(outputAmount + changeAmount, OperationType.Received))
      // both input and output, consider change as deducted from spend
      case (true, true) =>
        Chunk(
          makeOperation(inputAmount - changeAmount, OperationType.Sent),
          makeOperation(outputAmount, OperationType.Received)
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
      blockHeight = blockHeight,
      fees = fees
    )
  }
}
