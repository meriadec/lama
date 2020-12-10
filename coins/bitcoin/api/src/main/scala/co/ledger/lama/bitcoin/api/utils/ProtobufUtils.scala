package co.ledger.lama.bitcoin.api.utils

import java.util.UUID

import co.ledger.lama.common.models.Sort
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.common.utils.{ProtobufUtils => CommonProtobufUtils}
import co.ledger.lama.manager.{protobuf => pbManager}
import co.ledger.lama.bitcoin.api.models.accountManager._
import co.ledger.lama.bitcoin.common.models.interpreter.BalanceHistory
import co.ledger.lama.bitcoin.common.models.worker.Block
import co.ledger.protobuf.bitcoin.keychain

object ProtobufUtils {
  def toAccountInfoRequest(accountId: UUID): pbManager.AccountInfoRequest =
    pbManager.AccountInfoRequest(UuidUtils.uuidToBytes(accountId))

  def toGetSyncEventsRequest(
      accountId: UUID,
      limit: Option[Int],
      offset: Option[Int],
      sort: Option[Sort]
  ): pbManager.GetSyncEventsRequest =
    pbManager.GetSyncEventsRequest(
      accountId = UuidUtils.uuidToBytes(accountId),
      limit = limit.getOrElse(20),
      offset = offset.getOrElse(0),
      sort = sort.getOrElse(Sort.Descending) match {
        case Sort.Ascending  => pbManager.SortingOrder.ASC
        case Sort.Descending => pbManager.SortingOrder.DESC
      }
    )

  def toCreateKeychainRequest(cr: CreationRequest): keychain.CreateKeychainRequest =
    new keychain.CreateKeychainRequest(
      extendedPublicKey = cr.extendedPublicKey,
      scheme = cr.scheme.toProto,
      lookaheadSize = cr.lookaheadSize,
      network = cr.network.toKeychainProto
    )

  def fromRegisterAccount(ra: pbManager.RegisterAccountResult): AccountRegistered =
    AccountRegistered(
      accountId = UuidUtils.bytesToUuid(ra.accountId).get,
      syncId = UuidUtils.bytesToUuid(ra.syncId).get,
      syncFrequency = ra.syncFrequency
    )

  def fromAccountInfo(
      info: pbManager.AccountInfoResult,
      balance: BalanceHistory
  ): AccountInfo = {
    val accountId = UuidUtils.bytesToUuid(info.accountId).get
    AccountInfo(
      accountId,
      CommonProtobufUtils.fromCoinFamily(info.coinFamily),
      CommonProtobufUtils.fromCoin(info.coin),
      info.syncFrequency,
      info.lastSyncEvent.map(CommonProtobufUtils.fromSyncEvent[Block]),
      balance.balance,
      balance.utxos,
      balance.received,
      balance.sent,
      info.label.map(_.value)
    )
  }

}
