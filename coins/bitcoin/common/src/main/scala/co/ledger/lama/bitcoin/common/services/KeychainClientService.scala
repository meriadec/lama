package co.ledger.lama.bitcoin.common.services

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.worker.KeychainServiceError
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
    client
      .createKeychain(
        CreateKeychainRequest(
          extendedPublicKey,
          scheme.toProto,
          lookaheadSize,
          network.toProto
        ),
        new Metadata
      )
      .handleErrorWith(err =>
        IO.raiseError(
          KeychainServiceError(
            thr = err,
            errorMessage = s"Failed to create keychain for this expub $extendedPublicKey"
          )
        )
      )

  def getKeychainInfo(keychainId: UUID): IO[KeychainInfo] =
    client
      .getKeychainInfo(
        GetKeychainInfoRequest(UuidUtils.uuidToBytes(keychainId)),
        new Metadata
      )
      .handleErrorWith(err =>
        IO.raiseError(
          KeychainServiceError(
            thr = err,
            errorMessage = s"Failed to get keychain informations for this keychain $keychainId"
          )
        )
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
      .handleErrorWith(err =>
        IO.raiseError(
          KeychainServiceError(
            thr = err,
            errorMessage = s"Failed to get addresses for this keychain $keychainId"
          )
        )
      )

  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit] =
    client
      .markAddressesAsUsed(
        MarkAddressesAsUsedRequest(UuidUtils.uuidToBytes(keychainId), addresses),
        new Metadata
      )
      .void
      .handleErrorWith(err =>
        IO.raiseError(
          KeychainServiceError(
            thr = err,
            errorMessage = s"Failed to to mark addresses as used for this keychain $keychainId"
          )
        )
      )
}
