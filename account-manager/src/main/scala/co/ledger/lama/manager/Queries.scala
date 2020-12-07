package co.ledger.lama.manager

import java.util.UUID

import cats.data.NonEmptyList
import co.ledger.lama.common.models.{
  AccountIdentifier,
  Coin,
  CoinFamily,
  Sort,
  SyncEvent,
  TriggerableEvent,
  TriggerableStatus,
  WorkableEvent,
  WorkableStatus
}
import co.ledger.lama.manager.models._
import co.ledger.lama.manager.models.implicits._
import doobie.{Fragment, Fragments}
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import fs2.Stream
import org.postgresql.util.PGInterval

import scala.concurrent.duration.FiniteDuration

object Queries {

  def countAccounts(): ConnectionIO[Int] =
    sql"""SELECT COUNT(*) FROM account_sync_status"""
      .query[Int]
      .unique

  def fetchPublishableEvents(
      coinFamily: CoinFamily,
      coin: Coin
  ): Stream[ConnectionIO, WorkableEvent] =
    (
      sql"""SELECT account_id, sync_id, status, payload, updated
          FROM account_sync_status
          WHERE coin_family = $coinFamily
          AND coin = $coin
          AND """
        ++ Fragments.in(fr"status", NonEmptyList.fromListUnsafe(WorkableStatus.all.values.toList))
    ).query[WorkableEvent].stream

  def fetchTriggerableEvents(
      coinFamily: CoinFamily,
      coin: Coin
  ): Stream[ConnectionIO, TriggerableEvent] =
    (
      sql"""SELECT account_id, sync_id, status, payload, updated
          FROM account_sync_status
          WHERE updated + sync_frequency < CURRENT_TIMESTAMP
          AND coin_family = $coinFamily
          AND coin = $coin
          AND """
        ++ Fragments.in(
          fr"status",
          NonEmptyList.fromListUnsafe(TriggerableStatus.all.values.toList)
        )
    ).query[TriggerableEvent].stream

  def getAccounts(offset: Int, limit: Int): Stream[ConnectionIO, AccountSyncStatus] =
    sql"""SELECT account_id, key, coin_family, coin,
            extract(epoch FROM sync_frequency) / 60 * 60, label,
            sync_id, status, payload, updated
          FROM account_sync_status
          LIMIT $limit
          OFFSET $offset
         """
      .query[AccountSyncStatus]
      .stream

  def getAccountInfo(accountId: UUID): ConnectionIO[Option[AccountInfo]] =
    sql"""SELECT account_id, key, coin_family, coin, extract(epoch FROM sync_frequency)/60*60, label
          FROM account_info
          WHERE account_id = $accountId
         """
      .query[AccountInfo]
      .option

  def updateAccountSyncFrequency(
      accountId: UUID,
      syncFrequency: FiniteDuration
  ): ConnectionIO[Int] = {
    val syncFrequencyInterval = new PGInterval()
    syncFrequencyInterval.setSeconds(syncFrequency.toSeconds.toDouble)

    sql"""UPDATE account_info SET sync_frequency=$syncFrequencyInterval WHERE account_id = $accountId
          """.update.run
  }

  def insertAccountInfo(
      accountIdentifier: AccountIdentifier,
      label: Option[String],
      syncFrequency: FiniteDuration
  ): ConnectionIO[AccountInfo] = {
    val accountId  = accountIdentifier.id
    val key        = accountIdentifier.key
    val coinFamily = accountIdentifier.coinFamily
    val coin       = accountIdentifier.coin

    val syncFrequencyInterval = new PGInterval()
    syncFrequencyInterval.setSeconds(syncFrequency.toSeconds.toDouble)

    sql"""INSERT INTO account_info(account_id, key, coin_family, coin, sync_frequency, label)
          VALUES($accountId, $key, $coinFamily, $coin, $syncFrequencyInterval, $label)
          RETURNING account_id, key, coin_family, coin, extract(epoch FROM sync_frequency)/60*60, label
          """
      .query[AccountInfo]
      .unique
  }

  def insertSyncEvent(e: SyncEvent): ConnectionIO[Int] =
    sql"""
         INSERT INTO account_sync_event(account_id, sync_id, status, payload)
         VALUES(${e.accountId}, ${e.syncId}, ${e.status}, ${e.payload})
         """.update.run

  def getLastSyncEvent(accountId: UUID): ConnectionIO[Option[SyncEvent]] =
    sql"""SELECT account_id, sync_id, status, payload, updated
          FROM account_sync_status
          WHERE account_id = $accountId
          """
      .query[SyncEvent]
      .option

  def getSyncEvents(
      accountId: UUID,
      sort: Sort,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ): Stream[ConnectionIO, SyncEvent] = {
    val orderF  = Fragment.const(s"ORDER BY updated $sort")
    val limitF  = limit.map(l => fr"LIMIT $l").getOrElse(Fragment.empty)
    val offsetF = offset.map(o => fr"OFFSET $o").getOrElse(Fragment.empty)

    val query = sql"""SELECT account_id, sync_id, status, payload, updated
          FROM account_sync_event
          WHERE account_id = $accountId
          """ ++ orderF ++ limitF ++ offsetF

    query
      .query[SyncEvent]
      .stream
  }

  def countSyncEvents(accountId: UUID): ConnectionIO[Int] =
    sql"""SELECT COUNT(*) FROM account_sync_event WHERE account_id = $accountId"""
      .query[Int]
      .unique
}
