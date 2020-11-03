package co.ledger.lama.service.utils

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.service.{Operation, OutputView}
import co.ledger.lama.bitcoin.interpreter.{protobuf => pbInterpreter}
import co.ledger.lama.common.models.BitcoinNetwork.{MainNet, RegTest, TestNet3, Unspecified}
import co.ledger.lama.common.models.Scheme.{Bip44, Bip49, Bip84}
import co.ledger.lama.common.models.{Coin, CoinFamily, Status, SyncEvent}
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.manager.{protobuf => pbManager}
import co.ledger.lama.service.models.{
  AccountInfo,
  AccountRegistered,
  GetOperationsResult,
  GetUTXOsResult
}
import co.ledger.lama.service.routes.AccountController.CreationRequest
import co.ledger.protobuf.bitcoin.{BitcoinNetwork, CreateKeychainRequest, Scheme}
import io.circe.parser.parse

object ProtobufUtils {
  def toAccountInfoRequest(accountId: UUID): pbManager.AccountInfoRequest =
    new pbManager.AccountInfoRequest(UuidUtils.uuidToBytes(accountId))

  def toScheme(s: co.ledger.lama.common.models.Scheme): Scheme.Recognized =
    s match {
      case Bip44 => Scheme.SCHEME_BIP44
      case Bip49 => Scheme.SCHEME_BIP49
      case Bip84 => Scheme.SCHEME_BIP84
    }

  def toBitcoinNetwork(network: co.ledger.lama.common.models.BitcoinNetwork): BitcoinNetwork =
    network match {
      case MainNet     => BitcoinNetwork.BITCOIN_NETWORK_MAINNET
      case TestNet3    => BitcoinNetwork.BITCOIN_NETWORK_TESTNET3
      case RegTest     => BitcoinNetwork.BITCOIN_NETWORK_REGTEST
      case Unspecified => BitcoinNetwork.BITCOIN_NETWORK_UNSPECIFIED
    }

  def toCreateKeychainRequest(cr: CreationRequest): CreateKeychainRequest =
    new CreateKeychainRequest(
      extendedPublicKey = cr.extendedPublicKey,
      scheme = toScheme(cr.scheme),
      lookaheadSize = cr.lookaheadSize,
      network = toBitcoinNetwork(cr.network)
    )

  def toCoinFamily(cf: CoinFamily): co.ledger.lama.manager.protobuf.CoinFamily =
    cf match {
      case CoinFamily.Bitcoin => co.ledger.lama.manager.protobuf.CoinFamily.bitcoin
      case _                  => co.ledger.lama.manager.protobuf.CoinFamily.Unrecognized(-1)
    }

  def toCoin(c: Coin): co.ledger.lama.manager.protobuf.Coin =
    c match {
      case Coin.Btc => co.ledger.lama.manager.protobuf.Coin.btc
      case _        => co.ledger.lama.manager.protobuf.Coin.Unrecognized(-1)
    }

  def fromRegisterAccount(ra: pbManager.RegisterAccountResult): AccountRegistered =
    AccountRegistered(
      accountId = UuidUtils.bytesToUuid(ra.accountId).get,
      syncId = UuidUtils.bytesToUuid(ra.syncId).get,
      syncFrequency = ra.syncFrequency
    )

  def fromOperationListingInfos(txs: pbInterpreter.GetOperationsResult): GetOperationsResult =
    GetOperationsResult(
      truncated = txs.truncated,
      operations = txs.operations.map(Operation.fromProto),
      size = txs.operations.size
    )

  def fromUtxosListingInfo(txs: pbInterpreter.GetUTXOsResult): GetUTXOsResult =
    GetUTXOsResult(
      truncated = txs.truncated,
      utxos = txs.utxos.map(OutputView.fromProto),
      size = txs.utxos.size
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
      balance: pbInterpreter.BalanceHistory
  ): AccountInfo = {
    val accountId = UuidUtils.bytesToUuid(info.accountId).get
    AccountInfo(
      accountId,
      info.syncFrequency,
      info.lastSyncEvent.flatMap(fromSyncEvent(accountId, _)),
      BigInt(balance.balance),
      balance.utxos,
      BigInt(balance.received),
      BigInt(balance.sent)
    )
  }

}
