package co.ledger.lama.bitcoin.common.services.mocks

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.interpreter.ChangeType
import co.ledger.lama.bitcoin.common.models.{BitcoinNetwork, Scheme}
import co.ledger.lama.bitcoin.common.services.KeychainClientService
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain

import scala.collection.mutable

class KeychainClientServiceMock extends KeychainClientService with IOLogging {

  var usedAddresses: mutable.Seq[String] = mutable.Seq.empty

  private val derivedAddresses: Seq[keychain.AddressInfo] = Seq(
    keychain.AddressInfo("1MZbRqZGpiSWGRLg8DUdVrDKHwNe1oesUZ", List(1, 0)),
    keychain.AddressInfo("1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ", List(1, 0)),
    keychain.AddressInfo("1MfeDvj5AUBG4xVMrx1xPgmYdXQrzHtW5b", List(1, 0)),
    keychain.AddressInfo("1GgX4cGLiqF9p4Sd1XcPQhEAAhNDA4wLYS", List(1, 0)),
    keychain.AddressInfo("1Q2Bv9X4yCTNn1P1tmFuWpijHvT3xYt3F", List(1, 0)),
    keychain.AddressInfo("1G7g5zxfjWCSJRuNKVasVczrZNowQRwbij", List(1, 0)),
    keychain.AddressInfo("1MFjwXsibXbvVzkE4chJrhbczDivpbbVTE", List(1, 0)),
    keychain.AddressInfo("1HFzpigeFDZGp45peU4NAHLgyMxiGj1GzT", List(1, 0)),
    keychain.AddressInfo("17xsjFyLgbWrjauC8F5hyaaaWdf6L6Y6L4", List(1, 0)),
    keychain.AddressInfo("1Hc7EofusKsUrNPhbp1PUMkH6wfDohfDBd", List(1, 0)),
    keychain.AddressInfo("1Mj9jzHtAyVvM9Y274LCcfLBBBfwRiDK9V", List(1, 0)),
    keychain.AddressInfo("1Ng5FPQ1rUbEHak8Qcjy6BRJhjF1n3AVR6", List(1, 0)),
    keychain.AddressInfo("145Tdk8ntZQa5kgyLheL835z6yukHjbEKF", List(1, 0)),
    keychain.AddressInfo("16hG8pC6D4gRmRvfHT3zHGcED9FMocN4hG", List(1, 0)),
    keychain.AddressInfo("1NQd72r3kUESTAMvDjaJU1Gk842HPcPVQQ", List(1, 0)),
    keychain.AddressInfo("1JiBkCdhc3P4by29kLzraz4CuwjAvTA96H", List(1, 0)),
    keychain.AddressInfo("1MXLmPcLRoQAWZqfgxtvhvUWLDQ3We2sUJ", List(1, 0)),
    keychain.AddressInfo("1DRCwCw8HjeRsRi4wyfJzqgBeNBJTdvvx1", List(1, 0)),
    keychain.AddressInfo("1NTG6NWQq1DZYZf8VQ58FBGGDwA9deM7Aq", List(1, 0)),
    keychain.AddressInfo("1JMbu32pdVu6FvKbmrJMTSJSWFcJJ47JtY", List(1, 0)),
    keychain.AddressInfo("13ZLzktrPVDGjaoPpqvWrxhXko7UAXFJHQ", List(1, 0)),
    keychain.AddressInfo("19rpjEgDaPUwkeyuD7JHKUkTyxFHAmnorm", List(1, 0)),
    keychain.AddressInfo("1D2R9GQu541rmUKY5kz6gjWuX2kfEusRre", List(1, 0)),
    keychain.AddressInfo("1B3g4WxFBJtPh6azgQdRs5f7zwXhcocELc", List(1, 0)),
    keychain.AddressInfo("12AdRB44ctyTaQiLgthz7WMFJ7dFNornmA", List(1, 0)),
    keychain.AddressInfo("1KHyosZPVXxVBaQ7qtRjPUWWt911rAkfg6", List(1, 0)),
    keychain.AddressInfo("1KConohwqXnB87BYpp2n7GfrPRhPqa471a", List(1, 0)),
    keychain.AddressInfo("1BGCPcrzx3G48eY7vhpc7UEtJbpXW3mZ1t", List(1, 0)),
    keychain.AddressInfo("14er8aopUkpX4KcL9rx7GU2t8zbFANQyC3", List(1, 0)),
    keychain.AddressInfo("1LPR9mGFJrWkiMPj2HWfnBA5weEeKV2arY", List(1, 0)),
    keychain.AddressInfo("15M1GcHsakzQtxkVDcw92siMk3c3Ap3C5h", List(1, 0)),
    keychain.AddressInfo("1GWfouhfoTHctEeUCMd1tcF2cdkfuaSXdh", List(1, 0)),
    keychain.AddressInfo("1CyAcL6Kd5pWzFucQE2Ev527FEQ9dTtPJ1", List(1, 0)),
    keychain.AddressInfo("1AxhDoozM9VfsktCKVN7kp6UkaqVq65rHF", List(1, 0)),
    keychain.AddressInfo("1Aj3Gi1j5UsvZh4ccjaqdnogPMWy54Z5ii", List(1, 0))
  ) ++ (1 to 20).map(i => keychain.AddressInfo(s"unused$i", List(1, 0)))

  def create(
      extendedPublicKey: String,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinNetwork
  ): IO[keychain.KeychainInfo] =
    IO.delay(keychain.KeychainInfo(keychainId = UuidUtils.uuidToBytes(UUID.randomUUID())))

  def getKeychainInfo(keychainId: UUID): IO[keychain.KeychainInfo] =
    IO.pure(keychain.KeychainInfo(lookaheadSize = 20))

  def getAddresses(keychainId: UUID, fromIndex: Int, toIndex: Int): IO[Seq[keychain.AddressInfo]] =
    IO.delay(derivedAddresses.slice(fromIndex, toIndex))

  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit] =
    IO.delay {
      usedAddresses = usedAddresses ++ addresses
    }

  def getFreshAddresses(keychainId: UUID, change: ChangeType, size: Int): IO[List[String]] =
    IO(List("1MZbRqZGpiSWGRLg8DUdVrDKHwNe1oesUZ"))

}
