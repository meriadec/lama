package co.ledger.lama.bitcoin.worker.services

import cats.effect.{ConcurrentEffect, IO, Timer}
import co.ledger.lama.bitcoin.common.models._
import co.ledger.lama.bitcoin.worker.config.ExplorerConfig
import co.ledger.lama.bitcoin.worker.models.GetTransactionsResponse
import io.circe.Decoder
import org.http4s.{Method, Request}
import org.http4s.client.Client
import org.http4s.circe.CirceEntityDecoder._
import fs2.{Chunk, Pull, Stream}

class ExplorerService(httpClient: Client[IO], conf: ExplorerConfig) {

  private val btcBasePath = "/blockchain/v3/btc"

  def getCurrentBlock: IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"$btcBasePath/blocks/current"))

  def getBlock(hash: BlockHash): IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"$btcBasePath/blocks/$hash"))

  def getBlock(height: BlockHeight): IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"$btcBasePath/blocks/$height"))

  def getTransactions(
      addresses: Seq[String],
      blockHash: Option[BlockHash]
  )(implicit
      ce: ConcurrentEffect[IO],
      t: Timer[IO]
  ): Stream[IO, Transaction] = {
    if (addresses.nonEmpty)
      Stream
        .emits(addresses)
        .chunkLimit(conf.addressesSize)
        .map { chunk =>
          fetchPaginatedTransactions(httpClient, chunk.toList, blockHash).stream
            .flatMap(res => Stream.emits(res.txs))
            .timeout(conf.timeout)
        }
        .parJoinUnbounded
    else
      Stream.empty
  }

  private def GetOperationsRequest(addresses: Seq[String], blockHash: Option[BlockHash]) = {
    val baseUri =
      conf.uri
        .withPath(s"$btcBasePath/addresses/${addresses.mkString(",")}/transactions")
        .withQueryParam("no_token", true)
        .withQueryParam("batch_size", conf.txsBatchSize)

    Request[IO](
      Method.GET,
      blockHash match {
        case Some(value) => baseUri.withQueryParam("block_hash", value)
        case None        => baseUri
      }
    )

  }

  private def fetchPaginatedTransactions(
      client: Client[IO],
      addresses: Seq[String],
      blockHash: Option[BlockHash]
  )(implicit
      decoder: Decoder[GetTransactionsResponse]
  ): Pull[IO, GetTransactionsResponse, Unit] =
    Pull
      .eval(
        client.expect[GetTransactionsResponse](
          GetOperationsRequest(addresses, blockHash)
        )
      )
      .flatMap { res =>
        if (res.truncated) {
          // The explorer returns batch_size + 1 tx.
          // So, we need to drop the last tx to avoid having duplicate txs.
          val fixedRes      = res.copy(txs = res.txs.dropRight(1))
          val lastBlockHash = res.txs.lastOption.map(_.block.hash)
          Pull.output(Chunk(fixedRes)) >>
            fetchPaginatedTransactions(client, addresses, lastBlockHash)
        } else {
          Pull.output(Chunk(res))
        }
      }

}
