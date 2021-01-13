package co.ledger.lama.bitcoin.worker.services

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.clients.grpc.{InterpreterClient, KeychainClient}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient
import co.ledger.lama.bitcoin.common.models.explorer.{DefaultInput, UnconfirmedTransaction}
import co.ledger.lama.bitcoin.worker.models.BatchResult
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.Coin
import fs2.{Chunk, Pull, Stream}

import java.util.UUID

class Bookkeeper(
    keychainClient: KeychainClient,
    explorerClient: Coin => ExplorerClient,
    interpreterClient: InterpreterClient,
    maxTxsToSavePerBatch: Int,
    maxConcurrent: Int
) extends IOLogging {

  def recordUnconfirmedTransactions(
      coin: Coin,
      accountId: UUID,
      keychainId: UUID,
      lookaheadSize: Int,
      fromAddrIndex: Int,
      toAddrIndex: Int
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): Pull[IO, BatchResult[UnconfirmedTransaction], Unit] =
    Pull
      .eval {
        for {
          // Get batch of addresses from the keychain
          _            <- log.info("Calling keychain to get addresses")
          addressInfos <- keychainClient.getAddresses(keychainId, fromAddrIndex, toAddrIndex)

          // For this batch of addresses, fetch transactions from the explorer.
          _ <- log.info("Fetching transactions from explorer")
          transactions <- explorerClient(coin)
            .getUnconfirmedTransactions(addressInfos.map(_.accountAddress).toSet)
            .prefetch
            .chunkN(maxTxsToSavePerBatch)
            .map(_.toList)
            .parEvalMapUnordered(maxConcurrent) { txs =>
              // Ask to interpreter to save transactions.
              for {
                _             <- log.info(s"Sending ${txs.size} unconfirmed transactions to interpreter")
                savedTxsCount <- interpreterClient.saveUnconfirmedTransactions(accountId, txs)
                _             <- log.info(s"$savedTxsCount new transactions saved from mempool")
              } yield txs
            }
            .flatMap(Stream.emits(_))
            .compile
            .toList

          // Filter only used addresses.
          usedAddressesInfos = addressInfos.filter { a =>
            transactions.exists { t =>
              val isInputAddress = t.inputs.collectFirst {
                case i: DefaultInput if i.address == a.accountAddress => i
              }.isDefined

              isInputAddress || t.outputs.exists(_.address == a.accountAddress)
            }
          }

          _ <-
            if (transactions.nonEmpty) {
              // Mark addresses as used.
              log.info(s"Marking addresses as used") *>
                keychainClient.markAddressesAsUsed(
                  keychainId,
                  usedAddressesInfos.map(_.accountAddress)
                )
            } else IO.unit

          // Flag to know if we continue or not to discover addresses
          continue <- keychainClient
            .getAddresses(keychainId, toAddrIndex, toAddrIndex + lookaheadSize)
            .map(_.nonEmpty)

        } yield BatchResult(usedAddressesInfos, transactions, continue)
      }
      .flatMap { batchResult =>
        if (batchResult.continue)
          Pull.output(Chunk(batchResult)) >>
            recordUnconfirmedTransactions(
              coin,
              accountId,
              keychainId,
              lookaheadSize,
              toAddrIndex,
              toAddrIndex + lookaheadSize
            )
        else
          Pull.output(Chunk(batchResult))
      }
}
