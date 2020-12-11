package co.ledger.lama.bitcoin.common.grpc

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.config.ExplorerConfig
import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.common.models.transactor.FeeInfo
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.Coin
import co.ledger.lama.common.models.Coin.{Btc, BtcTestnet}
import co.ledger.lama.common.utils.IOUtils
import fs2.{Chunk, Pull, Stream}
import io.circe.{Decoder, Json}
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.{Method, Request}

trait ExplorerClientService {

  def getCurrentBlock: IO[Block]

  def getBlock(hash: String): IO[Option[Block]]

  def getBlock(height: Long): IO[Block]

  def getConfirmedTransactions(
      addresses: Seq[String],
      blockHash: Option[String]
  )(implicit cs: ContextShift[IO], t: Timer[IO]): Stream[IO, ConfirmedTransaction]

  def getSmartFees: IO[FeeInfo]

  def broadcastTransaction(tx: String): IO[String]

}

class ExplorerV3ClientService(httpClient: Client[IO], conf: ExplorerConfig, coin: Coin)
    extends ExplorerClientService
    with IOLogging {

  private val coinBasePath = coin match {
    case Btc        => "/blockchain/v3/btc"
    case BtcTestnet => "/blockchain/v3/btc_testnet"
  }

  def getCurrentBlock: IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"$coinBasePath/blocks/current"))

  def getBlock(hash: String): IO[Option[Block]] =
    httpClient
      .expect[List[Block]](conf.uri.withPath(s"$coinBasePath/blocks/$hash"))
      .map(_.headOption)

  def getBlock(height: Long): IO[Block] =
    httpClient.expect[Block](conf.uri.withPath(s"$coinBasePath/blocks/$height"))

  def getConfirmedTransactions(
      addresses: Seq[String],
      blockHash: Option[String]
  )(implicit cs: ContextShift[IO], t: Timer[IO]): Stream[IO, ConfirmedTransaction] =
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

  def getSmartFees: IO[FeeInfo] = {

    for {

      json <- httpClient.expect[Json](
        conf.uri.withPath(s"$coinBasePath/fees")
      )

      feeInfo <- IO.fromOption {
        json.asObject
          .flatMap { o =>
            val sortedFees = o
              .filterKeys(_.toIntOption.isDefined)
              .toList
              .flatMap { case (_, v) =>
                v.asNumber.flatMap(_.toLong)
              }
              .sorted

            sortedFees match {
              case slow :: normal :: fast :: Nil => Some(FeeInfo(slow, normal, fast))
              case _                             => None
            }
          }
      }(
        new Exception(
          s"Explorer.fees did not conform to expected format. payload :\n${json.spaces2SortKeys}"
        )
      )

    } yield feeInfo

  }

  def broadcastTransaction(tx: String): IO[String] =
    httpClient
      .expect[SendTransactionResult](
        Request[IO](
          Method.POST,
          conf.uri.withPath(s"$coinBasePath/transactions/send")
        ).withEntity(Json.obj("tx" -> Json.fromString(tx)))
      )
      .map(_.result)

  private def GetOperationsRequest(addresses: Seq[String], blockHash: Option[String]) = {
    val baseUri =
      conf.uri
        .withPath(s"$coinBasePath/addresses/${addresses.mkString(",")}/transactions")
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
          IOUtils.retry(
            httpClient
              .expect[GetTransactionsResponse](
                GetOperationsRequest(addresses, blockHash)
              )
              .timeout(conf.timeout)
          )
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
