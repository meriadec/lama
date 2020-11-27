package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.worker.DefaultInput
import co.ledger.lama.bitcoin.common.services.{
  ExplorerClient,
  InterpreterClientService,
  KeychainClientService
}
import co.ledger.lama.bitcoin.common.models.interpreter.AccountAddress
import co.ledger.lama.bitcoin.common.models.interpreter.ChangeType
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.models._
import co.ledger.lama.bitcoin.worker.services._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.Status.{Registered, Unregistered}
import co.ledger.lama.common.models.{ReportableEvent, WorkableEvent}
import co.ledger.lama.common.utils.UuidUtils
import fs2.{Chunk, Pull, Stream}
import io.circe.Json
import io.circe.syntax._

class Worker(
    syncEventService: SyncEventService,
    keychainClient: KeychainClientService,
    explorerClient: ExplorerClient,
    interpreterClient: InterpreterClientService,
    cursorStateService: CursorStateService,
    conf: Config
) extends IOLogging {

  def run(implicit cs: ContextShift[IO], t: Timer[IO]): Stream[IO, Unit] =
    syncEventService.consumeWorkableEvents
      .evalMap { workableEvent =>
        val reportableEvent =
          workableEvent.status match {
            case Registered   => synchronizeAccount(workableEvent)
            case Unregistered => deleteAccount(workableEvent)
          }

        // In case of error, fallback to a reportable failed event.
        log.info(s"Received event: ${workableEvent.asJson.toString}") *>
          reportableEvent
            .handleErrorWith { error =>
              val previousState =
                workableEvent.payload.data
                  .as[PayloadData]
                  .toOption

              val payloadData = PayloadData(
                lastBlock = previousState.flatMap(_.lastBlock),
                errorMessage = Some(error.getMessage)
              )

              val failedEvent = workableEvent.reportFailure(payloadData.asJson)

              log.error(s"Failed event: $failedEvent", error) *>
                IO.pure(failedEvent)
            }
            // Always report the event at the end.
            .flatMap(syncEventService.reportEvent)
      }

  def synchronizeAccount(
      workableEvent: WorkableEvent
  )(implicit cs: ContextShift[IO], t: Timer[IO]): IO[ReportableEvent] = {
    val account = workableEvent.payload.account

    val previousBlockState =
      workableEvent.payload.data
        .as[PayloadData]
        .toOption
        .flatMap(_.lastBlock)

    // sync the whole account per streamed batch
    for {
      _ <- log.info(s"Syncing from cursor state: $previousBlockState")

      keychainId   <- UuidUtils.stringToUuidIO(account.key)
      keychainInfo <- keychainClient.getKeychainInfo(keychainId)

      // REORG
      lastValidBlock <- previousBlockState.map { block =>
        for {
          lvb <- cursorStateService.getLastValidState(account.id, block)
          _ <-
            if (block.hash == lvb.hash)
              // If the previous block is still valid, do not reorg
              IO.unit
            else
              // remove all transactions and operations up until last valid block
              interpreterClient
                .removeDataFromCursor(account.id, Some(lvb.height))
        } yield lvb
      }.sequence

      batchResults <- syncAccountBatch(
        account.id,
        keychainId,
        keychainInfo.lookaheadSize,
        lastValidBlock.map(_.hash),
        0,
        keychainInfo.lookaheadSize
      ).stream.compile.toList

      addresses = batchResults.flatMap(_.addresses).map { a =>
        AccountAddress(
          a.address,
          if (a.change.isChangeExternal) ChangeType.External else ChangeType.Internal
        )
      }

      opsCount <- interpreterClient
        .compute(account.id, addresses)

      _ <- log.info(s"$opsCount operations computed")

      txs = batchResults.flatMap(_.transactions).distinctBy(_.hash)

      lastBlock =
        if (txs.isEmpty) previousBlockState
        else Some(txs.maxBy(_.block.time).block)

      _ <- log.info(s"New cursor state: $lastBlock")
    } yield {
      // New cursor state.
      val payloadData = PayloadData(lastBlock = lastBlock)

      // Create the reportable successful event.
      workableEvent.reportSuccess(payloadData.asJson)
    }
  }

  /** Sync account algorithm:
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
  )(implicit cs: ContextShift[IO], t: Timer[IO]): Pull[IO, BatchResult, Unit] =
    Pull
      .eval {
        for {
          // Get batch of addresses from the keychain
          _ <- log.info("Calling keychain to get addresses")
          addressInfos <- keychainClient
            .getAddresses(keychainId, fromAddrIndex, toAddrIndex)

          // For this batch of addresses, fetch transactions from the explorer.
          _ <- log.info("Fetching transactions from explorer")
          transactions <-
            explorerClient
              .getConfirmedTransactions(addressInfos.map(_.address), blockHashCursor)
              .prefetch
              .chunkN(conf.maxTxsToSavePerBatch)
              .map(_.toList)
              .parEvalMapUnordered(conf.maxConcurrent) { txs =>
                // Ask to interpreter to save transactions.
                for {
                  _ <- log.info(s"Sending ${txs.size} transactions to interpreter")
                  savedTxsCount <- interpreterClient
                    .saveTransactions(accountId, txs)
                  _ <- log.info(s"$savedTxsCount new transactions saved")
                } yield txs
              }
              .flatMap(Stream.emits(_))
              .compile
              .toList

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
              log.info(s"Marking addresses as used") *>
                keychainClient.markAddressesAsUsed(keychainId, usedAddressesInfos.map(_.address))
            } else IO.unit

          // Flag to know if we continue or not to discover addresses
          continue <-
            keychainClient
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

  def deleteAccount(workableEvent: WorkableEvent): IO[ReportableEvent] =
    interpreterClient
      .removeDataFromCursor(workableEvent.accountId, None)
      .map(_ => workableEvent.reportSuccess(Json.obj()))
}
