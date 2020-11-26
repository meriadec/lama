package co.ledger.lama.bitcoin.worker.mock.faulty

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.interpreter
import co.ledger.lama.bitcoin.common.models.interpreter.grpc.{
  GetLastBlocksResult,
  GetOperationsResult
}
import co.ledger.lama.bitcoin.common.models.interpreter.{AccountAddress, grpc}
import co.ledger.lama.bitcoin.common.models.worker.{Block, ConfirmedTransaction}
import co.ledger.lama.bitcoin.common.services.InterpreterClientService
import co.ledger.lama.common.models.Sort

import scala.collection.mutable

class FaultyInterpreterClientServiceMock extends InterpreterClientService {

  var savedTransactions: mutable.Map[UUID, List[ConfirmedTransaction]] = mutable.Map.empty

  def saveTransactions(
      accountId: UUID,
      txs: List[ConfirmedTransaction]
  ): IO[Int] =
    IO.raiseError(new Exception)

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int] = {
    val io = blockHeightCursor match {
      case Some(blockHeight) =>
        IO.delay {
          savedTransactions.update(
            accountId,
            savedTransactions.getOrElse(accountId, List.empty).filter(_.block.height < blockHeight)
          )
        }
      case None => IO.delay(savedTransactions.remove(accountId))
    }

    io.map(_ => 0)
  }

  def compute(accountId: UUID, addresses: List[AccountAddress]): IO[Int] = {
    IO.pure(0)
  }

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult] = {
    IO.pure(
      GetLastBlocksResult(
        List(
          Block(
            "0x00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608371", //invalid
            559035L,
            Instant.now()
          ),
          Block(
            "0x00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608372", //invalid
            559034L,
            Instant.now()
          ),
          Block(
            "0x00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379", //last valid
            559033L,
            Instant.now()
          ),
          Block(
            "0x00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608373", //invalid
            559032L,
            Instant.now()
          ),
          Block(
            "0000000000000000000bf68b57eacbff287ceafecb54a30dc3fd19630c9a3883", //valid but not last
            559031L,
            Instant.now()
          )
        )
      )
    )
  }

  override def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetOperationsResult] = IO.raiseError(new NotImplementedError("Implement if needed"))

  override def getUTXOs(
      accountId: UUID,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[grpc.GetUTXOsResult] = IO.raiseError(new NotImplementedError("Implement if needed"))

  override def getBalance(accountId: UUID): IO[interpreter.BalanceHistory] =
    IO.raiseError(new NotImplementedError("Implement if needed"))

  override def getBalanceHistory(
      accountId: UUID,
      start: Option[Instant],
      end: Option[Instant]
  ): IO[grpc.GetBalanceHistoryResult] =
    IO.raiseError(new NotImplementedError("Implement if needed"))
}
