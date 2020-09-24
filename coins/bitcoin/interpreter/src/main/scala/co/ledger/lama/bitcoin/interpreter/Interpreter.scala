package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.bitcoin.interpreter.protobuf._
import com.google.protobuf.ByteString
import com.google.protobuf.empty.Empty
import io.grpc.{Metadata, ServerServiceDefinition}

import scala.collection.mutable

trait Interpreter extends BitcoinInterpreterServiceFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    BitcoinInterpreterServiceFs2Grpc.bindService(this)

  def bytesToUUID(bytes: ByteString): UUID = UUID.nameUUIDFromBytes(bytes.toByteArray)
}

class FakeInterpreter extends Interpreter {
  var transactions: mutable.Map[UUID, Seq[Transaction]] = mutable.Map[UUID, Seq[Transaction]]()

  def saveTransactions(request: SaveTransactionsRequest, ctx: Metadata): IO[Empty] =
    IO.pure {
      transactions.update(bytesToUUID(request.accountId), request.transactions)
    }.map(_ => Empty())

  def deleteTransactions(request: DeleteTransactionsRequest, ctx: Metadata): IO[Empty] =
    IO.pure {
      val accountId = bytesToUUID(request.accountId)
      if (request.blockHeight == 0) {
        transactions remove accountId
      } else {
        transactions.update(
          accountId,
          transactions(accountId) filter {
            _.block.exists { _.height < request.blockHeight }
          }
        )
      }
    }.map(_ => Empty())

  def getTransactions(request: GetTransactionsRequest, ctx: Metadata): IO[GetTransactionsResult] = {
    val limit = if (request.limit == 0) 20 else request.limit

    IO {
      val filteredTransactions = transactions
        .getOrElse(bytesToUUID(request.accountId), Seq())
        .filter(_.block.exists { _.height >= request.blockHeight })
        .sortWith((t, t2) => {
          request.sort match {
            case SortingOrder.DESC => t.hash > t2.hash
            case _                 => t.hash < t2.hash
          }
        })

      val slicedTransactions = filteredTransactions.slice(request.offset, request.offset + limit)
      val hasMore            = filteredTransactions.drop(request.offset + limit).nonEmpty

      GetTransactionsResult(slicedTransactions, truncated = hasMore)
    }
  }
}
