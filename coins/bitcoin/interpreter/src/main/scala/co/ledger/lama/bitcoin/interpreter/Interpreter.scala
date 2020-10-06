package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.{ConcurrentEffect, IO}
import cats.implicits._
import co.ledger.lama.bitcoin.common.models
import co.ledger.lama.bitcoin.interpreter.protobuf._
import co.ledger.lama.common.utils.UuidUtils
import com.google.protobuf.ByteString
import com.google.protobuf.empty.Empty
import doobie.Transactor
import doobie.implicits._
import io.grpc.{Metadata, ServerServiceDefinition}

trait Interpreter extends BitcoinInterpreterServiceFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    BitcoinInterpreterServiceFs2Grpc.bindService(this)

  def bytesToUUID(bytes: ByteString): UUID = UUID.nameUUIDFromBytes(bytes.toByteArray)
}

class DbInterpreter(db: Transactor[IO]) extends Interpreter {

  def saveTransactions(request: SaveTransactionsRequest, ctx: Metadata): IO[Empty] = {

    //TODO reorg : remove all blocks with height >= minimum transactions block height

    request.transactions.toList
      .traverse(requestTx => saveTx(request.accountId, requestTx))
      .map(_ => Empty())
  }

  private def saveTx(accountId: ByteString, requestTx: Transaction) = {
    for {
      tx        <- IO.pure(models.Transaction.fromProto(requestTx))
      accountId <- UuidUtils.bytesToUuidIO(accountId)
      res <- (
          Queries.upsertBlock(tx.block),
          Queries.saveTransaction(tx, accountId)
      ).tupled.transact(db).void
    } yield res
  }

  def getOperations(request: GetOperationsRequest, ctx: Metadata): IO[GetOperationsResult] = {
    val limit  = if (request.limit <= 0) 20 else request.limit
    val offset = if (request.offset < 0) 0 else request.offset

    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)

      // We fetch limit + 1 operations to know if there's more to fetch.
      operations <-
        Queries
          .fetchOperations(accountId, limit + 1, offset)
          .transact(db)
          .compile
          .toList

      truncated = operations.size > limit
    } yield {
      val protoOperations = operations.slice(0, limit).map(_.toProto)
      GetOperationsResult(protoOperations, truncated)
    }

    //TODO deal with ordering

  }

  def deleteTransactions(request: DeleteTransactionsRequest, ctx: Metadata): IO[Empty] = ???

  def computeOperations(request: ComputeOperationsRequest, ctx: Metadata): IO[Empty] =
    for {

      accountId <- UuidUtils.bytesToUuidIO(request.accountId)

      txs <-
        Queries
          .fetchUncomputedTxs(accountId)
          .transact(db)
          .compile
          .toList

      fullTxs <- txs.traverse { tx =>
        Queries
          .populateTx(tx, accountId)
          .transact(db)
      }

      _ <-
        fullTxs
          .flatMap { transaction =>
            OperationComputer.compute(transaction, accountId, request.addresses.toList)
          }
          .traverse { operation =>
            Queries
              .saveOperation(operation) //TODO use updateMany instead of map
          }
          .transact(db)

    } yield Empty()

}
