package co.ledger.lama.bitcoin.worker.services

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.protobuf.bitcoin.{
  AddressInfo,
  BitcoinNetwork,
  CreateKeychainRequest,
  GetAllObservableAddressesRequest,
  KeychainInfo,
  KeychainServiceFs2Grpc,
  MarkAddressesAsUsedRequest,
  Scheme
}
import io.grpc._

trait KeychainService {
  def create(extendedPublicKey: String, scheme: Scheme, network: BitcoinNetwork): IO[KeychainInfo]
  def getAddresses(keychainId: UUID, fromIndex: Int, toIndex: Int): IO[Seq[AddressInfo]]
  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit]
}

class KeychainGrpcClientService(
    client: KeychainServiceFs2Grpc[IO, Metadata],
    lookaheadSize: Int
) extends KeychainService {

  def create(extendedPublicKey: String, scheme: Scheme, network: BitcoinNetwork): IO[KeychainInfo] =
    client.createKeychain(
      CreateKeychainRequest(
        extendedPublicKey,
        scheme,
        lookaheadSize,
        network
      ),
      new Metadata()
    )

  def getAddresses(keychainId: UUID, fromIndex: Int, toIndex: Int): IO[Seq[AddressInfo]] =
    client
      .getAllObservableAddresses(
        GetAllObservableAddressesRequest(
          keychainId = UuidUtils.uuidToBytes(keychainId),
          fromIndex = fromIndex,
          toIndex = toIndex
        ),
        new Metadata()
      )
      .map(_.addresses)

  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit] =
    client
      .markAddressesAsUsed(
        MarkAddressesAsUsedRequest(UuidUtils.uuidToBytes(keychainId), addresses),
        new Metadata()
      )
      .void
}
