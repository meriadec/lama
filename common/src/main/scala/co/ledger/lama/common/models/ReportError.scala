package co.ledger.lama.common.models

import co.ledger.lama.common.Exceptions.GrpcClientException
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._
import co.ledger.lama.common.models.implicits._

case class ReportError(code: String, message: String)

object ReportError {
  def fromThrowable(t: Throwable) = t match {
    case e: GrpcClientException => ReportError(e.clientName, e.getMessage)
    case unknown                => ReportError("UnknownError", unknown.getMessage)
  }

  implicit val encoder: Encoder[ReportError] = deriveConfiguredEncoder[ReportError]
  implicit val decoder: Decoder[ReportError] = deriveConfiguredDecoder[ReportError]
}
