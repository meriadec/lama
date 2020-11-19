package co.ledger.lama.bitcoin.api.endpoints

import java.time.Instant
import java.util.UUID

import co.ledger.lama.bitcoin.api.models.{
  AccountInfo,
  AccountRegistered,
  GetOperationsResult,
  GetUTXOsResult
}
import co.ledger.lama.bitcoin.api.routes.AccountController.{CreationRequest, UpdateRequest}
import co.ledger.lama.bitcoin.common.models.service.BalanceHistory
import co.ledger.lama.common.models.Sort
import co.ledger.lama.manager.protobuf.SyncEvent
import endpoints4s.algebra._

trait AccountsEndpoints extends Endpoints with JsonEntitiesFromSchemas with JsonSchemas {

  val accountIdSegment: Path[UUID] = segment[UUID]("accountId", docs = Some("A user id"))

  val getUserAccount: Endpoint[UUID, AccountInfo] =
    endpoint(
      get(path / "accounts" / accountIdSegment),
      ok(jsonResponse[AccountInfo])
    )

  implicit val syncEventSchema: JsonSchema[SyncEvent] =
    field[SyncEvent]("sync_event")

  implicit val accountInfoSchema: JsonSchema[AccountInfo] = (
    field[UUID]("account_id", Some("A user id")),
    field[Long]("sync_frequency", Some("How often do we check for new operations")),
    optField[SyncEvent]("last_sync_event"),
    field[BigInt]("balance"),
    field[Int]("utxos"),
    field[BigInt]("received"),
    field[BigInt]("sent")
  )

  val createUserAccount: Endpoint[CreationRequest, AccountRegistered] =
    endpoint(
      post(path / "accounts", jsonRequest[CreationRequest]),
      ok(jsonResponse[AccountRegistered])
    )

  val updateSyncFrequency: Endpoint[(UUID, UpdateRequest), Unit] =
    endpoint(
      put(path / "accounts" / accountIdSegment, jsonRequest[UpdateRequest]),
      ok(emptyResponse)
    )

  val deleteAccount: Endpoint[UUID, Unit] =
    endpoint(
      delete(path / "accounts" / accountIdSegment),
      ok(emptyResponse)
    )

  val getUtxos: Endpoint[(UUID, Option[Int], Option[Int], Option[Sort]), GetUTXOsResult] =
    endpoint(
      get(
        path / "accounts" / accountIdSegment / "utxos" /? (qs[Option[Int]]("limit") & qs[Option[
          Int
        ]](
          "offset"
        ) & qs[Option[Sort]]("sort"))
      ),
      ok(jsonResponse[GetUTXOsResult])
    )

  val getOperations: Endpoint[
    (UUID, Option[Long], Option[Int], Option[Int], Option[Sort]),
    GetOperationsResult
  ] =
    endpoint(
      get(
        path / "accounts" / accountIdSegment / "operations" /? (qs[Option[Long]](
          "blockHeight"
        ) & qs[Option[Int]](
          "limit"
        ) & qs[Option[Int]](
          "offset"
        ) & qs[Option[Sort]]("sort"))
      ),
      ok(jsonResponse[GetOperationsResult])
    )

  val getBalances
      : Endpoint[(UUID, Option[Instant], Option[Instant], Option[Sort]), Seq[BalanceHistory]] =
    endpoint(
      get(
        path / "accounts" / accountIdSegment / "balances" /? (qs[Option[Instant]]("start") & qs[
          Option[Instant]
        ](
          "end"
        ))
      ),
      ok(jsonResponse[Seq[BalanceHistory]])
    )

}
