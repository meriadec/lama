package co.ledger.lama.bitcoin.worker.services

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.worker.config.ExplorerConfig
import co.ledger.lama.bitcoin.worker.models.GetTransactionsResponse
import co.ledger.lama.common.logging.IOLogging
import io.circe.Decoder
import org.http4s.{Method, Request}
import org.http4s.client.Client
import org.http4s.circe.CirceEntityDecoder._
import fs2.{Chunk, Pull, Stream}

class ExplorerService(httpClient: Client[IO], conf: ExplorerConfig) extends IOLogging {

  private val btcBasePath = "/blockchain/v3/btc"

  def getCurrentBlock: IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"$btcBasePath/blocks/current"))

  def getBlock(hash: String): IO[Option[Block]] =
    httpClient
      .expect[List[Block]](conf.uri.withPath(s"$btcBasePath/blocks/$hash"))
      .map(_.headOption)

  def getBlock(height: Long): IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"$btcBasePath/blocks/$height"))

  def getConfirmedTransactions(
      addresses: Seq[String],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): Stream[IO, ConfirmedTransaction] =
    Stream
      .emits(addresses)
      .chunkLimit(conf.addressesSize)
      .map { chunk =>
        fetchPaginatedTransactions(chunk.toList, blockHash).stream
          .flatMap { res =>
            // The explorer v3 returns also unconfirmed txs, so we need to remove it
            val confirmedTxs = res.txs.collect {
              case confirmedTx: ConfirmedTransaction => confirmedTx
            }
            Stream.emits(confirmedTxs)
          }
      }
      .parJoinUnbounded

  private def GetOperationsRequest(addresses: Seq[String], blockHash: Option[String]) = {
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
      addresses: Seq[String],
      blockHash: Option[String]
  )(implicit
      cs: ContextShift[IO],
      t: Timer[IO],
      decoder: Decoder[GetTransactionsResponse]
  ): Pull[IO, GetTransactionsResponse, Unit] =
    Pull
      .eval(
        log.info(
          s"Getting txs with block_hash=$blockHash for addresses: ${addresses.mkString(",")}"
        ) *>
          httpClient
            .expect[GetTransactionsResponse](
              GetOperationsRequest(addresses, blockHash)
            )
            .timeout(conf.timeout)
      )
      .flatMap { res =>
        if (res.truncated) {
          // The explorer returns batch_size + 1 tx.
          // So, we need to drop the last tx to avoid having duplicate txs.
          val fixedRes = res.copy(txs = res.txs.dropRight(1))

          // Txs are not sorted per page,
          // so we need get only confirmed txs and
          // get the most recent fetched block hash for the next cursor
          val lastBlockHash =
            res.txs
              .collect {
                case confirmedTx: ConfirmedTransaction => confirmedTx
              }
              .maxByOption(_.block.time)
              .map(_.block.hash)

          Pull.output(Chunk(fixedRes)) >>
            fetchPaginatedTransactions(addresses, lastBlockHash)
        } else {
          Pull.output(Chunk(res))
        }
      }

}
