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
import co.ledger.lama.common.models.implicits._
import io.circe.Printer
import sttp.tapir._
import sttp.tapir.json.circe._

object DropNullTapirJsonCirce extends TapirJsonCirce {
  override def jsonPrinter: Printer = Printer.spaces2.copy(dropNullValues = true)
}

object AccountsEndpoints {

  import DropNullTapirJsonCirce._

  implicit val sSort: Schema[Option[Sort]]            = Schema.derive
  implicit val sortValidator: Validator[Option[Sort]] = Validator.derive
  implicit val sortCodec: Codec[String, Option[Sort], CodecFormat.TextPlain] =
    Codec.string.map[Option[Sort]](s => Sort.fromKey(s)) {
      case Some(value) => value.name
      case None        => Sort.Ascending.name
    }

  val accountIdPath: EndpointInput.PathCapture[UUID] = path[UUID]("accountId")

  val blockHeightQs: EndpointInput.Query[Option[Long]] = query[Option[Long]]("blockHeight")
  val limitQs: EndpointInput.Query[Option[Int]]        = query[Option[Int]]("limit")
  val offsetQs: EndpointInput.Query[Option[Int]]       = query[Option[Int]]("offset")
  val sortQs: EndpointInput.Query[Option[Sort]]        = query[Option[Sort]]("sort")

  val startQs: EndpointInput.Query[Option[Instant]] = query[Option[Instant]]("start")
  val endQs: EndpointInput.Query[Option[Instant]]   = query[Option[Instant]]("end")

  val getAccount: Endpoint[UUID, Unit, AccountInfo, Any] =
    endpoint.get.in(accountIdPath).out(jsonBody[AccountInfo])

  val createAccount: Endpoint[CreationRequest, Unit, AccountRegistered, Any] =
    endpoint.post.in(jsonBody[CreationRequest]).out(jsonBody[AccountRegistered])

  val updateSyncFrequency: Endpoint[(UUID, UpdateRequest), Unit, Long, Any] =
    endpoint.put.in(accountIdPath).in(jsonBody[UpdateRequest]).out(jsonBody[Long])

  val unregisterAccount: Endpoint[UUID, Unit, Unit, Any] =
    endpoint.delete.in(accountIdPath)

  val getUtxos
      : Endpoint[(UUID, Option[Int], Option[Int], Option[Sort]), Unit, GetUTXOsResult, Any] =
    endpoint.get
      .in(accountIdPath)
      .in("utxos")
      .in(limitQs)
      .in(offsetQs)
      .in(sortQs)
      .out(jsonBody[GetUTXOsResult])

  val getOperations: Endpoint[
    (UUID, Option[Long], Option[Int], Option[Int], Option[Sort]),
    Unit,
    GetOperationsResult,
    Any
  ] =
    endpoint.get
      .in(accountIdPath)
      .in("operations")
      .in(blockHeightQs)
      .in(limitQs)
      .in(offsetQs)
      .in(sortQs)
      .out(jsonBody[GetOperationsResult])

  val getBalances
      : Endpoint[(UUID, Option[Instant], Option[Instant]), Unit, Seq[BalanceHistory], Any] =
    endpoint.get
      .in(accountIdPath)
      .in("balances")
      .in(startQs)
      .in(endQs)
      .out(jsonBody[Seq[BalanceHistory]])

  val getAccountsRoutes =
    List(getAccount, createAccount, updateSyncFrequency, getOperations, getUtxos, getBalances)
}
