package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.{ConcurrentEffect, IO, Timer}
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.BlockHash
import co.ledger.lama.bitcoin.worker.models.{BatchResult, PayloadData}
import co.ledger.lama.bitcoin.worker.services._
import co.ledger.lama.common.models.Status.{Registered, Unregistered}
import co.ledger.lama.common.models.{AccountIdentifier, ReportableEvent, WorkableEvent}
import fs2.{Chunk, Pull, Stream}
import io.circe.Json
import io.circe.syntax._

import scala.util.Try

class Worker(
    syncEventService: SyncEventService,
    keychainService: KeychainService,
    explorerService: ExplorerService,
    interpreterService: InterpreterService,
    addressesBatchSize: Int
) {

  def run(implicit ce: ConcurrentEffect[IO], t: Timer[IO]): Stream[IO, Unit] =
    syncEventService.consumeWorkableEvents
      .evalMap { workableEvent =>
        val reportableEvent = workableEvent.status match {
          case Registered   => synchronizeAccount(workableEvent)
          case Unregistered => deleteAccount(workableEvent)
        }

        // In case of error, fallback to a reportable failed event.
        reportableEvent
          .handleErrorWith { error =>
            val payloadData = PayloadData(errorMessage = Some(error.getMessage))
            val failedEvent = workableEvent.reportFailure(payloadData.asJson)
            IO.pure(failedEvent)
          }
          // Always report the event at the end.
          .flatMap(syncEventService.reportEvent)
      }

  def synchronizeAccount(
      workableEvent: WorkableEvent
  )(implicit ce: ConcurrentEffect[IO], t: Timer[IO]): IO[ReportableEvent] = {
    val account = workableEvent.payload.account

    val blockHashCursor =
      workableEvent.payload.data
        .as[PayloadData]
        .toOption
        .flatMap(_.blockHash)

    // sync the whole account per streamed batch
    syncAccountBatch(account, blockHashCursor, 0, addressesBatchSize).stream.compile.toList
      .map { batchResults =>
        // TODO: pass account addresses to the interpreter when calling interpreter.computeOperations
        // val addresses = batchResults.flatMap(_.addresses).distinctBy(_.address)

        val txs = batchResults.flatMap(_.transactions).distinctBy(_.hash)

        val lastBlock = txs.maxBy(_.block.time).block
        val txsSize   = txs.size

        // New cursor state.
        val payloadData = PayloadData(
          blockHeight = Some(lastBlock.height),
          blockHash = Some(lastBlock.hash),
          txsSize = Some(txsSize)
        )

        // Create the reportable successful event.
        workableEvent.reportSuccess(payloadData.asJson)
      }
  }

  /**
    * Sync account algorithm:
    *   - 1) Get addresses per batch from the keychain
    *   - 2) Get transactions per batch from the explorer
    *   - 3a) If there are transactions for this batch of addresses:
    *       - mark addresses as used
    *       - repeat 1)
    *   - 3b) Otherwise, stop sync because we consider this batch as fresh addresses
    */
  private def syncAccountBatch(
      account: AccountIdentifier,
      blockHashCursor: Option[BlockHash],
      fromAddrIndex: Int,
      toAddrIndex: Int
  )(implicit ce: ConcurrentEffect[IO], t: Timer[IO]): Pull[IO, BatchResult, Unit] =
    Pull
      .eval {
        for {
          keychainId <- IO.fromTry(Try(UUID.fromString(account.key)))

          // Get batch of addresses from the keychain
          addressInfos <- keychainService.getAddresses(keychainId, fromAddrIndex, toAddrIndex)

          // For this batch of addresses, fetch transactions from the explorer.
          transactions <-
            explorerService
              .getTransactions(addressInfos.map(_.address), blockHashCursor)
              .compile
              .toList

          // Ask to interpreter to save transactions.
          _ <- interpreterService.saveTransactions(account.id, transactions)

          // Filter only used addresses.
          usedAddressesInfos = addressInfos.filter { a =>
            transactions.exists { t =>
              t.inputs.exists(_.address == a.address) || t.outputs.exists(_.address == a.address)
            }
          }

          _ <-
            if (transactions.nonEmpty) {
              // Mark addresses as used.
              keychainService.markAddressesAsUsed(keychainId, usedAddressesInfos.map(_.address))
            } else IO.unit
        } yield BatchResult(usedAddressesInfos, transactions)
      }
      .flatMap { batchResult =>
        if (batchResult.transactions.nonEmpty)
          Pull.output(Chunk(batchResult)) >>
            syncAccountBatch(account, blockHashCursor, toAddrIndex, toAddrIndex + toAddrIndex)
        else
          Pull.output(Chunk(batchResult))
      }

  // TODO:
  //  - ask interpreter to delete account
  //  - delete keychain
  //  - report successful deleted event
  def deleteAccount(workableEvent: WorkableEvent): IO[ReportableEvent] =
    IO.pure(workableEvent.reportSuccess(Json.obj()))

}
