package co.ledger.lama.bitcoin.api.routes

import cats.effect.{ContextShift, IO}
import cats.implicits._
import co.ledger.lama.bitcoin.api.models.accountManager._
import co.ledger.lama.bitcoin.api.models.transactor._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.services.NotificationService
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.common.utils.{ProtobufUtils => CommonProtobufUtils}
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountLabel,
  AccountManagerServiceFs2Grpc,
  GetAccountsRequest,
  RegisterAccountRequest,
  UnregisterAccountRequest,
  UpdateAccountRequest
}
import co.ledger.lama.bitcoin.api.utils.ProtobufUtils._
import co.ledger.lama.bitcoin.api.utils.RouterUtils._
import co.ledger.lama.bitcoin.common.models.interpreter.ChangeType
import co.ledger.lama.bitcoin.common.grpc.{
  InterpreterClientService,
  KeychainClientService,
  TransactorClientService
}
import io.circe.Json
import io.circe.generic.extras.auto._
import io.grpc.Metadata
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import io.circe.syntax._

object AccountController extends Http4sDsl[IO] with IOLogging {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  // TODO: refacto with accountManagerService
  def routes(
      notificationService: NotificationService,
      keychainClient: KeychainClientService,
      accountManagerClient: AccountManagerServiceFs2Grpc[IO, Metadata],
      interpreterClient: InterpreterClientService,
      transactorClient: TransactorClientService
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root
          :? OptionalLimitQueryParamMatcher(limit)
          +& OptionalOffsetQueryParamMatcher(offset) =>
        val t = for {
          accountsResult <- accountManagerClient.getAccounts(
            GetAccountsRequest(limit.getOrElse(0), offset.getOrElse(0)),
            new Metadata
          )

          accounts = accountsResult.accounts.toList

          accountsWithIds <- accounts
            .parTraverse(account =>
              UuidUtils.bytesToUuidIO(account.accountId).map(accountId => accountId -> account)
            )

          accountsWithBalances <- accountsWithIds.parTraverse { case (accountId, account) =>
            interpreterClient.getBalance(accountId).map(balance => account -> balance)
          }

        } yield (accountsWithBalances, accountsResult.total)

        t.flatMap { case (accountsWithBalances, total) =>
          val accountsInfos = accountsWithBalances.map { case (account, balance) =>
            fromAccountInfo(account, balance)
          }

          Ok(
            Json.obj(
              "accounts" -> Json.fromValues(accountsInfos.map(_.asJson)),
              "total"    -> Json.fromInt(total)
            )
          )
        }

      case GET -> Root / UUIDVar(accountId) =>
        accountManagerClient
          .getAccountInfo(toAccountInfoRequest(accountId), new Metadata)
          .parProduct(interpreterClient.getBalance(accountId))
          .flatMap { case (info, balance) =>
            Ok(fromAccountInfo(info, balance))
          }

      case GET -> Root / UUIDVar(accountId) / "events"
          :? OptionalLimitQueryParamMatcher(limit)
          +& OptionalOffsetQueryParamMatcher(offset)
          +& OptionalSortQueryParamMatcher(sort) =>
        accountManagerClient
          .getSyncEvents(toGetSyncEventsRequest(accountId, limit, offset, sort), new Metadata)
          .flatMap { res =>
            Ok(
              Json.obj(
                "events" ->
                  Json.fromValues(
                    res.syncEvents.map(se => CommonProtobufUtils.fromSyncEvent(se).asJson)
                  ),
                "total" -> Json.fromInt(res.total)
              )
            )
          }

      case req @ POST -> Root =>
        val ra = for {
          creationRequest <- req.as[CreationRequest]
          _               <- log.info(s"Creating keychain with arguments: $creationRequest")

          createdKeychain <- keychainClient.create(
            creationRequest.extendedPublicKey,
            creationRequest.scheme,
            creationRequest.lookaheadSize,
            creationRequest.network
          )
          _ <- log.info(s"Keychain created with id: ${createdKeychain.keychainId}")
          _ <- log.info("Registering account")

          registeredAccount <- accountManagerClient.registerAccount(
            new RegisterAccountRequest(
              key = createdKeychain.keychainId.toString,
              coinFamily = CommonProtobufUtils.toCoinFamily(creationRequest.coinFamily),
              coin = CommonProtobufUtils.toCoin(creationRequest.coin),
              syncFrequency = creationRequest.syncFrequency.getOrElse(0L),
              label = creationRequest.label.map(AccountLabel(_))
            ),
            new Metadata
          )

          account = fromRegisterAccount(registeredAccount)

          // This creates a new queue for this account notifications
          _ <- notificationService.createQueue(
            account.accountId,
            creationRequest.coinFamily,
            creationRequest.coin
          )

          _ <- log.info(
            s"Account registered with id: ${account.accountId}"
          )
        } yield account

        ra.flatMap(Ok(_))

      case req @ PUT -> Root / UUIDVar(accountId) =>
        val r = for {
          updateRequest <- req.as[UpdateRequest]

          _ <- log.info(
            s"Updating account $accountId sync frequency with value ${updateRequest.syncFrequency}"
          )

          _ <- accountManagerClient.updateAccount(
            new UpdateAccountRequest(
              accountId = UuidUtils.uuidToBytes(accountId),
              syncFrequency = updateRequest.syncFrequency
            ),
            new Metadata
          )
        } yield updateRequest.syncFrequency
        r.flatMap(_ => Ok())

      case DELETE -> Root / UUIDVar(accountId) =>
        log.info(s"Fetching account informations for id: $accountId")
        val r = for {
          _ <- log.info(s"Fetching account informations for id: $accountId")

          account <- accountManagerClient.getAccountInfo(
            new AccountInfoRequest(UuidUtils.uuidToBytes(accountId)),
            new Metadata
          )

          accountId <- UuidUtils.bytesToUuidIO(account.accountId)

          _ <- log.info("Deleting keychain")

          keychainId <- UuidUtils.stringToUuidIO(account.key)

          _ <- keychainClient
            .deleteKeychain(keychainId)
            .map(_ => log.info("Keychain deleted"))
            .handleErrorWith(_ =>
              log.info("An error occurred while deleting the keychain, moving on")
            )

          _ <- log.info("Unregistering account")

          _ <- accountManagerClient.unregisterAccount(
            new UnregisterAccountRequest(
              UuidUtils.uuidToBytes(accountId)
            ),
            new Metadata
          )
          _ <- log.info("Account unregistered")
          _ <- log.info("Deleting queue")

          _ <- notificationService.deleteQueue(
            accountId = accountId,
            coinFamily = CommonProtobufUtils.fromCoinFamily(account.coinFamily),
            coin = CommonProtobufUtils.fromCoin(account.coin)
          )
          _ <- log.info("Queue deleted")

        } yield ()

        r.flatMap(_ => Ok())

      case GET -> Root / UUIDVar(
            accountId
          ) / "operations" :? OptionalBlockHeightQueryParamMatcher(blockHeight)
          +& OptionalLimitQueryParamMatcher(limit)
          +& OptionalOffsetQueryParamMatcher(offset)
          +& OptionalSortQueryParamMatcher(sort) =>
        log.info(s"Fetching operations for account: $accountId") *>
          interpreterClient
            .getOperations(
              accountId = accountId,
              blockHeight = blockHeight.getOrElse(0L),
              limit = limit.getOrElse(0),
              offset = offset.getOrElse(0),
              sort = sort
            )
            .flatMap(Ok(_))

      case GET -> Root / UUIDVar(
            accountId
          ) / "utxos" :? OptionalLimitQueryParamMatcher(limit)
          +& OptionalOffsetQueryParamMatcher(offset)
          +& OptionalSortQueryParamMatcher(sort) =>
        log.info(s"Fetching UTXOs for account: $accountId") *>
          interpreterClient
            .getUTXOs(
              accountId = accountId,
              limit = limit.getOrElse(0),
              offset = offset.getOrElse(0),
              sort = sort
            )
            .flatMap(Ok(_))

      case GET -> Root / UUIDVar(
            accountId
          ) / "balances" :? OptionalStartInstantQueryParamMatcher(start)
          +& OptionalEndInstantQueryParamMatcher(end) =>
        log.info(s"Fetching balances history for account: $accountId") *>
          interpreterClient
            .getBalanceHistory(
              accountId,
              start,
              end
            )
            .flatMap(Ok(_))

      case GET -> Root / UUIDVar(
            accountId
          ) / "balances" / "demo" =>
        log.info(s"Fetching balances history for account: $accountId") *>
          interpreterClient
            .getBalanceHistories(accountId)
            .flatMap(Ok(_))

      case req @ POST -> Root / UUIDVar(
            accountId
          ) / "transactions" =>
        for {
          _                        <- log.info(s"Preparing transaction creation for account: $accountId")
          createTransactionRequest <- req.as[CreateTransactionRequest]

          account <- accountManagerClient.getAccountInfo(
            new AccountInfoRequest(UuidUtils.uuidToBytes(accountId)),
            new Metadata
          )

          keychainId <- UuidUtils.stringToUuidIO(account.key)

          response <- transactorClient
            .createTransaction(
              accountId,
              keychainId,
              createTransactionRequest.coinSelection,
              createTransactionRequest.outputs,
              CommonProtobufUtils.fromCoin(account.coin)
            )
            .flatMap(Ok(_))

        } yield response

      case req @ POST -> Root / "_internal" / "sign" =>
        for {
          _       <- log.info(s"Signing Transaction")
          request <- req.as[GenerateSignaturesRequest]

          response <- transactorClient
            .generateSignature(
              request.rawTransaction,
              request.privKey
            )
            .flatMap(Ok(_))

        } yield response

      case req @ POST -> Root / UUIDVar(
            accountId
          ) / "transactions" / "send" =>
        for {
          _       <- log.info(s"Preparing transaction creation for account: $accountId")
          request <- req.as[BroadcastTransactionRequest]

          account <- accountManagerClient.getAccountInfo(
            new AccountInfoRequest(UuidUtils.uuidToBytes(accountId)),
            new Metadata
          )

          keychainId <- UuidUtils.stringToUuidIO(account.key)

          response <- transactorClient
            .broadcastTransaction(
              keychainId,
              account.coin.name,
              request.rawTransaction,
              request.signatures
            )
            .flatMap(Ok(_))

        } yield response

      case GET -> Root / UUIDVar(
            accountId
          ) / "addresses" :? OptionalFromIndexQueryParamMatcher(from)
          +& OptionalToIndexQueryParamMatcher(to)
          +& OptionalChangeTypeParamMatcher(change) =>
        for {
          account <- accountManagerClient.getAccountInfo(
            new AccountInfoRequest(UuidUtils.uuidToBytes(accountId)),
            new Metadata
          )

          keychainId <- UuidUtils.stringToUuidIO(account.key)

          response <- keychainClient
            .getAddresses(
              keychainId,
              from.getOrElse(0),
              to.getOrElse(0),
              change
            )
            .flatMap(Ok(_))

        } yield response

      case GET -> Root / UUIDVar(
            accountId
          ) / "addresses" / "fresh" :? OptionalChangeTypeParamMatcher(change) =>
        // TODO: refactor when moving keychainService into bitcoin.common
        for {
          account <- accountManagerClient.getAccountInfo(
            new AccountInfoRequest(UuidUtils.uuidToBytes(accountId)),
            new Metadata
          )

          keychainId <- UuidUtils.stringToUuidIO(account.key)

          keychainInfo <- keychainClient
            .getKeychainInfo(keychainId)

          response <- keychainClient
            .getFreshAddresses(
              keychainId = keychainId,
              change = change.getOrElse(ChangeType.External),
              size = keychainInfo.lookaheadSize
            )
            .flatMap(Ok(_))

        } yield response
    }

}
