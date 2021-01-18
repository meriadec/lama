package co.ledger.lama.bitcoin.common.models.transactor

case class FeeInfo(slow: Long, normal: Long, fast: Long) {

  def getValue(feeLevel: FeeLevel): Long = feeLevel match {
    case FeeLevel.Slow   => slow
    case FeeLevel.Normal => normal
    case FeeLevel.Fast   => fast
  }

}
