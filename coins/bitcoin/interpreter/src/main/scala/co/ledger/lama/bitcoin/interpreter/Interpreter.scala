package co.ledger.lama.bitcoin.interpreter

import cats.effect.{ConcurrentEffect, IO}
import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.common.models.service._
import co.ledger.lama.bitcoin.interpreter.protobuf.ResultCount
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.utils.UuidUtils
import doobie.Transactor
import io.grpc.{Metadata, ServerServiceDefinition}

trait Interpreter extends protobuf.BitcoinInterpreterServiceFs2Grpc[IO, Metadata] {
  def definition(implicit ce: ConcurrentEffect[IO]): ServerServiceDefinition =
    protobuf.BitcoinInterpreterServiceFs2Grpc.bindService(this)
}

class DbInterpreter(db: Transactor[IO]) extends Interpreter with IOLogging {

  val transactionInterpreter = new TransactionInterpreter(db)
  val operationInterpreter   = new OperationInterpreter(db)

  def saveTransactions(
      request: protobuf.SaveTransactionsRequest,
      ctx: Metadata
  ): IO[protobuf.ResultCount] = {
    log.info(s"Saving ${request.transactions.size} transactions")
    log.debug(s"{request.transactions}")

    for {
      accountId  <- UuidUtils.bytesToUuidIO(request.accountId)
      txs        <- IO.pure(request.transactions.map(Transaction.fromProto).toList)
      savedCount <- transactionInterpreter.saveTransactions(accountId, txs)

      _ = log.info("Transactions saved")

    } yield ResultCount(savedCount)
  }

  def getOperations(
      request: protobuf.GetOperationsRequest,
      ctx: Metadata
  ): IO[protobuf.GetOperationsResult] = {
    val limit  = if (request.limit <= 0) 20 else request.limit
    val offset = if (request.offset < 0) 0 else request.offset

    log.info(s"""Getting operations with parameters:
         |- accountId: ${request.accountId}
         |- limit: $limit
         |- offset: $offset
         |""".stripMargin)

    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      _ = log.info("Fetching operations")
      opResult <- operationInterpreter.getOperations(accountId, limit, offset)
      _                       = log.info(s"$opResult")
      (operations, truncated) = opResult
    } yield protobuf.GetOperationsResult(operations.map(_.toProto), truncated)
  }

  def getUTXOs(request: protobuf.GetUTXOsRequest, ctx: Metadata): IO[protobuf.GetUTXOsResult] = {

    val limit  = if (request.limit <= 0) 20 else request.limit
    val offset = if (request.offset < 0) 0 else request.offset

    log.info(s"""Getting UTXOs with parameters:
              |- accountId: ${request.accountId}
              |- limit: $limit
              |- offset: $offset
              |""".stripMargin)

    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      _         <- log.info("Fetching UTXOs")
      res       <- operationInterpreter.getUTXOs(accountId, limit, offset)
      (utxos, truncated) = res
    } yield {
      protobuf.GetUTXOsResult(utxos.map(_.toProto), truncated)
    }
  }

  def deleteTransactions(
      request: protobuf.DeleteTransactionsRequest,
      ctx: Metadata
  ): IO[protobuf.ResultCount] = {
    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      _         <- log.info(s"""Deleting transactions with parameters:
                      |- accountId: ${accountId}
                      |- blockHeight: ${request.blockHeight}""".stripMargin)
      txRes     <- transactionInterpreter.deleteTransactions(accountId, request.blockHeight)
      _         <- log.info(s"Deleted $txRes transactions")
    } yield ResultCount(txRes)
  }

  def computeOperations(
      request: protobuf.ComputeOperationsRequest,
      ctx: Metadata
  ): IO[protobuf.ResultCount] = {
    log.info(s"""Computing operations with parameters:
         |- accountId: ${request.accountId}
         |- addresses: ${request.addresses}
         |""".stripMargin)
    for {
      accountId <- UuidUtils.bytesToUuidIO(request.accountId)
      addresses = request.addresses.map(AccountAddress.fromProto).toList
      savedOps <- operationInterpreter.computeOperations(accountId, addresses)
    } yield ResultCount(savedOps)
  }
}
