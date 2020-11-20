package co.ledger.lama.bitcoin.api.routes

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.api.endpoints.AccountsEndpoints._
import co.ledger.lama.bitcoin.common.models.service.BalanceHistory
import co.ledger.lama.bitcoin.interpreter.protobuf.{
  BitcoinInterpreterServiceFs2Grpc,
  GetBalanceHistoryRequest,
  GetBalanceRequest,
  GetOperationsRequest,
  GetUTXOsRequest
}
import co.ledger.lama.common.Exceptions.MalformedProtobufUuidException
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{BitcoinNetwork, Coin, CoinFamily, Scheme, Sort}
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.services.NotificationService
import co.ledger.lama.common.utils.{ProtobufUtils, UuidUtils}
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountManagerServiceFs2Grpc,
  RegisterAccountRequest,
  UnregisterAccountRequest,
  UpdateAccountRequest
}
import co.ledger.lama.bitcoin.api.utils.ProtobufUtils._
import co.ledger.lama.bitcoin.api.utils.RouterUtils._
import co.ledger.protobuf.bitcoin.{DeleteKeychainRequest, KeychainServiceFs2Grpc}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._
import io.grpc.Metadata
import org.http4s.HttpRoutes
import sttp.tapir.{Schema, Validator}
import sttp.tapir.server.http4s._
import cats.syntax.all._

import scala.concurrent.ExecutionContext

object AccountController extends IOLogging {

  implicit val ec: ExecutionContext           = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO]               = IO.timer(ec)

  case class UpdateRequest(syncFrequency: Long)

  object UpdateRequest {
    implicit val sUpdateRequest: Schema[UpdateRequest]            = Schema.derive
    implicit val updateRequestValidator: Validator[UpdateRequest] = Validator.derive

    implicit val encoder: Encoder[UpdateRequest] = deriveConfiguredEncoder[UpdateRequest]
    implicit val decoder: Decoder[UpdateRequest] = deriveConfiguredDecoder[UpdateRequest]
  }

  case class CreationRequest(
      extendedPublicKey: String,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinNetwork,
      coinFamily: CoinFamily,
      coin: Coin,
      syncFrequency: Option[Long]
  )

  object CreationRequest {
    implicit val sCreationRequest: Schema[CreationRequest]            = Schema.derive
    implicit val creationRequestValidator: Validator[CreationRequest] = Validator.derive

    implicit val encoder: Encoder[CreationRequest] = deriveConfiguredEncoder[CreationRequest]
    implicit val decoder: Decoder[CreationRequest] = deriveConfiguredDecoder[CreationRequest]
  }

  def routes(
      notificationService: NotificationService,
      keychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      accountManagerClient: AccountManagerServiceFs2Grpc[IO, Metadata],
      interpreterClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata]
  ): HttpRoutes[IO] =
    getAccount.toRoutes(accountId =>
      accountManagerClient
        .getAccountInfo(toAccountInfoRequest(accountId), new Metadata)
        .parProduct(
          interpreterClient
            .getBalance(
              GetBalanceRequest(UuidUtils.uuidToBytes(accountId)),
              new Metadata
            )
        )
        .map { case (info, balance) =>
          Right(fromAccountInfo(info, balance))
        }
    ) <+> createAccount.toRoutes(creationRequest => {
      for {
        _ <- log.info(s"Creating keychain with arguments: $creationRequest")
        createdKeychain <-
          keychainClient.createKeychain(toCreateKeychainRequest(creationRequest), new Metadata)
        keychainId <- IO.fromOption(
          UuidUtils.bytesToUuid(createdKeychain.keychainId)
        )(MalformedProtobufUuidException)
        _ <- log.info(s"Keychain created with id: $keychainId")
        _ <- log.info("Registering account")
        registeredAccount <- accountManagerClient.registerAccount(
          new RegisterAccountRequest(
            key = keychainId.toString,
            coinFamily = toCoinFamily(creationRequest.coinFamily),
            coin = toCoin(creationRequest.coin),
            syncFrequency = creationRequest.syncFrequency.getOrElse(0L)
          ),
          new Metadata
        )

        account = fromRegisterAccount(registeredAccount)

        // This creates a new queue for this account notifications
        _ <- notificationService.createQueue(account.accountId, CoinFamily.Bitcoin, Coin.Btc)

        _ <- log.info(
          s"Account registered with id: ${account.accountId}"
        )
      } yield Right(account)
    }) <+> updateSyncFrequency.toRoutes { case (accountId, updateRequest) =>
      for {
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
      } yield Right(updateRequest.syncFrequency)
    } <+> unregisterAccount.toRoutes(accountId =>
      for {
        _ <- log.info(s"Fetching account informations for id: $accountId")

        ai <- accountManagerClient.getAccountInfo(
          new AccountInfoRequest(UuidUtils.uuidToBytes(accountId)),
          new Metadata
        )

        accountId <- UuidUtils.bytesToUuidIO(ai.accountId)

        _ <- log.info("Deleting keychain")

        _ <- keychainClient
          .deleteKeychain(
            new DeleteKeychainRequest(
              keychainId = UuidUtils.uuidToBytes(UUID.fromString(ai.key))
            ),
            new Metadata
          )
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
          coinFamily = CoinFamily.Bitcoin,
          coin = Coin.Btc
        )
        _ <- log.info("Queue deleted")

      } yield Right(())
    ) <+> getOperations.toRoutes { case (accountId, blockHeight, limit, offset, sort) =>
      log.info(s"Fetching operations for account: $accountId") *>
        interpreterClient
          .getOperations(
            new GetOperationsRequest(
              accountId = UuidUtils.uuidToBytes(accountId),
              blockHeight = blockHeight.getOrElse(0L),
              limit = limit.getOrElse(0),
              offset = offset.getOrElse(0),
              sort = parseSorting(sort)
            ),
            new Metadata
          )
          .map(res => Right(fromOperationListingInfos(res)))
    } <+> getUtxos.toRoutes { case (accountId, limit, offset, sort) =>
      log.info(s"Fetching UTXOs for account: $accountId") *>
        interpreterClient
          .getUTXOs(
            new GetUTXOsRequest(
              accountId = UuidUtils.uuidToBytes(accountId),
              limit = limit.getOrElse(0),
              offset = offset.getOrElse(0),
              sort = parseSorting(sort, Sort.Ascending)
            ),
            new Metadata
          )
          .map(res => Right(fromUtxosListingInfo(res)))
    } <+> getBalances.toRoutes { case (accountId, start, end) =>
      log.info(s"Fetching balances history for account: $accountId") *>
        interpreterClient
          .getBalanceHistory(
            GetBalanceHistoryRequest(
              UuidUtils.uuidToBytes(accountId),
              start.map(ProtobufUtils.fromInstant),
              end.map(ProtobufUtils.fromInstant)
            ),
            new Metadata()
          )
          .map(res => Right(res.balances.map(BalanceHistory.fromProto)))
    }
}
