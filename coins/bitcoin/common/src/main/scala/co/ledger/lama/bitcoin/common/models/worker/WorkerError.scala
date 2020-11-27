package co.ledger.lama.bitcoin.common.models.worker

trait WorkerError extends Exception {
  val errorMessage: String
  val cause: Throwable

  override def getMessage: String =
    s"""
       |$errorMessage
       |
       |${cause.getMessage}
       |""".stripMargin
}

case class ExplorerServiceError(cause: Throwable, errorMessage: String)    extends WorkerError
case class InterpreterServiceError(cause: Throwable, errorMessage: String) extends WorkerError
case class KeychainServiceError(cause: Throwable, errorMessage: String)    extends WorkerError
