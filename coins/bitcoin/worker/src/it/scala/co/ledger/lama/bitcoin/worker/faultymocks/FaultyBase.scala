package co.ledger.lama.bitcoin.worker.faultymocks

import co.ledger.lama.bitcoin.worker.models.PayloadData
import co.ledger.lama.common.models.ReportableEvent

trait FaultyBase {
  val fakeCause = new Exception("Fake exception")

  def getErrorMessage(reportableEvent: Option[ReportableEvent]): Option[String] =
    reportableEvent
      .flatMap(_.payload.data.as[PayloadData].toOption)
      .flatMap(_.errorMessage)
}
