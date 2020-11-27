package co.ledger.lama.bitcoin.worker.faultymocks

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.worker.KeychainServiceError
import co.ledger.lama.bitcoin.common.models.{BitcoinNetwork, Scheme}
import co.ledger.lama.bitcoin.common.services.KeychainClientService
import co.ledger.protobuf.bitcoin.keychain
import co.ledger.protobuf.bitcoin.keychain.AddressInfo

class FaultyKeychainClientServiceMock extends KeychainClientService with FaultyBase {

  def create(
      extendedPublicKey: String,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinNetwork
  ): IO[keychain.KeychainInfo] =
    IO.raiseError(
      KeychainServiceError(
        cause = err,
        errorMessage = s"Failed to create keychain for this expub $extendedPublicKey"
      )
    )

  def getKeychainInfo(keychainId: UUID): IO[keychain.KeychainInfo] =
    IO.raiseError(
      KeychainServiceError(
        cause = err,
        errorMessage = s"Failed to get keychain informations for this keychain $keychainId"
      )
    )

  def getAddresses(keychainId: UUID, fromIndex: Int, toIndex: Int): IO[Seq[AddressInfo]] =
    IO.raiseError(
      KeychainServiceError(
        cause = err,
        errorMessage = s"Failed to get addresses for this keychain $keychainId"
      )
    )

  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit] =
    IO.raiseError(
      KeychainServiceError(
        cause = err,
        errorMessage = s"Failed to to mark addresses as used for this keychain $keychainId"
      )
    )

}
