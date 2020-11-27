package co.ledger.lama.bitcoin.common.models.worker

import io.circe.Json

trait WorkerError extends Exception {
  val errorMessage: String
  val thr: Throwable
  val asJson: Json = Json.obj(
    "error"     -> Json.fromString(errorMessage),
    "exception" -> Json.fromString(thr.getMessage)
  )
}

case class ExplorerServiceError(thr: Throwable, errorMessage: String)    extends WorkerError
case class InterpreterServiceError(thr: Throwable, errorMessage: String) extends WorkerError
case class KeychainServiceError(thr: Throwable, errorMessage: String)    extends WorkerError
