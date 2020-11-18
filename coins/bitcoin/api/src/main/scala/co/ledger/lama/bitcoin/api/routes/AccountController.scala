package co.ledger.lama.bitcoin.api.routes

import java.time.Instant
import java.util.UUID

import cats.effect.IO.ioEffect
import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.api.routes.AccountController.{CreationRequest, UpdateRequest}
import co.ledger.lama.bitcoin.common.models.service.BalanceHistory
import co.ledger.lama.bitcoin.interpreter.protobuf.{
  BitcoinInterpreterServiceFs2Grpc,
  GetBalanceHistoryRequest,
  GetBalanceRequest,
  GetOperationsRequest,
  GetUTXOsRequest
}
import co.ledger.lama.common.Exceptions.MalformedProtobufUuidException
import co.ledger.lama.common.logging.IOLogger
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
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
import io.grpc.Metadata
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe.jsonOf
import org.http4s.rho.RhoRoutes
import org.http4s.rho.bits._
import co.ledger.lama.bitcoin.api.routes.AccountController._
import org.http4s.rho.swagger.SwaggerSyntax

class AccountController(
    notificationService: NotificationService,
    keychainClient: KeychainServiceFs2Grpc[IO, Metadata],
    accountManagerClient: AccountManagerServiceFs2Grpc[IO, Metadata],
    interpreterClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata],
    swaggerSyntax: SwaggerSyntax[IO],
    log: IOLogger
) extends RhoRoutes[IO] {

  import swaggerSyntax._

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

  private val accountIdPv = pathVar[UUID]("accountId")

  "Returns an account informations" **
    List("accounts") @@
    GET / accountIdPv |>> { (accountId: UUID) =>
      accountManagerClient
        .getAccountInfo(toAccountInfoRequest(accountId), new Metadata)
        .parProduct(
          interpreterClient
            .getBalance(
              GetBalanceRequest(UuidUtils.uuidToBytes(accountId)),
              new Metadata
            )
        )
        .flatMap { case (info, balance) =>
          Ok(fromAccountInfo(info, balance))
        }
    }

  "Creates an account" **
    List("accounts") @@
    POST ^ jsonOf[IO, CreationRequest] |>> { (creationRequest: CreationRequest) =>
      val ra = for {
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
      } yield account

      ra.flatMap(Ok(_))

    }

  "Updates an account sync frequency" **
    List("accounts") @@
    PUT / accountIdPv ^ jsonOf[IO, UpdateRequest] |>> {
      (accountId: UUID, updateRequest: UpdateRequest) =>
        val r = for {
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
        r.flatMap(_ => Ok(()))
    }

  "Removes an account and all affiliated stuff" **
    List("accounts") @@
    DELETE / accountIdPv |>> { (accountId: UUID) =>
      log.info(s"Fetching account informations for id: $accountId")
      val r = for {
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

      } yield ()

      r.flatMap(_ => Ok(()))
    }

  "Returns an account operations" **
    List("accounts") @@
    GET / accountIdPv / "operations" +? param[Option[Long]]("blockHeight") & param[Option[
      Int
    ]]("limit") & param[Option[Int]]("offset") & param[Option[Sort]]("sort") |>> {
      (
          accountId: UUID,
          blockHeight: Option[Long],
          limit: Option[Int],
          offset: Option[Int],
          sort: Option[Sort]
      ) =>
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
            .map(fromOperationListingInfos)
            .flatMap(Ok(_))
    }

  "Returns an account utxos" **
    List("accounts") @@
    GET / accountIdPv / "utxos" +? param[Option[Int]]("limit") & param[Option[Int]](
      "offset"
    ) & param[Option[Sort]]("sort") |>> {
      (accountId: UUID, limit: Option[Int], offset: Option[Int], sort: Option[Sort]) =>
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
            .map(fromUtxosListingInfo)
            .flatMap(Ok(_))
    }

  "Returns an account balances" **
    List("accounts") @@
    GET / accountIdPv / "balances" +? param[Option[Instant]]("start") & param[Option[
      Instant
    ]](
      "end"
    ) |>> { (accountId: UUID, start: Option[Instant], end: Option[Instant]) =>
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
          .map(_.balances.map(BalanceHistory.fromProto))
          .flatMap(Ok(_))
    }
}

object AccountController {
  case class UpdateRequest(syncFrequency: Long)

  object UpdateRequest {
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
    implicit val encoder: Encoder[CreationRequest] = deriveConfiguredEncoder[CreationRequest]
    implicit val decoder: Decoder[CreationRequest] = deriveConfiguredDecoder[CreationRequest]
  }

  implicit def sortParser: StringParser[IO, Option[Sort]] =
    StringParser.strParser[IO].map(s => Some(Sort.fromKey(s).getOrElse(Sort.Descending)))
}
