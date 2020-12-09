package co.ledger.lama.bitcoin.transactor.services

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.{BitcoinNetwork, interpreter, transactor}
import co.ledger.lama.bitcoin.transactor.grpc.BitcoinLibGrpcService
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib
import com.google.protobuf.ByteString

class BitcoinLibClientServiceMock extends BitcoinLibGrpcService {

  def createTransaction(
      selectedUtxos: List[interpreter.Utxo],
      outputs: List[transactor.PrepareTxOutput],
      changeAddress: String,
      feeSatPerKb: Long,
      lockTime: Long
  ): IO[bitcoinLib.RawTransactionResponse] = IO(
    bitcoinLib.RawTransactionResponse(
      "hex",
      "hash",
      "witnessHash",
      None
    )
  )
  override def generateSignatures(
      rawTransaction: transactor.CreateTransactionResponse,
      privkey: String
  ): IO[Seq[ByteString]] = ???

  override def signTransaction(
      rawTransaction: transactor.CreateTransactionResponse,
      network: BitcoinNetwork,
      signatures: List[bitcoinLib.SignatureMetadata]
  ): IO[bitcoinLib.RawTransactionResponse] = ???
}
