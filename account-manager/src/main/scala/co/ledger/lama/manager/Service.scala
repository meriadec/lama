package co.ledger.lama.manager

import java.util.UUID
import java.util.concurrent.TimeUnit

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.common.models._
import co.ledger.lama.common.models.SyncEvent.Payload
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.manager.Exceptions.{
  AccountNotFoundException,
  CoinConfigurationException,
  MalformedProtobufException
}
import co.ledger.lama.manager.config.CoinConfig
import co.ledger.lama.manager.models.AccountInfo
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountInfoResult,
  AccountManagerServiceFs2Grpc,
  RegisterAccountRequest,
  RegisterAccountResult,
  UnregisterAccountRequest,
  UnregisterAccountResult
}
import co.ledger.lama.manager.utils.ProtobufUtils
import com.google.protobuf.ByteString
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.Json
import io.circe.syntax._
import io.grpc.Metadata

import scala.concurrent.duration.FiniteDuration

class Service(val db: Transactor[IO], val coinConfigs: List[CoinConfig])
    extends AccountManagerServiceFs2Grpc[IO, Metadata] {

  def definition(implicit ce: ConcurrentEffect[IO]) = AccountManagerServiceFs2Grpc.bindService(this)

  def registerAccount(
      request: RegisterAccountRequest,
      ctx: Metadata
  ): IO[RegisterAccountResult] = {
    val account    = ProtobufUtils.from(request)
    val coinFamily = account.coinFamily
    val coin       = account.coin
    val cursor     = cursorToJson(request)

    val syncFrequencyFromRequest =
      if (request.syncFrequency > 0L)
        Some(FiniteDuration(request.syncFrequency, TimeUnit.SECONDS))
      else
        None

    for {
      // Get the sync frequency from the request
      // or fallback to the default one from the coin configuration.
      syncFrequency <- IO.fromOption {
        syncFrequencyFromRequest orElse
          coinConfigs
            .find(c => c.coinFamily == coinFamily && c.coin == coin)
            .map(_.syncFrequency)
      }(CoinConfigurationException(coinFamily, coin))

      // Build queries.
      queries = for {
        // Upsert the account info.
        accountInfo <-
          Queries
            .upsertAccountInfo(
              account,
              syncFrequency
            )
        accountId     = accountInfo.id
        syncFrequency = accountInfo.syncFrequency

        // Create then insert the registered event.
        syncEvent = WorkableEvent(
          account.id,
          UUID.randomUUID(),
          Status.Registered,
          Payload(account, cursor)
        )
        _ <- Queries.insertSyncEvent(syncEvent)

      } yield (accountId, syncEvent.syncId, syncFrequency)

      response <-
        // Run queries and return an sync event result.
        queries
          .transact(db)
          .map {
            case (accountId, syncId, syncFrequency) =>
              RegisterAccountResult(
                UuidUtils.uuidToBytes(accountId),
                UuidUtils.uuidToBytes(syncId),
                syncFrequency.toSeconds
              )
          }
    } yield response
  }

  def unregisterAccount(
      request: UnregisterAccountRequest,
      ctx: Metadata
  ): IO[UnregisterAccountResult] = {
    val accountIdOpt = UuidUtils.bytesToUuid(request.accountId)

    for {
      accountId <- IO.fromOption(accountIdOpt)(
        new Exception(s"${request.accountId} is not a valid UUID")
      )

      existing <-
        Queries
          .getLastSyncEvent(accountId)
          .transact(db)
          .map(_.filter(e => e.status == Status.Unregistered || e.status == Status.Deleted))

      result <- existing match {
        case Some(e) =>
          IO.pure(
            UnregisterAccountResult(
              UuidUtils.uuidToBytes(e.accountId),
              UuidUtils.uuidToBytes(e.syncId)
            )
          )

        case _ =>
          for {
            account <- getAccountInfo(accountId)

            // Create then insert an unregistered event.
            event = WorkableEvent(
              accountId,
              UUID.randomUUID(),
              Status.Unregistered,
              Payload(AccountIdentifier(account.key, account.coinFamily, account.coin))
            )

            result <-
              Queries
                .insertSyncEvent(event)
                .transact(db)
                .map(_ =>
                  UnregisterAccountResult(
                    UuidUtils.uuidToBytes(event.accountId),
                    UuidUtils.uuidToBytes(event.syncId)
                  )
                )
          } yield result
      }
    } yield result
  }

  private def cursorToJson(request: protobuf.RegisterAccountRequest): Json = {
    if (request.cursor.isBlockHeight)
      Json.obj(
        "blockHeight" -> Json.fromLong(
          request.cursor.blockHeight
            .map(_.state)
            .getOrElse(throw MalformedProtobufException(request))
        )
      )
    else
      Json.obj()
  }

  def getAccountInfo(request: AccountInfoRequest, ctx: Metadata): IO[AccountInfoResult] = {
    val accountIdOpt = UuidUtils.bytesToUuid(request.accountId)

    for {
      accountId <- IO.fromOption(accountIdOpt)(
        new Exception(s"${request.accountId} is not a valid UUID")
      )
      accountInfo   <- getAccountInfo(accountId)
      lastSyncEvent <- Queries.getLastSyncEvent(accountInfo.id).transact(db)
    } yield {
      val lastSyncEventProto = lastSyncEvent.map { se =>
        protobuf.SyncEvent(
          UuidUtils.uuidToBytes(se.syncId),
          se.status.name,
          ByteString.copyFrom(se.payload.asJson.noSpaces.getBytes())
        )
      }

      AccountInfoResult(
        UuidUtils.uuidToBytes(accountInfo.id),
        accountInfo.key,
        accountInfo.syncFrequency.toSeconds,
        lastSyncEventProto
      )
    }
  }

  private def getAccountInfo(accountId: UUID): IO[AccountInfo] =
    Queries
      .getAccountInfo(accountId)
      .transact(db)
      .flatMap {
        IO.fromOption(_)(AccountNotFoundException(accountId))
      }

}
