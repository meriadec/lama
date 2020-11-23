package co.ledger.lama.bitcoin.worker.services

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.worker.Block
import co.ledger.lama.bitcoin.common.services.{ExplorerClientService, InterpreterClientService}
import co.ledger.lama.common.logging.IOLogging
import org.http4s.client.UnexpectedStatus

class CursorStateService(
    explorerClient: ExplorerClientService,
    interpreterClient: InterpreterClientService
) extends IOLogging {

  /* This method checks if the provided block is valid by calling "explorerClient.getBlock()"
   * If it is, the block is returned and used for the next sync
   * It it isn't , the last 20 known blocks are queried to the interpreter for this account,
   * and for each block in reverse order, we check if it's a valid block.
   * The first valid block found this way is returned for the sync.
   */
  def getLastValidState(accountId: UUID, block: Block): IO[Block] = {

    /*
     * Unfortunately (for now), the signature of the explorer is not set in stone and recent changes made us rework this part.
     * To be sure, we now support 2 signatures :
     * - In both cases a valid hash returns a 200 with a list of blocks
     * - An unknown valid hash return either a 404, or an empty list
     * - An invalid hash returns either a 400 or a 500 error.
     */
    explorerClient
      .getBlock(block.hash)
      .flatMap {
        case Some(lvb) => IO.pure(lvb)
        case None      => fetchLastBlocksUntilValid(accountId, block)
      }
      .handleErrorWith {
        case serverError: UnexpectedStatus if serverError.status.code != 404 =>
          logUnexpectedError(block, serverError)
        case notFoundError: UnexpectedStatus if notFoundError.status.code == 404 =>
          fetchLastBlocksUntilValid(accountId, block)
      }
  }

  private def fetchLastBlocksUntilValid(accountId: UUID, block: Block): IO[Block] = {
    for {
      _ <- log.info(
        s"Block [hash: '${block.hash}', height: ${block.height}] has been invalidated, searching last known valid block."
      )
      getblocksResult <- interpreterClient.getLastBlocks(accountId)
      lastValidBlock  <- getlastValidBlockRec(getblocksResult.blocks.toList)
      _ <- log.info(
        s"block [hash: '${lastValidBlock.hash}', height: ${lastValidBlock.height}] is valid !"
      )
    } yield lastValidBlock
  }

  private def getlastValidBlockRec(blocks: List[Block]): IO[Block] =
    blocks match {
      case Nil => IO.raiseError(new Exception("no valid block found in the last blocks..."))
      case block :: tail =>
        log.info(s"testing block [hash: '${block.hash}', height: ${block.height}]") *>
          explorerClient
            .getBlock(block.hash)
            .flatMap {
              case Some(lvb) => IO.pure(lvb)
              case None      => getlastValidBlockRec(tail)
            }
            .handleErrorWith {
              case serverError: UnexpectedStatus if serverError.status.code != 404 =>
                logUnexpectedError(block, serverError)
              case notFoundError: UnexpectedStatus if notFoundError.status.code == 404 =>
                getlastValidBlockRec(tail)
            }

    }

  private def logUnexpectedError(b: Block, serverError: UnexpectedStatus): IO[Block] = {
    log.error(s"Error ${serverError.status.code} while calling explorer with block : ${b.hash}") *>
      IO.raiseError(serverError)
  }
}
