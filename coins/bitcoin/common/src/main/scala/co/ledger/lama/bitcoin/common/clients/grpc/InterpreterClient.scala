package co.ledger.lama.bitcoin.common.clients.grpc

import java.time.Instant
import java.util.UUID
import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.explorer.{ConfirmedTransaction, UnconfirmedTransaction}
import co.ledger.lama.bitcoin.common.models.interpreter._
import co.ledger.lama.bitcoin.common.utils.BtcProtoUtils._
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.clients.grpc.GrpcClient
import co.ledger.lama.common.models.{Coin, Sort}
import co.ledger.lama.common.utils.{TimestampProtoUtils, UuidUtils}
import io.grpc.{ManagedChannel, Metadata}

trait InterpreterClient {
  def saveTransactions(accountId: UUID, txs: List[ConfirmedTransaction]): IO[Int]

  def saveUnconfirmedTransactions(accountId: UUID, txs: List[UnconfirmedTransaction]): IO[Int]

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int]

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult]

  def compute(
      accountId: UUID,
      coin: Coin,
      addresses: List[AccountAddress]
  ): IO[Int]

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetOperationsResult]

  def getUTXOs(
      accountId: UUID,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetUtxosResult]

  def getUnconfirmedUTXOs(
      accountId: UUID
  ): IO[List[Utxo]]

  def getBalance(accountId: UUID): IO[CurrentBalance]

  def getBalanceHistory(
      accountId: UUID,
      start: Option[Instant],
      end: Option[Instant],
      interval: Option[Int]
  ): IO[GetBalanceHistoryResult]

}

class InterpreterGrpcClient(
    val managedChannel: ManagedChannel
)(implicit val cs: ContextShift[IO])
    extends InterpreterClient {

  val client: protobuf.BitcoinInterpreterServiceFs2Grpc[IO, Metadata] =
    GrpcClient.resolveClient(protobuf.BitcoinInterpreterServiceFs2Grpc.stub[IO], managedChannel)

  def saveTransactions(accountId: UUID, txs: List[ConfirmedTransaction]): IO[Int] =
    client
      .saveTransactions(
        protobuf.SaveTransactionsRequest(
          accountId = UuidUtils uuidToBytes accountId,
          transactions = txs.map(_.toProto)
        ),
        new Metadata()
      )
      .map(_.count)

  def saveUnconfirmedTransactions(accountId: UUID, txs: List[UnconfirmedTransaction]): IO[Int] =
    IO.pure(txs.size)

  def removeDataFromCursor(accountId: UUID, blockHeightCursor: Option[Long]): IO[Int] =
    client
      .removeDataFromCursor(
        protobuf.DeleteTransactionsRequest(
          UuidUtils uuidToBytes accountId,
          blockHeightCursor.getOrElse(0)
        ),
        new Metadata()
      )
      .map(_.count)

  def getLastBlocks(accountId: UUID): IO[GetLastBlocksResult] =
    client
      .getLastBlocks(
        protobuf.GetLastBlocksRequest(
          UuidUtils uuidToBytes accountId
        ),
        new Metadata()
      )
      .map(GetLastBlocksResult.fromProto)

  def compute(accountId: UUID, coin: Coin, addresses: List[AccountAddress]): IO[Int] =
    client
      .compute(
        protobuf.ComputeRequest(
          UuidUtils.uuidToBytes(accountId),
          addresses.map(_.toProto),
          coin.name
        ),
        new Metadata()
      )
      .map(_.count)

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetOperationsResult] =
    client
      .getOperations(
        protobuf.GetOperationsRequest(
          accountId = UuidUtils.uuidToBytes(accountId),
          blockHeight = blockHeight,
          limit = limit,
          offset = offset,
          sort = sort.map(_.toProto).getOrElse(protobuf.SortingOrder.DESC)
        ),
        new Metadata
      )
      .map(GetOperationsResult.fromProto)

  def getUTXOs(
      accountId: UUID,
      limit: Int,
      offset: Int,
      sort: Option[Sort]
  ): IO[GetUtxosResult] = {
    client
      .getUTXOs(
        protobuf.GetUTXOsRequest(
          accountId = UuidUtils.uuidToBytes(accountId),
          limit = limit,
          offset = offset,
          sort = sort.map(_.toProto).getOrElse(protobuf.SortingOrder.DESC)
        ),
        new Metadata
      )
      .map(GetUtxosResult.fromProto)
  }

  def getUnconfirmedUTXOs(
      accountId: UUID
  ): IO[List[Utxo]] = {
    client
      .getUnconfirmedUTXOs(
        protobuf.GetUnconfirmedUTXOsRequest(
          accountId = UuidUtils.uuidToBytes(accountId)
        ),
        new Metadata
      )
      .map(_.utxos.map(Utxo.fromProto).toList)
  }

  def getBalance(accountId: UUID): IO[CurrentBalance] = {
    client
      .getBalance(
        protobuf.GetBalanceRequest(
          accountId = UuidUtils.uuidToBytes(accountId)
        ),
        new Metadata
      )
      .map(CurrentBalance.fromProto)
  }

  def getBalanceHistory(
      accountId: UUID,
      start: Option[Instant],
      end: Option[Instant],
      interval: Option[Int]
  ): IO[GetBalanceHistoryResult] = {
    client
      .getBalanceHistory(
        protobuf.GetBalanceHistoryRequest(
          accountId = UuidUtils.uuidToBytes(accountId),
          start.map(TimestampProtoUtils.serialize),
          end.map(TimestampProtoUtils.serialize),
          interval.getOrElse(0)
        ),
        new Metadata
      )
      .map(GetBalanceHistoryResult.fromProto)
  }

}
