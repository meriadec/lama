package co.ledger.lama.bitcoin.worker.models

import java.util.UUID

import io.circe.Json

trait WorkerErrors extends Exception {
  val errorMessage: Json
}

case class FindKeychainAddressesFailed(keychainId: UUID) extends WorkerErrors {
  val errorMessage: Json =
    Json.obj("error" -> Json.fromString(s"Unable to find addresses for keychain $keychainId"))
}

case class GetConfirmedTransactionsFailed(keychainId: UUID) extends WorkerErrors {
  val errorMessage: Json =
    Json.obj(
      "error" -> Json.fromString(s"Unable to get confirmed transactions for keychain $keychainId")
    )
}

case class DeleteAccountFailed(accountId: UUID) extends WorkerErrors {
  val errorMessage: Json =
    Json.obj(
      "error" -> Json.fromString(s"Failed to delete account $accountId")
    )
}

case class SaveTransactionsFailed(accountId: UUID) extends WorkerErrors {
  val errorMessage: Json =
    Json.obj(
      "error" -> Json.fromString(s"Failed to save transactions for account $accountId")
    )
}

case class RemoveDataFromCursorFailed(accountId: UUID) extends WorkerErrors {
  val errorMessage: Json =
    Json.obj(
      "error" -> Json.fromString(s"Failed to remove data from cursor for account $accountId")
    )
}

case class ComputeAddressesFailed(accountId: UUID) extends WorkerErrors {
  val errorMessage: Json =
    Json.obj(
      "error" -> Json.fromString(s"Failed to compute addresses for account $accountId")
    )
}

case class GetLastBlocksFailed(accountId: UUID) extends WorkerErrors {
  val errorMessage: Json =
    Json.obj(
      "error" -> Json.fromString(s"Failed to get last blocks for account $accountId")
    )
}
