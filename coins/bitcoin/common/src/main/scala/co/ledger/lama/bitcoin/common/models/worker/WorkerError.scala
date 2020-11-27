package co.ledger.lama.bitcoin.common.models.worker

sealed trait WorkerError extends Exception {
  val service: String
  val message: String
  val rootCause: Throwable

  override def getMessage: String = s"$service - $message - ${rootCause.getMessage}"
}

case class ExplorerServiceError(rootCause: Throwable, message: String) extends WorkerError {
  val service: String = "Explorer service"
}

case class InterpreterServiceError(rootCause: Throwable, message: String) extends WorkerError {
  val service: String = "Interpreter service"
}

case class KeychainServiceError(rootCause: Throwable, message: String) extends WorkerError {
  val service: String = "Keychain service"
}
