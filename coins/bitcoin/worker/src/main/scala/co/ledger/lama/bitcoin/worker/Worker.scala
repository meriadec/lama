package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.{ConcurrentEffect, IO, Timer}
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.Explorer.DefaultInput
import co.ledger.lama.bitcoin.interpreter.protobuf.AccountAddress
import co.ledger.lama.bitcoin.interpreter.protobuf.ChangeType.{EXTERNAL, INTERNAL}
import co.ledger.lama.bitcoin.worker.models.{BatchResult, PayloadData}
import co.ledger.lama.bitcoin.worker.services._
import co.ledger.lama.common.models.Status.{Registered, Unregistered}
import co.ledger.lama.common.models.{ReportableEvent, WorkableEvent}
import fs2.{Chunk, Pull, Stream}
import io.circe.Json
import io.circe.syntax._

import scala.util.Try

class Worker(
    syncEventService: SyncEventService,
    keychainService: KeychainService,
    explorerService: ExplorerService,
    interpreterService: InterpreterService
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
            error.printStackTrace() // TODO: logging

            val previousState =
              workableEvent.payload.data
                .as[PayloadData]
                .toOption

            val payloadData = PayloadData(
              lastBlock = previousState.flatMap(_.lastBlock),
              errorMessage = Some(error.getMessage)
            )

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

    val previousState =
      workableEvent.payload.data
        .as[PayloadData]
        .toOption

    // sync the whole account per streamed batch
    for {
      keychainId <- IO.fromTry(Try(UUID.fromString(account.key)))

      keychainInfo <- keychainService.getKeychainInfo(keychainId)

      batchResults <- syncAccountBatch(
        account.id,
        keychainId,
        keychainInfo.lookaheadSize,
        previousState.flatMap(_.lastBlock.map(_.hash)),
        0,
        keychainInfo.lookaheadSize
      ).stream.compile.toList

      addresses = batchResults.flatMap(_.addresses).map { a =>
        AccountAddress(a.address, if (a.change.isChangeExternal) EXTERNAL else INTERNAL)
      }

      _ <- interpreterService.computeOperations(account.id, addresses)
    } yield {
      val txs = batchResults.flatMap(_.transactions).distinctBy(_.hash)

      val txsSize = txs.size

      val lastBlock =
        if (txsSize == 0) previousState.flatMap(_.lastBlock)
        else Some(txs.maxBy(_.block.time).block)

      // New cursor state.
      val payloadData = PayloadData(
        lastBlock = lastBlock,
        fetchedTxsSize = Some(txsSize)
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
      accountId: UUID,
      keychainId: UUID,
      lookaheadSize: Int,
      blockHashCursor: Option[String],
      fromAddrIndex: Int,
      toAddrIndex: Int
  )(implicit ce: ConcurrentEffect[IO], t: Timer[IO]): Pull[IO, BatchResult, Unit] =
    Pull
      .eval {
        for {
          // Get batch of addresses from the keychain
          addressInfos <- keychainService.getAddresses(keychainId, fromAddrIndex, toAddrIndex)

          // For this batch of addresses, fetch transactions from the explorer.
          transactions <-
            explorerService
              .getTransactions(addressInfos.map(_.address), blockHashCursor)
              .compile
              .toList

          // Ask to interpreter to save transactions.
          _ <- interpreterService.saveTransactions(accountId, transactions)

          // Filter only used addresses.
          usedAddressesInfos = addressInfos.filter { a =>
            transactions.exists { t =>
              val isInputAddress = t.inputs.collectFirst {
                case i: DefaultInput if i.address == a.address => i
              }.isDefined

              isInputAddress || t.outputs.exists(_.address == a.address)
            }
          }

          _ <-
            if (transactions.nonEmpty) {
              // Mark addresses as used.
              keychainService.markAddressesAsUsed(keychainId, usedAddressesInfos.map(_.address))
            } else IO.unit

          // Flag to know if we continue or not to discover addresses
          continue <-
            keychainService
              .getAddresses(keychainId, toAddrIndex, toAddrIndex + lookaheadSize)
              .map(_.nonEmpty)

        } yield BatchResult(usedAddressesInfos, transactions, continue)
      }
      .flatMap { batchResult =>
        if (batchResult.continue)
          Pull.output(Chunk(batchResult)) >>
            syncAccountBatch(
              accountId,
              keychainId,
              lookaheadSize,
              blockHashCursor,
              toAddrIndex,
              toAddrIndex + lookaheadSize
            )
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
