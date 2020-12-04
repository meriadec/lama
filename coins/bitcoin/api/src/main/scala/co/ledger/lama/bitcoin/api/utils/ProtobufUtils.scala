package co.ledger.lama.bitcoin.api.utils

import java.util.UUID

import co.ledger.lama.common.models.{Status, SyncEvent}
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.common.utils.{ProtobufUtils => CommonProtobufUtils}
import co.ledger.lama.manager.{protobuf => pbManager}
import co.ledger.lama.bitcoin.api.models.accountManager._
import co.ledger.lama.bitcoin.common.models.interpreter.BalanceHistory
import co.ledger.protobuf.bitcoin.keychain
import io.circe.parser.parse

object ProtobufUtils {
  def toAccountInfoRequest(accountId: UUID): pbManager.AccountInfoRequest =
    new pbManager.AccountInfoRequest(UuidUtils.uuidToBytes(accountId))

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

  def fromSyncEvent(accountId: UUID, pb: pbManager.SyncEvent): Option[SyncEvent] =
    for {
      syncId  <- UuidUtils.bytesToUuid(pb.syncId)
      status  <- Status.fromKey(pb.status)
      payload <- parse(new String(pb.payload.toByteArray)).flatMap(_.as[SyncEvent.Payload]).toOption
    } yield {
      SyncEvent(
        accountId,
        syncId,
        status,
        payload
      )
    }

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
      info.lastSyncEvent.flatMap(fromSyncEvent(accountId, _)),
      balance.balance,
      balance.utxos,
      balance.received,
      balance.sent,
      info.label.map(_.value)
    )
  }

}
