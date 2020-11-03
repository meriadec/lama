package co.ledger.lama.service.routes

import java.util.UUID

import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.service.BalanceHistory
import co.ledger.lama.bitcoin.interpreter.protobuf.{
  BitcoinInterpreterServiceFs2Grpc,
  GetBalanceHistoryRequest,
  GetBalanceRequest,
  GetOperationsRequest,
  GetUTXOsRequest,
  SortingOrder
}
import co.ledger.lama.common.Exceptions.MalformedProtobufUuidException
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{BitcoinNetwork, Coin, CoinFamily, Scheme, Sort}
import co.ledger.lama.common.models.implicits._
import co.ledger.lama.common.utils.{ProtobufUtils, UuidUtils}
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountManagerServiceFs2Grpc,
  RegisterAccountRequest,
  UnregisterAccountRequest,
  UpdateAccountRequest
}
import co.ledger.lama.service.utils.ProtobufUtils._
import co.ledger.lama.service.utils.RouterUtils._
import co.ledger.protobuf.bitcoin.{DeleteKeychainRequest, KeychainServiceFs2Grpc}
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
      keychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      accountManagerClient: AccountManagerServiceFs2Grpc[IO, Metadata],
      interpreterClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / UUIDVar(accountId) =>
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
          _ <- log.info(
            s"Account registered with id: ${UuidUtils.bytesToUuid(registeredAccount.accountId).getOrElse("")}"
          )
        } yield registeredAccount

        ra
          .map(fromRegisterAccount)
          .flatMap(Ok(_))

      case req @ PUT -> Root / UUIDVar(accountId) =>
        val r = for {
          updateRequest <- req.as[UpdateRequest]

          _ <- log.info(
            s"Updating account ${accountId} sync frequency with value ${updateRequest.syncFrequency}"
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

          _ <- log.info("Deleting keychain")

          _ <- keychainClient.deleteKeychain(
            new DeleteKeychainRequest(
              keychainId = UuidUtils.uuidToBytes(UUID.fromString(ai.key))
            ),
            new Metadata
          )

          _ <- log.info("Keychain deleted")
          _ <- log.info("Unregistering account")

          _ <- accountManagerClient.unregisterAccount(
            new UnregisterAccountRequest(
              UuidUtils.uuidToBytes(accountId)
            ),
            new Metadata
          )
          _ <- log.info("Account unregistered")
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
              new GetOperationsRequest(
                accountId = UuidUtils.uuidToBytes(accountId),
                blockHeight = blockHeight.getOrElse(0L),
                limit = limit.getOrElse(0),
                offset = offset.getOrElse(0),
                sort = sort.getOrElse(Sort.Descending) match {
                  case Sort.Ascending => SortingOrder.ASC
                  case _              => SortingOrder.DESC
                }
              ),
              new Metadata
            )
            .map(fromOperationListingInfos)
            .flatMap(Ok(_))

      case GET -> Root / UUIDVar(
            accountId
          ) / "utxos" :? OptionalLimitQueryParamMatcher(limit)
          +& OptionalOffsetQueryParamMatcher(offset) =>
        log.info(s"Fetching UTXOs for account: $accountId") *>
          interpreterClient
            .getUTXOs(
              new GetUTXOsRequest(
                accountId = UuidUtils.uuidToBytes(accountId),
                limit = limit.getOrElse(0),
                offset = offset.getOrElse(0)
              ),
              new Metadata
            )
            .map(fromUtxosListingInfo)
            .flatMap(Ok(_))

      case GET -> Root / UUIDVar(
            accountId
          ) / "balances" :? OptionalStartInstantQueryParamMatcher(start)
          +& OptionalEndInstantQueryParamMatcher(end) =>
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
