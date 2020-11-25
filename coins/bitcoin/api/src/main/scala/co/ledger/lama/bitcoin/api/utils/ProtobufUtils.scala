package co.ledger.lama.bitcoin.api.utils

import java.util.UUID

import co.ledger.lama.common.models.{Coin, CoinFamily, Status, SyncEvent}
import co.ledger.lama.common.utils.UuidUtils
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

  def toCoinFamily(cf: CoinFamily): co.ledger.lama.manager.protobuf.CoinFamily =
    cf match {
      case CoinFamily.Bitcoin => co.ledger.lama.manager.protobuf.CoinFamily.bitcoin
      case _                  => co.ledger.lama.manager.protobuf.CoinFamily.Unrecognized(-1)
    }

  def toCoin(c: Coin): co.ledger.lama.manager.protobuf.Coin =
    c match {
      case Coin.Btc        => co.ledger.lama.manager.protobuf.Coin.btc
      case Coin.BtcTestnet => co.ledger.lama.manager.protobuf.Coin.btc_testnet
      case _               => co.ledger.lama.manager.protobuf.Coin.Unrecognized(-1)
    }

  val fromCoin: PartialFunction[co.ledger.lama.manager.protobuf.Coin, Coin] = {
    case co.ledger.lama.manager.protobuf.Coin.btc         => Coin.Btc
    case co.ledger.lama.manager.protobuf.Coin.btc_testnet => Coin.BtcTestnet
  }

  val fromCoinFamily: PartialFunction[co.ledger.lama.manager.protobuf.CoinFamily, CoinFamily] = {
    case co.ledger.lama.manager.protobuf.CoinFamily.bitcoin => CoinFamily.Bitcoin
  }

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
      info.syncFrequency,
      info.lastSyncEvent.flatMap(fromSyncEvent(accountId, _)),
      balance.balance,
      balance.utxos,
      balance.received,
      balance.sent
    )
  }

}
