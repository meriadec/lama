package co.ledger.lama.bitcoin.transactor.services

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.BitcoinNetwork
import co.ledger.lama.bitcoin.common.models.interpreter.Utxo
import co.ledger.lama.bitcoin.common.models.transactor.{CreateTransactionResponse, PrepareTxOutput}
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib._
import co.ledger.lama.bitcoin.transactor.models.implicits._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.protobuf.bitcoin.libgrpc
import com.google.protobuf.ByteString
import io.grpc.Metadata

trait BitcoinLibGrpcService {
  def createTransaction(
      selectedUtxos: List[Utxo],
      outputs: List[PrepareTxOutput],
      changeAddress: String,
      feeSatPerKb: Long,
      lockTime: Long
  ): IO[RawTransactionResponse]

  def generateSignatures(
      rawTransaction: CreateTransactionResponse,
      privkey: String
  ): IO[Seq[ByteString]]

  def signTransaction(
      rawTransaction: CreateTransactionResponse,
      network: BitcoinNetwork,
      signatures: List[SignatureMetadata]
  ): IO[RawTransactionResponse]
}

class BitcoinLibGrpcClientService(grpcClient: libgrpc.CoinServiceFs2Grpc[IO, Metadata])
    extends BitcoinLibGrpcService
    with IOLogging {

  def createTransaction(
      selectedUtxos: List[Utxo],
      outputs: List[PrepareTxOutput],
      changeAddress: String,
      feeSatPerKb: Long,
      lockTime: Long = 0L
  ): IO[RawTransactionResponse] =
    grpcClient
      .createTransaction(
        CreateTransactionRequest(
          lockTime.toInt,
          selectedUtxos.map(utxosToInputs),
          outputs.map(prepareTxOutput =>
            Output(
              prepareTxOutput.address,
              prepareTxOutput.value
            )
          ),
          BitcoinNetwork.TestNet3,
          changeAddress,
          feeSatPerKb
        ).toProto,
        new Metadata
      )
      .map(RawTransactionResponse.fromProto)

  def generateSignatures(
      rawTransaction: CreateTransactionResponse,
      privkey: String
  ): IO[Seq[ByteString]] =
    grpcClient
      .generateDerSignatures(
        libgrpc.GenerateDerSignaturesRequest(
          Some(
            libgrpc.RawTransactionResponse(
              rawTransaction.hex,
              rawTransaction.hash,
              rawTransaction.witnessHash,
              None
            )
          ),
          rawTransaction.utxos
            .map(utxo =>
              libgrpc.Utxo(
                utxo.scriptHex,
                utxo.value.toString,
                utxo.derivation.toList
              )
            )
            .toList,
          privkey
        ),
        new Metadata
      )
      .map(_.derSignatures)

  def signTransaction(
      rawTransaction: CreateTransactionResponse,
      network: BitcoinNetwork,
      signatures: List[SignatureMetadata]
  ): IO[RawTransactionResponse] =
    grpcClient
      .signTransaction(
        libgrpc.SignTransactionRequest(
          Some(
            libgrpc.RawTransactionResponse(
              rawTransaction.hex,
              rawTransaction.hash,
              rawTransaction.witnessHash,
              None
            )
          ),
          network.toLibGrpcProto,
          signatures.map(_.toProto)
        ),
        new Metadata
      )
      .map(RawTransactionResponse.fromProto)
      .handleErrorWith { e =>
        e.printStackTrace()
        IO.raiseError(e)
      }

  private def utxosToInputs(utxo: Utxo): Input = {
    Input(
      utxo.transactionHash,
      utxo.outputIndex,
      utxo.scriptHex,
      utxo.value
    )
  }

}
