package co.ledger.lama.bitcoin.transactor

import java.util.UUID

import cats.data.NonEmptyList
import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.bitcoin.common.utils.CoinImplicits._
import co.ledger.lama.bitcoin.common.models.interpreter.{ChangeType, Utxo}
import co.ledger.lama.bitcoin.common.models.transactor.{
  BroadcastTransactionResponse,
  CoinSelectionStrategy,
  CreateTransactionResponse,
  PrepareTxOutput,
  RawTransactionAndUtxos
}
import co.ledger.lama.bitcoin.common.services.{
  ExplorerClientService,
  InterpreterClientService,
  KeychainClientService
}
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib.SignatureMetadata
import co.ledger.lama.bitcoin.transactor.services.{BitcoinLibGrpcService, CoinSelectionService}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{Coin, Sort}
import co.ledger.lama.common.utils.UuidUtils
import fs2.{Chunk, Stream}
import io.circe.syntax._
import io.grpc.{Metadata, ServerServiceDefinition}

trait Transactor extends protobuf.BitcoinTransactorServiceFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    protobuf.BitcoinTransactorServiceFs2Grpc.bindService(this)
}

class BitcoinLibTransactor(
    bitcoinLibClient: BitcoinLibGrpcService,
    explorerClient: Coin => ExplorerClientService,
    keychainClient: KeychainClientService,
    interpreterClient: InterpreterClientService
) extends Transactor
    with IOLogging {

  def createTransaction(
      request: protobuf.CreateTransactionRequest,
      ctx: io.grpc.Metadata
  ): IO[protobuf.CreateTransactionResponse] =
    for {
      coin <- IO.fromOption(Coin.fromKey(request.coinId))(
        new IllegalArgumentException(
          s"Unknown coin type ${request.coinId}) in CreateTransactionRequest"
        )
      )

      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      _ <- log.info(
        s"""Preparing transaction:
            - accountId: $accountId
            - strategy: ${request.coinSelection.name}
            - outputs: ${request.outputs.mkString("[\n", ",\n", "]")}
         """
      )

      utxos <- getUTXOs(accountId, 100, Sort.Ascending).compile.toList
      _ <- log.info(
        s"""Utxos found for account $accountId:
            - number of utxos: ${utxos.size}
            - sum: ${utxos.map(_.value).sum}
         """
      )

      // TODO: testnet smart fees is buggy on explorer v3
      estimatedFeeSatPerKb <-
        coin match {
          case Coin.BtcTestnet => IO.pure(25642L)
          case c               => explorerClient(c).getSmartFees.map(_.normal)
        }

      _ <- log.info(s"GetSmartFees feeLevel: Normal - feeSatPerKb: $estimatedFeeSatPerKb ")

      outputs = request.outputs.map(PrepareTxOutput.fromProto).toList

      keychainId <- UuidUtils.bytesToUuidIO(request.keychainId)
      changeAddress <- keychainClient
        .getFreshAddresses(keychainId, ChangeType.Internal, 1)
        .flatMap { addresses =>
          IO.fromOption(addresses.headOption)(
            new NoSuchElementException(
              s"Could not get fresh change address from keychain with id : $keychainId"
            )
          )
        }

      rawTransaction <- createRawTransaction(
        CoinSelectionStrategy.fromProto(request.coinSelection),
        utxos,
        outputs,
        changeAddress.address,
        estimatedFeeSatPerKb,
        outputs.map(_.value).sum
      )

    } yield {
      CreateTransactionResponse(
        rawTransaction.hex,
        rawTransaction.hash,
        rawTransaction.witnessHash,
        NonEmptyList.fromListUnsafe(rawTransaction.utxos)
      ).toProto
    }

  def broadcastTransaction(
      request: protobuf.BroadcastTransactionRequest,
      ctx: io.grpc.Metadata
  ): IO[protobuf.BroadcastTransactionResponse] =
    for {
      coin <- IO.fromOption(Coin.fromKey(request.coinId))(
        new IllegalArgumentException(
          s"Unknown coin type ${request.coinId}) in CreateTransactionRequest"
        )
      )

      keychainId <- UuidUtils.bytesToUuidIO(request.keychainId)

      rawTransaction <- IO.fromOption(
        request.rawTransaction.map(CreateTransactionResponse.fromProto)
      )(new Exception("Raw Transaction : bad format"))

      _ <- log.info(
        s"""Transaction to sign:
            - coin: ${coin.name}
            - hex: ${rawTransaction.hex}
            - tx hash: ${rawTransaction.hash}
         """
      )

      signatures <- bitcoinLibClient.generateSignatures(
        rawTransaction,
        request.privkey
      )

      _ <- log.info(s"Get ${signatures.size} signatures")

      pubKeys <- keychainClient.getAddressesPublicKeys(
        keychainId,
        rawTransaction.utxos.map(_.derivation)
      )

      _ <- log.info(s"Get pub keys $pubKeys")

      signedRawTx <- bitcoinLibClient.signTransaction(
        rawTransaction,
        coin.toNetwork,
        signatures
          .zip(pubKeys)
          .map { case (signature, pubKey) =>
            SignatureMetadata(
              signature,
              pubKey
            )
          }
          .toList
      )

      _ <- log.info(
        s"""Signed transaction:
            - coin: ${coin.name}
            - signed hex: ${signedRawTx.hex}
            - tx hash: ${signedRawTx.hash}
         """
      )

      broadcastedTxHash <- explorerClient(coin).broadcastTransaction(signedRawTx.hex)

      _ <- log.info(s"Broadcasted tx hash: $broadcastedTxHash")

      _ <-
        if (signedRawTx.hash != broadcastedTxHash)
          IO.raiseError(
            new Exception(
              s"Signed tx hash is not equal to broadcasted tx hash: ${signedRawTx.hash} != $broadcastedTxHash"
            )
          )
        else IO.unit

    } yield {
      BroadcastTransactionResponse(
        signedRawTx.hex,
        broadcastedTxHash,
        signedRawTx.witnessHash
      ).toProto
    }

  private def createRawTransaction(
      strategy: CoinSelectionStrategy,
      utxos: List[Utxo],
      outputs: List[PrepareTxOutput],
      changeAddress: String,
      estimatedFeeSatPerKb: Long,
      amount: BigInt,
      retryCount: Int = 5
  ): IO[RawTransactionAndUtxos] = {

    for {

      _ <-
        if (retryCount <= 0)
          IO.raiseError(
            new Exception(s"""Impossible to create raw transaction satisfying criterias :
                utxos : ${utxos.asJson}
                outputs: ${outputs.asJson}
                estimatedFeeSatPerKb: $estimatedFeeSatPerKb
              """)
          )
        else IO.unit

      selectedUtxos <- CoinSelectionService.coinSelection(
        strategy,
        utxos,
        amount
      )
      _ <- log.info(
        s"""Picked Utxos :
            - number of utxos : ${selectedUtxos.size}
            - sum : ${selectedUtxos.map(_.value).sum}
         """
      )

      _ <- validateTransaction(selectedUtxos, outputs)

      rawTransaction <- bitcoinLibClient.createTransaction(
        selectedUtxos,
        outputs,
        changeAddress,
        estimatedFeeSatPerKb,
        0L
      )

      rawTransactionAndUtxos <- rawTransaction.notEnoughUtxo.fold(
        IO(
          RawTransactionAndUtxos(
            rawTransaction.hex,
            rawTransaction.hash,
            rawTransaction.witnessHash,
            selectedUtxos
          )
        )
      )(notEnoughUtxo =>
        createRawTransaction(
          strategy,
          utxos,
          outputs,
          changeAddress,
          estimatedFeeSatPerKb,
          outputs.map(_.value).sum + notEnoughUtxo.missingAmount,
          retryCount - 1
        )
      )

    } yield rawTransactionAndUtxos

  }

  private def getUTXOs(accountId: UUID, limit: Int, sort: Sort): Stream[IO, Utxo] = {
    def getUtxosRec(accountId: UUID, limit: Int, offset: Int, sort: Sort): Stream[IO, Utxo] = {
      Stream
        .eval(interpreterClient.getUTXOs(accountId, limit + offset, offset, Some(sort)))
        .flatMap { result =>
          val head = Stream.chunk(Chunk.seq(result.utxos)).covary[IO]

          val tail =
            if (result.truncated)
              getUtxosRec(accountId, limit, offset + limit, sort)
            else
              Stream.empty

          head ++ tail
        }
    }

    getUtxosRec(accountId, limit, 0, sort)
  }

  private def validateTransaction(utxos: List[Utxo], recipients: List[PrepareTxOutput]): IO[Unit] =
    if (utxos.map(_.value).sum < recipients.map(_.value).sum)
      IO.raiseError(new Exception("Not enough coins in Utxos to cover for coins sent."))
    else
      IO.unit

}
