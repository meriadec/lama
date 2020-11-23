package co.ledger.lama.bitcoin.api.routes

import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.Exceptions.MalformedProtobufUuidException
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{Coin, CoinFamily}
import co.ledger.lama.common.services.NotificationService
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountManagerServiceFs2Grpc,
  RegisterAccountRequest,
  UnregisterAccountRequest,
  UpdateAccountRequest
}
import co.ledger.lama.bitcoin.api.utils.ProtobufUtils._
import co.ledger.lama.bitcoin.api.utils.RouterUtils._
import co.ledger.lama.bitcoin.common.models.{BitcoinNetwork, Scheme}
import co.ledger.lama.bitcoin.common.services.InterpreterClientService
import co.ledger.protobuf.bitcoin.keychain.{DeleteKeychainRequest, KeychainServiceFs2Grpc}
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.auto._
import io.circe.generic.extras.semiauto._
import io.grpc.Metadata
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl

object AccountController extends Http4sDsl[IO] with IOLogging {

  implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

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

  def routes(
      notificationService: NotificationService,
      keychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      accountManagerClient: AccountManagerServiceFs2Grpc[IO, Metadata],
      interpreterClient: InterpreterClientService
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / UUIDVar(accountId) =>
        accountManagerClient
          .getAccountInfo(toAccountInfoRequest(accountId), new Metadata)
          .parProduct(interpreterClient.getBalance(accountId))
          .flatMap { case (info, balance) =>
            Ok(fromAccountInfo(info, balance))
          }

      case req @ POST -> Root =>
        val ra = for {
          creationRequest <- req.as[CreationRequest]
          _               <- log.info(s"Creating keychain with arguments: $creationRequest")
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
    }

}
