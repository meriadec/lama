package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.bitcoin.common.models.service._
import co.ledger.lama.bitcoin.interpreter.models.OperationToSave

object OperationComputer extends IOLogging {

  def compute(
      tx: TransactionView,
      accountId: UUID,
      addresses: List[AccountAddress]
  ): List[OperationToSave] = {

    val inputAmount = extractInputAmount(tx, addresses)

    log.logger.debug(s"Input amount: $inputAmount")

    val outputAmount = extractOutputAmount(tx, addresses)

    log.logger.debug(s"Output amount: $outputAmount")

    val changeAmount = extractChangeAmount(tx, addresses)

    log.logger.debug(s"Change amount: $changeAmount")

    val (sentAmount, receivedAmount) = {
      // in case the account is not the sender but change was received,
      // consider it a normal output.
      if (inputAmount <= 0L && changeAmount > 0L)
        (BigInt(0), outputAmount + changeAmount)
      else
        (inputAmount - changeAmount, outputAmount)
    }

    log.logger.debug(s"Sent amount: $sentAmount")
    log.logger.debug(s"Received amount: $receivedAmount")

    val sentOperation = OperationToSave(
      accountId = accountId,
      hash = tx.hash,
      operationType = Sent,
      value = sentAmount,
      time = tx.block.time,
      blockHash = tx.block.hash,
      blockHeight = tx.block.height
    )

    log.logger.debug(s"Sent operation: $sentOperation")

    val receivedOperation = OperationToSave(
      accountId = accountId,
      hash = tx.hash,
      operationType = Received,
      value = receivedAmount,
      time = tx.block.time,
      blockHash = tx.block.hash,
      blockHeight = tx.block.height
    )

    log.logger.debug(s"Received operation: $receivedOperation")

    // Both send and remove operations are created so we remove useless operation with value 0
    List(sentOperation, receivedOperation).filter(_.value > 0L)

  }

  private def extractChangeAmount(tx: TransactionView, addresses: List[AccountAddress]) = {
    tx.outputs.collect {
      case output
          if addresses
            .exists(a => a.changeType == Internal && a.accountAddress == output.address) =>
        output.value
    }.sum
  }

  private def extractOutputAmount(tx: TransactionView, addresses: List[AccountAddress]) = {
    tx.outputs.collect {
      case output
          if addresses
            .exists(a => a.changeType == External && a.accountAddress == output.address) =>
        output.value
    }.sum
  }

  private def extractInputAmount(tx: TransactionView, addresses: List[AccountAddress]) = {
    tx.inputs.collect {
      case input if addresses.exists(ad => ad.accountAddress == input.address) =>
        input.value
    }.sum
  }
}
