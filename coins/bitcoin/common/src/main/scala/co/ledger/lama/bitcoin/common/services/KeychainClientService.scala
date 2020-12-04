package co.ledger.lama.bitcoin.common.services

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.interpreter.ChangeType
import co.ledger.lama.bitcoin.common.models.{BitcoinNetwork, Scheme}
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain._
import io.grpc._

trait KeychainClientService {
  def create(
      extendedPublicKey: String,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinNetwork
  ): IO[KeychainInfo]
  def getKeychainInfo(keychainId: UUID): IO[KeychainInfo]
  def getAddresses(keychainId: UUID, fromIndex: Int, toIndex: Int): IO[Seq[AddressInfo]]
  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit]
  def getFreshAddresses(keychainId: UUID, change: ChangeType, size: Int): IO[Seq[AddressInfo]]
  def getAddressesPublicKeys(
      keychainId: UUID,
      derivations: NonEmptyList[NonEmptyList[Int]]
  ): IO[List[String]]
}

class KeychainGrpcClientService(
    client: KeychainServiceFs2Grpc[IO, Metadata]
) extends KeychainClientService {

  def create(
      extendedPublicKey: String,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinNetwork
  ): IO[KeychainInfo] =
    client.createKeychain(
      CreateKeychainRequest(
        extendedPublicKey,
        scheme.toProto,
        lookaheadSize,
        network.toKeychainProto
      ),
      new Metadata
    )

  def getKeychainInfo(keychainId: UUID): IO[KeychainInfo] =
    client.getKeychainInfo(
      GetKeychainInfoRequest(UuidUtils.uuidToBytes(keychainId)),
      new Metadata
    )

  def getAddresses(keychainId: UUID, fromIndex: Int, toIndex: Int): IO[Seq[AddressInfo]] =
    client
      .getAllObservableAddresses(
        GetAllObservableAddressesRequest(
          keychainId = UuidUtils.uuidToBytes(keychainId),
          fromIndex = fromIndex,
          toIndex = toIndex
        ),
        new Metadata
      )
      .map(_.addresses)

  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit] =
    client
      .markAddressesAsUsed(
        MarkAddressesAsUsedRequest(UuidUtils.uuidToBytes(keychainId), addresses),
        new Metadata
      )
      .void

  def getFreshAddresses(keychainId: UUID, change: ChangeType, size: Int): IO[Seq[AddressInfo]] =
    client
      .getFreshAddresses(
        GetFreshAddressesRequest(
          UuidUtils.uuidToBytes(keychainId),
          change.toKeychainProto,
          size
        ),
        new Metadata
      )
      .map(_.addresses)

  def getAddressesPublicKeys(
      keychainId: UUID,
      derivations: NonEmptyList[NonEmptyList[Int]]
  ): IO[List[String]] =
    client
      .getAddressesPublicKeys(
        GetAddressesPublicKeysRequest(
          UuidUtils.uuidToBytes(keychainId),
          derivations
            .map(derivation =>
              DerivationPath(
                derivation.toList
              )
            )
            .toList
        ),
        new Metadata
      )
      .map(_.publicKeys.toList)

}
