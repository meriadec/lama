package co.ledger.lama.service.utils

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.service.{Operation, OutputView}
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.BitcoinNetwork.{MainNet, RegTest, TestNet3, Unspecified}
import co.ledger.lama.common.models.Scheme.{Bip44, Bip49, Bip84}
import co.ledger.lama.common.models.{Coin, CoinFamily}
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.lama.manager.protobuf.{
  AccountInfoRequest,
  AccountInfoResult,
  RegisterAccountResult
}
import co.ledger.lama.service.models.{
  AccountRegistered,
  GetAccountManagerInfoResult,
  GetOperationsResult,
  GetUTXOsResult
}
import co.ledger.lama.service.routes.AccountController.CreationRequest
import co.ledger.protobuf.bitcoin.{BitcoinNetwork, CreateKeychainRequest, Scheme}

object ProtobufUtils {
  def toAccountInfoRequest(accountId: UUID): AccountInfoRequest =
    new AccountInfoRequest(UuidUtils.uuidToBytes(accountId))

  def fromAccountInfoResult(accountInfoResult: AccountInfoResult): GetAccountManagerInfoResult = {
    val accountId = UuidUtils.bytesToUuid(accountInfoResult.accountId).get

    GetAccountManagerInfoResult(
      accountId = accountId,
      keychainId = accountInfoResult.key,
      syncFrequency = accountInfoResult.syncFrequency,
      status = accountInfoResult.lastSyncEvent.map(_.status)
    )
  }

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

  def fromRegisterAccount(ra: RegisterAccountResult): AccountRegistered =
    AccountRegistered(
      accountId = UuidUtils.bytesToUuid(ra.accountId).get,
      syncId = UuidUtils.bytesToUuid(ra.syncId).get,
      syncFrequency = ra.syncFrequency
    )

  def fromOperationListingInfos(txs: protobuf.GetOperationsResult): GetOperationsResult =
    GetOperationsResult(
      truncated = txs.truncated,
      operations = txs.operations.map(Operation.fromProto),
      size = txs.operations.size
    )

  def fromUtxosListingInfo(txs: protobuf.GetUTXOsResult): GetUTXOsResult =
    GetUTXOsResult(
      truncated = txs.truncated,
      utxos = txs.utxos.map(OutputView.fromProto),
      size = txs.utxos.size
    )

}
