package co.ledger.lama.bitcoin.common.services

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.config.ExplorerConfig
import co.ledger.lama.bitcoin.common.models.worker._
import co.ledger.lama.bitcoin.common.models.explorer.GetTransactionsResponse
import co.ledger.lama.common.logging.IOLogging
import fs2.{Chunk, Pull, Stream}
import io.circe.Decoder
import org.http4s.{Method, Request}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client

trait ExplorerClient {
  def getCurrentBlock: IO[Block]
  def getBlock(hash: String): IO[Option[Block]]
  def getConfirmedTransactions(
      addresses: Seq[String],
      blockHash: Option[String]
  )(implicit cs: ContextShift[IO], t: Timer[IO]): Stream[IO, ConfirmedTransaction]
}

class ExplorerClientService(httpClient: Client[IO], conf: ExplorerConfig)
    extends ExplorerClient
    with IOLogging {

  private val btcBasePath = "/blockchain/v3/btc"

  def getCurrentBlock: IO[Block] =
    httpClient
      .expect[Block](conf.uri.withPath(s"$btcBasePath/blocks/current"))
      .handleErrorWith(err =>
        IO.raiseError(
          ExplorerServiceError(
            rootCause = err,
            errorMessage = "Failed to get current block"
          )
        )
      )

  def getBlock(hash: String): IO[Option[Block]] =
    httpClient
      .expect[List[Block]](conf.uri.withPath(s"$btcBasePath/blocks/$hash"))
      .map(_.headOption)
      .handleErrorWith(err =>
        IO.raiseError(
          ExplorerServiceError(
            rootCause = err,
            errorMessage = s"Failed to get a block for this hash $hash"
          )
        )
      )

  def getBlock(height: Long): IO[Block] =
    httpClient
      .expect[Block](conf.uri.withPath(s"$btcBasePath/blocks/$height"))
      .handleErrorWith(err =>
        IO.raiseError(
          ExplorerServiceError(
            rootCause = err,
            errorMessage = s"Failed to get a block for this height: $height"
          )
        )
      )

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
            val confirmedTxs = res.txs.collect { case confirmedTx: ConfirmedTransaction =>
              confirmedTx
            }
            Stream.emits(confirmedTxs)
          }
      }
      .parJoinUnbounded
      .handleErrorWith(err =>
        Stream.raiseError[IO](
          ExplorerServiceError(
            rootCause = err,
            errorMessage = s"Failed to get confirmed transactions for this addresses: $addresses"
          )
        )
      )

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
              .collect { case confirmedTx: ConfirmedTransaction =>
                confirmedTx
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
