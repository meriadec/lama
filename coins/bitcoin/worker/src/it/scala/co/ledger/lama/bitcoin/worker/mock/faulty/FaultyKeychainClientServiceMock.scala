package co.ledger.lama.bitcoin.worker.mock.faulty

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.{BitcoinNetwork, Scheme}
import co.ledger.lama.bitcoin.common.services.KeychainClientService
import co.ledger.protobuf.bitcoin.keychain
import co.ledger.protobuf.bitcoin.keychain.AddressInfo

import scala.collection.mutable

class FaultyKeychainClientServiceMock extends KeychainClientService {

  var usedAddresses: mutable.Seq[String] = mutable.Seq.empty

  def create(
      extendedPublicKey: String,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinNetwork
  ): IO[keychain.KeychainInfo] =
    IO.raiseError(new Exception())

  def getKeychainInfo(keychainId: UUID): IO[keychain.KeychainInfo] =
    IO.pure(keychain.KeychainInfo(lookaheadSize = 20))

  def getAddresses(keychainId: UUID, fromIndex: Int, toIndex: Int): IO[Seq[AddressInfo]] =
    IO.raiseError(new Exception)

  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit] =
    IO.delay {
      usedAddresses = usedAddresses ++ addresses
    }

}
