package co.ledger.lama.service.routes

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.interpreter.protobuf.{
  BitcoinInterpreterServiceFs2Grpc,
  GetOperationsRequest,
  SortingOrder
}
import co.ledger.lama.common.Exceptions.MalformedProtobufUuidException
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{BitcoinNetwork, Coin, CoinFamily, Scheme, Sort}
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountManagerServiceFs2Grpc,
  RegisterAccountRequest,
  UnregisterAccountRequest
}
import co.ledger.lama.service.utils.ProtobufUtils._
import co.ledger.lama.service.utils.RouterUtils._
import co.ledger.protobuf.bitcoin.{DeleteKeychainRequest, KeychainServiceFs2Grpc}
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.grpc.Metadata
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl

object AccountController extends Http4sDsl[IO] with IOLogging {

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
    implicit val encoder: Encoder[CreationRequest] = deriveEncoder[CreationRequest]
    implicit val decoder: Decoder[CreationRequest] = deriveDecoder[CreationRequest]
  }

  def routes(
      keychainClient: KeychainServiceFs2Grpc[IO, Metadata],
      accountManagerClient: AccountManagerServiceFs2Grpc[IO, Metadata],
      interpreterClient: BitcoinInterpreterServiceFs2Grpc[IO, Metadata]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "accounts" / UUIDVar(accountId) =>
        accountManagerClient
          .getAccountInfo(toAccountInfoRequest(accountId), new Metadata)
          .map(fromAccountInfoResult)
          .flatMap(Ok(_))

      case req @ POST -> Root / "accounts" =>
        val ra = for {
          creationRequest <- req.as[CreationRequest]
          _               <- log.info(s"Creating keychain with arguments: ${creationRequest}")
          createdKeychain <-
            keychainClient.createKeychain(toCreateKeychainRequest(creationRequest), new Metadata)
          keychainId <- IO.fromOption(
            UuidUtils.bytesToUuid(createdKeychain.keychainId)
          )(MalformedProtobufUuidException)
          _ = log.debug(s"Keychain created with id: $keychainId")
          _ = log.info("Registering account")
          registeredAccount <- accountManagerClient.registerAccount(
            new RegisterAccountRequest(
              key = keychainId.toString,
              coinFamily = toCoinFamily(creationRequest.coinFamily),
              coin = toCoin(creationRequest.coin),
              syncFrequency = creationRequest.syncFrequency.getOrElse(0L)
            ),
            new Metadata
          )
          _ = log.debug(
            s"Account registered with id: ${UuidUtils.bytesToUuid(registeredAccount.accountId).getOrElse("")}"
          )
        } yield registeredAccount

        ra
          .map(fromRegisterAccount)
          .flatMap(Ok(_))

      case DELETE -> Root / "accounts" / UUIDVar(accountId) =>
        log.info(s"Fetching account informations for id: ${accountId}")
        val r = for {
          ai <- accountManagerClient.getAccountInfo(
            new AccountInfoRequest(UuidUtils.uuidToBytes(accountId)),
            new Metadata
          )

          _ = log.info("Deleting keychain")

          _ <- keychainClient.deleteKeychain(
            new DeleteKeychainRequest(
              keychainId = UuidUtils.uuidToBytes(UUID.fromString(ai.key))
            ),
            new Metadata
          )

          _ = log.info("Keychain deleted")
          _ = log.info("Unregistering account")

          _ <- accountManagerClient.unregisterAccount(
            new UnregisterAccountRequest(
              UuidUtils.uuidToBytes(accountId)
            ),
            new Metadata
          )
          _ = log.info("Account unregistered")
        } yield ()

        r.flatMap(_ => Ok())

      case GET -> Root / "accounts" / UUIDVar(
            accountId
          ) / "operations" :? OptionalBlockHeightQueryParamMatcher(blockHeight)
          +& OptionalLimitQueryParamMatcher(limit)
          +& OptionalOffsetQueryParamMatcher(offset)
          +& OptionalSortQueryParamMatcher(sort) =>
        log.info(s"Fetching UTXOs for account: $accountId")
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
          .map(fromTransactionListingInfos)
          .flatMap(Ok(_))
    }
}
