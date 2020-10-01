package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.worker.services.KeychainService
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.protobuf.bitcoin.{AddressInfo, BitcoinNetwork, KeychainInfo, Scheme}

import scala.collection.mutable

class KeychainServiceMock extends KeychainService {

  var usedAddresses: mutable.Seq[String] = mutable.Seq.empty

  private val derivedAddresses: Seq[AddressInfo] = Seq(
    AddressInfo("1MZbRqZGpiSWGRLg8DUdVrDKHwNe1oesUZ"),
    AddressInfo("1LD1pARePgXXyZA1J3EyvRtB82vxENs5wQ"),
    AddressInfo("1MfeDvj5AUBG4xVMrx1xPgmYdXQrzHtW5b"),
    AddressInfo("1GgX4cGLiqF9p4Sd1XcPQhEAAhNDA4wLYS"),
    AddressInfo("1Q2Bv9X4yCTNn1P1tmFuWpijHvT3xYt3F"),
    AddressInfo("1G7g5zxfjWCSJRuNKVasVczrZNowQRwbij"),
    AddressInfo("1MFjwXsibXbvVzkE4chJrhbczDivpbbVTE"),
    AddressInfo("1HFzpigeFDZGp45peU4NAHLgyMxiGj1GzT"),
    AddressInfo("17xsjFyLgbWrjauC8F5hyaaaWdf6L6Y6L4"),
    AddressInfo("1Hc7EofusKsUrNPhbp1PUMkH6wfDohfDBd"),
    AddressInfo("1Mj9jzHtAyVvM9Y274LCcfLBBBfwRiDK9V"),
    AddressInfo("1Ng5FPQ1rUbEHak8Qcjy6BRJhjF1n3AVR6"),
    AddressInfo("145Tdk8ntZQa5kgyLheL835z6yukHjbEKF"),
    AddressInfo("16hG8pC6D4gRmRvfHT3zHGcED9FMocN4hG"),
    AddressInfo("1NQd72r3kUESTAMvDjaJU1Gk842HPcPVQQ"),
    AddressInfo("1JiBkCdhc3P4by29kLzraz4CuwjAvTA96H"),
    AddressInfo("1MXLmPcLRoQAWZqfgxtvhvUWLDQ3We2sUJ"),
    AddressInfo("1DRCwCw8HjeRsRi4wyfJzqgBeNBJTdvvx1"),
    AddressInfo("1NTG6NWQq1DZYZf8VQ58FBGGDwA9deM7Aq"),
    AddressInfo("1JMbu32pdVu6FvKbmrJMTSJSWFcJJ47JtY"),
    AddressInfo("13ZLzktrPVDGjaoPpqvWrxhXko7UAXFJHQ"),
    AddressInfo("19rpjEgDaPUwkeyuD7JHKUkTyxFHAmnorm"),
    AddressInfo("1D2R9GQu541rmUKY5kz6gjWuX2kfEusRre"),
    AddressInfo("1B3g4WxFBJtPh6azgQdRs5f7zwXhcocELc"),
    AddressInfo("12AdRB44ctyTaQiLgthz7WMFJ7dFNornmA"),
    AddressInfo("1KHyosZPVXxVBaQ7qtRjPUWWt911rAkfg6"),
    AddressInfo("1KConohwqXnB87BYpp2n7GfrPRhPqa471a"),
    AddressInfo("1BGCPcrzx3G48eY7vhpc7UEtJbpXW3mZ1t"),
    AddressInfo("14er8aopUkpX4KcL9rx7GU2t8zbFANQyC3"),
    AddressInfo("1LPR9mGFJrWkiMPj2HWfnBA5weEeKV2arY"),
    AddressInfo("15M1GcHsakzQtxkVDcw92siMk3c3Ap3C5h"),
    AddressInfo("1GWfouhfoTHctEeUCMd1tcF2cdkfuaSXdh"),
    AddressInfo("1CyAcL6Kd5pWzFucQE2Ev527FEQ9dTtPJ1"),
    AddressInfo("1AxhDoozM9VfsktCKVN7kp6UkaqVq65rHF"),
    AddressInfo("1Aj3Gi1j5UsvZh4ccjaqdnogPMWy54Z5ii")
  ) ++ (1 to 20).map(i => AddressInfo(s"unused$i"))

  def create(extendedPublicKey: String, scheme: Scheme, network: BitcoinNetwork): IO[KeychainInfo] =
    IO.delay(KeychainInfo(keychainId = UuidUtils.uuidToBytes(UUID.randomUUID())))

  def getAddresses(keychainId: UUID, fromIndex: Int, toIndex: Int): IO[Seq[AddressInfo]] =
    IO.delay(derivedAddresses.slice(fromIndex, toIndex))

  def markAddressesAsUsed(keychainId: UUID, addresses: Seq[String]): IO[Unit] =
    IO.delay {
      usedAddresses = usedAddresses ++ addresses
    }

}
