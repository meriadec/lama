package co.ledger.lama.manager

import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

import co.ledger.lama.common.models.SyncEvent.Payload
import co.ledger.lama.common.models._
import co.ledger.lama.manager.models.implicits._

import scala.concurrent.duration.FiniteDuration
import doobie.postgres.implicits._
import doobie.implicits.legacy.instant._
import doobie.util.Read

package object models {

  case class AccountInfo(
      id: UUID,
      key: String,
      coinFamily: CoinFamily,
      coin: Coin,
      syncFrequency: FiniteDuration
  )

  object AccountInfo {
    implicit val doobieRead: Read[AccountInfo] =
      Read[(UUID, String, CoinFamily, Coin, Long)].map {
        case (accountId, key, coinFamily, coin, syncFrequencyInSeconds) =>
          AccountInfo(
            accountId,
            key,
            coinFamily,
            coin,
            FiniteDuration(syncFrequencyInSeconds, TimeUnit.SECONDS)
          )
      }
  }

  case class AccountSyncStatus(
      id: UUID,
      key: String,
      coinFamily: CoinFamily,
      coin: Coin,
      syncFrequency: FiniteDuration,
      syncId: UUID,
      status: Status,
      payload: Payload,
      updated: Instant
  )

  object AccountSyncStatus {
    implicit val doobieRead: Read[AccountSyncStatus] =
      Read[(UUID, String, CoinFamily, Coin, Long, UUID, Status, Payload, Instant)].map {
        case (
              accountId,
              key,
              coinFamily,
              coin,
              syncFrequencyInSeconds,
              syncId,
              status,
              payload,
              updated
            ) =>
          AccountSyncStatus(
            accountId,
            key,
            coinFamily,
            coin,
            FiniteDuration(syncFrequencyInSeconds, TimeUnit.SECONDS),
            syncId,
            status,
            payload,
            updated
          )
      }
  }
}
