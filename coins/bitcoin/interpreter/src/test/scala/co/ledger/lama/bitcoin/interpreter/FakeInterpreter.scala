package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.interpreter.BalanceHistory
import co.ledger.lama.bitcoin.interpreter.protobuf._
import com.google.protobuf.ByteString
import io.grpc.Metadata

import scala.collection.mutable

class FakeInterpreter extends Interpreter {

  def bytesToUUID(bytes: ByteString): UUID = UUID.nameUUIDFromBytes(bytes.toByteArray)

  var transactions: mutable.Map[UUID, Seq[Transaction]] = mutable.Map[UUID, Seq[Transaction]]()

  def saveTransactions(request: SaveTransactionsRequest, ctx: Metadata): IO[ResultCount] =
    IO.pure {
      transactions.update(bytesToUUID(request.accountId), request.transactions)
    }.map(_ => ResultCount(request.transactions.size))

  def removeDataFromCursor(request: DeleteTransactionsRequest, ctx: Metadata): IO[ResultCount] =
    IO.pure {
      val accountId = bytesToUUID(request.accountId)
      if (request.blockHeight == 0) {
        transactions remove accountId
      } else {
        transactions.update(
          accountId,
          transactions(accountId) filter {
            _.block.exists {
              _.height < request.blockHeight
            }
          }
        )
      }
    }.map(_ => ResultCount(1))

  def getOperations(request: GetOperationsRequest, ctx: Metadata): IO[GetOperationsResult] = {
    val limit = if (request.limit == 0) 20 else request.limit

    IO {
      val filteredTransactions = transactions
        .getOrElse(bytesToUUID(request.accountId), Seq())
        .filter(_.block.exists {
          _.height >= request.blockHeight
        })
        .sortWith((t, t2) => {
          request.sort match {
            case SortingOrder.DESC => t.hash > t2.hash
            case _                 => t.hash < t2.hash
          }
        })

      val slicedTransactions = filteredTransactions.slice(request.offset, request.offset + limit)
      val hasMore            = filteredTransactions.drop(request.offset + limit).nonEmpty

      val operations = slicedTransactions.map(transaction =>
        Operation(
          accountId = request.accountId,
          hash = transaction.hash,
          None,
          OperationType.SENT,
          BigInt(0).toString,
          BigInt(0).toString,
          transaction.block.get.time
        )
      )

      GetOperationsResult(operations, truncated = hasMore)
    }
  }

  def compute(request: ComputeRequest, ctx: Metadata): IO[ResultCount] =
    IO.pure(ResultCount(0))

  def getUTXOs(request: protobuf.GetUTXOsRequest, ctx: Metadata): IO[protobuf.GetUTXOsResult] =
    IO.pure(GetUTXOsResult(Nil))

  def getBalance(request: GetBalanceRequest, ctx: Metadata): IO[protobuf.BalanceHistory] =
    IO.pure(BalanceHistory(0, 0, 0, 0).toProto)

  def getBalanceHistory(
      request: GetBalanceHistoryRequest,
      ctx: Metadata
  ): IO[GetBalanceHistoryResult] =
    IO.pure(GetBalanceHistoryResult(balances = Seq(BalanceHistory(0, 0, 0, 0).toProto)))

  def getLastBlocks(request: GetLastBlocksRequest, ctx: Metadata): IO[GetLastBlocksResult] =
    IO.pure(GetLastBlocksResult(List(Block("hash", 1L, None))))

}
