package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.bitcoin.common.models.Explorer._
import co.ledger.lama.bitcoin.common.models.Service._
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.utils.UuidUtils
import com.google.protobuf.empty.Empty
import doobie.Transactor
import io.grpc.{Metadata, ServerServiceDefinition}

trait Interpreter extends protobuf.BitcoinInterpreterServiceFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    protobuf.BitcoinInterpreterServiceFs2Grpc.bindService(this)
}

class DbInterpreter(db: Transactor[IO]) extends Interpreter {

  val transactionInterpreter = new TransactionInterpreter(db)
  val operationInterpreter   = new OperationInterpreter(db)

  def saveTransactions(request: protobuf.SaveTransactionsRequest, ctx: Metadata): IO[Empty] =
    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      txs       <- IO.pure(request.transactions.map(Transaction.fromProto).toList)
      _         <- transactionInterpreter.saveTransactions(accountId, txs)
    } yield Empty() //TODO return Int

  def getOperations(
      request: protobuf.GetOperationsRequest,
      ctx: Metadata
  ): IO[protobuf.GetOperationsResult] = {
    val limit  = if (request.limit <= 0) 20 else request.limit
    val offset = if (request.offset < 0) 0 else request.offset

    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      opResult  <- operationInterpreter.getOperations(accountId, limit, offset)
      (operations, truncated) = opResult
    } yield protobuf.GetOperationsResult(operations.map(_.toProto), truncated)
  }
  def deleteTransactions(request: protobuf.DeleteTransactionsRequest, ctx: Metadata): IO[Empty] =
    ???

  def computeOperations(request: protobuf.ComputeOperationsRequest, ctx: Metadata): IO[Empty] =
    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      addresses = request.addresses.map(AccountAddress.fromProto).toList
      _ <- operationInterpreter.computeOperations(accountId, addresses)
    } yield Empty() //TODO return Int

}
