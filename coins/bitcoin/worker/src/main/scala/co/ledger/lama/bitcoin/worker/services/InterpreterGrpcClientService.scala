package co.ledger.lama.bitcoin.worker.services

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.interpreter.protobuf.{
  AccountAddress,
  BitcoinInterpreterServiceFs2Grpc,
  DeleteTransactionsRequest,
  GetTransactionsRequest,
  GetTransactionsResult,
  SaveTransactionsRequest,
  SortingOrder
}
import com.google.protobuf.ByteString
import co.ledger.lama.bitcoin.worker.models.explorer.{BlockHeight, Transaction}
import co.ledger.lama.bitcoin.worker.services.SortingEnum.{Ascending, Descending, SortingEnum}
import co.ledger.lama.bitcoin.worker.utils.ProtobufUtils
import co.ledger.lama.common.utils.UuidUtils
import io.grpc.Metadata

object SortingEnum extends Enumeration {
  type SortingEnum = Value
  val Ascending, Descending = Value
}

trait InterpreterService {
  def saveTransactions(
      accountId: UUID,
      addresses: List[AccountAddress],
      txs: List[Transaction]
  ): IO[Unit]

  def removeTransactions(accountId: UUID, blockHeightCursor: Option[BlockHeight]): IO[Unit]

  def getTransactions(
      accountId: UUID,
      blockHeight: Option[BlockHeight],
      limit: Option[Int],
      offset: Option[Int],
      sortingOrder: Option[SortingEnum]
  ): IO[GetTransactionsResult]
}

class InterpreterGrpcClientService(
    grpcBtcInterpreter: BitcoinInterpreterServiceFs2Grpc[IO, Metadata]
) extends InterpreterService {

  def getByteStringedString(str: String): ByteString = ByteString.copyFromUtf8(str)

  def saveTransactions(
      accountId: UUID,
      addresses: List[AccountAddress],
      txs: List[Transaction]
  ): IO[Unit] =
    grpcBtcInterpreter
      .saveTransactions(
        new SaveTransactionsRequest(
          accountId = UuidUtils uuidToBytes accountId,
          accountAddresses = addresses,
          transactions = txs.map(ProtobufUtils.serializeTransaction)
        ),
        new Metadata()
      )
      .void

  def removeTransactions(accountId: UUID, blockHeightCursor: Option[BlockHeight]): IO[Unit] =
    grpcBtcInterpreter
      .deleteTransactions(
        new DeleteTransactionsRequest(
          UuidUtils uuidToBytes accountId,
          blockHeightCursor.getOrElse(0)
        ),
        new Metadata()
      )
      .void

  def getTransactions(
      accountId: UUID,
      blockHeight: Option[BlockHeight],
      limit: Option[Int],
      offset: Option[Int],
      sortingOrder: Option[SortingEnum]
  ): IO[GetTransactionsResult] = {

    val sort = sortingOrder.getOrElse(SortingEnum.Descending) match {
      case Ascending  => SortingOrder.ASC
      case Descending => SortingOrder.DESC
    }

    grpcBtcInterpreter.getTransactions(
      new GetTransactionsRequest(
        UuidUtils uuidToBytes accountId,
        blockHeight.getOrElse(0),
        limit.getOrElse(0),
        offset.getOrElse(0),
        sort
      ),
      new Metadata()
    )
  }
}
