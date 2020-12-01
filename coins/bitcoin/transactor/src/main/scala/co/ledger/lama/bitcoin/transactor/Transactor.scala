package co.ledger.lama.bitcoin.transactor

import java.util.UUID

import cats.effect.{ConcurrentEffect, IO}
import fs2.{Chunk, Stream}
import co.ledger.lama.bitcoin.transactor.services.{BitcoinLibGrpcService, CoinSelectionService}
import co.ledger.lama.bitcoin.common.models.BitcoinNetwork
import co.ledger.lama.bitcoin.common.models.interpreter.{ChangeType, Utxo}
import co.ledger.lama.bitcoin.common.models.transactor.{CoinSelectionStrategy, PrepareTxOutput}
import co.ledger.lama.bitcoin.common.services.{
  ExplorerClientService,
  InterpreterClientService,
  KeychainClientService
}
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib
import co.ledger.lama.bitcoin.transactor.protobuf
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.Sort
import co.ledger.lama.common.utils.UuidUtils
import io.grpc.{Metadata, ServerServiceDefinition}

trait Transactor extends protobuf.BitcoinTransactorServiceFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    protobuf.BitcoinTransactorServiceFs2Grpc.bindService(this)
}

class BitcoinLibTransactor(
    bitcoinLibClient: BitcoinLibGrpcService,
    explorerClient: ExplorerClientService,
    keychainClient: KeychainClientService,
    interpreterClient: InterpreterClientService
) extends Transactor
    with IOLogging {

  def createTransaction(
      request: protobuf.CreateTransactionRequest,
      ctx: io.grpc.Metadata
  ): IO[protobuf.CreateTransactionResponse] = {

    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      _ <- log.info(
        s"""Preparing transaction :
            - accountId : $accountId
            - strategy : ${request.coinSelection.name}
            - outputs : ${request.outputs.mkString("[\n", ",\n", "]")}
         """
      )

      utxos <- getUTXOs(accountId, 100, Sort.Ascending).compile.toList
      _ <- log.info(
        s"""Utxos found for account $accountId :
            - number of utxos : ${utxos.size}
            - sum : ${utxos.map(_.value).sum}
         """
      )

      outputs = request.outputs.map(PrepareTxOutput.fromProto(_)).toList
      pickedUtxos <- CoinSelectionService.pickUtxos(
        CoinSelectionStrategy.fromProto(request.coinSelection),
        utxos,
        outputs.map(_.value).sum
      )
      _ <- log.info(
        s"""Picked Utxos :
            - number of utxos : ${pickedUtxos.size}
            - sum : ${pickedUtxos.map(_.value).sum}
         """
      )

      _ <- validateTransaction(pickedUtxos, outputs)

      estimatedFeeSatPerKb <- explorerClient.getSmartFees.map(_.normal)
      keychainId           <- UuidUtils.bytesToUuidIO(request.keychainId)
      changeAddress <- keychainClient
        .getFreshAddresses(keychainId, ChangeType.Internal, 1)
        .flatMap { addresses =>
          IO.fromOption(addresses.headOption)(
            new NoSuchElementException(
              s"Could not get fresh change address from keychain with id : $keychainId"
            )
          )
        }

      unsignedTx <- IO(createTransactionRequest(pickedUtxos, outputs, 0L))
      preparedTransaction <- bitcoinLibClient.createTransaction(
        unsignedTx,
        changeAddress,
        estimatedFeeSatPerKb
      )
    } yield {

      new protobuf.CreateTransactionResponse(
        preparedTransaction.hex,
        preparedTransaction.hash,
        preparedTransaction.witnessHash,
        pickedUtxos.map(_.toProto)
      )

    }

  }

  private def getUTXOs(accountId: UUID, limit: Int, sort: Sort): Stream[IO, Utxo] = {
    def getUtxosRec(accountId: UUID, offset: Int, limit: Int, sort: Sort): Stream[IO, Utxo] = {
      Stream
        .eval(interpreterClient.getUTXOs(accountId, offset, limit + offset, Some(sort)))
        .flatMap { result =>
          val head = Stream.chunk(Chunk.seq(result.utxos)).covary[IO]

          val tail =
            if (result.truncated)
              getUtxosRec(accountId, offset + limit, limit, sort)
            else
              Stream.empty

          head ++ tail
        }
    }

    getUtxosRec(accountId, 0, limit, sort)
  }

  private def validateTransaction(utxos: List[Utxo], recipients: List[PrepareTxOutput]): IO[Unit] =
    if (utxos.map(_.value).sum < recipients.map(_.value).sum)
      IO.raiseError(new Exception("Not enough coins in Utxos to cover for coins sent."))
    else
      IO.unit

  private def createTransactionRequest(
      utxos: List[Utxo],
      recipients: List[PrepareTxOutput],
      lockTime: Long
  ): bitcoinLib.CreateTransactionRequest = {
    bitcoinLib.CreateTransactionRequest(
      lockTime.toInt,
      utxos.map(utxosToInputs),
      recipients.map(prepareTxOutput =>
        bitcoinLib.Output(
          prepareTxOutput.address,
          prepareTxOutput.value
        )
      ),
      BitcoinNetwork.MainNet
    )
  }

  private def utxosToInputs(utxo: Utxo): bitcoinLib.Input = {
    bitcoinLib.Input(
      utxo.transactionHash,
      utxo.outputIndex
    )
  }

}
