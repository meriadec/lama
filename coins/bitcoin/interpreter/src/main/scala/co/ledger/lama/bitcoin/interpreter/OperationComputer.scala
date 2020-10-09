package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.Service._

object OperationComputer {

  def compute(
      tx: TransactionView,
      accountId: UUID,
      addresses: List[AccountAddress]
  ): List[Operation] = {

    val inputAmount  = extractInputAmount(tx, addresses)
    val outputAmount = extractOutputAmount(tx, addresses)
    val changeAmount = extractChangeAmount(tx, addresses)

    val (sentAmount, receivedAmount) = {
      // in case the account is not the sender but change was received,
      // consider it a normal output.
      if (inputAmount <= 0L && changeAmount > 0L)
        (BigInt(0), outputAmount + changeAmount)
      else
        (inputAmount - changeAmount, outputAmount)
    }

    val sentOperation = Operation(
      accountId = accountId,
      hash = tx.hash,
      Some(tx),
      operationType = Sent,
      value = sentAmount,
      time = tx.block.time
    )

    val receivedOperation = Operation(
      accountId = accountId,
      hash = tx.hash,
      Some(tx),
      operationType = Received,
      value = receivedAmount,
      time = tx.block.time
    )

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
