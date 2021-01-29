package co.ledger.lama.bitcoin.interpreter.models

sealed trait TransactionType

object TransactionType {
  def fromAmounts(input: BigInt, output: BigInt, change: BigInt): TransactionType = {
    (input > 0, output > 0) match {
      case (true, false)   => SendType
      case (false, true)   => ReceiveType
      case (true, true)    => BothType
      case _ if change > 0 => ChangeOnlyType
      case _               => NoneType
    }
  }
}

case object SendType       extends TransactionType
case object ReceiveType    extends TransactionType
case object BothType       extends TransactionType
case object ChangeOnlyType extends TransactionType
case object NoneType       extends TransactionType
