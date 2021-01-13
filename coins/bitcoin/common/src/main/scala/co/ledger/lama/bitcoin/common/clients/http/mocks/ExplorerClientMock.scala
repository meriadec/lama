package co.ledger.lama.bitcoin.common.clients.http.mocks

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient
import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient.Address
import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.common.models.transactor.FeeInfo
import fs2.Stream

class ExplorerClientMock(
    blockchain: Map[Address, List[ConfirmedTransaction]] = Map.empty,
    mempool: Map[Address, List[UnconfirmedTransaction]] = Map.empty
) extends ExplorerClient {

  def getCurrentBlock: IO[Block] = ???

  def getBlock(hash: String): IO[Option[Block]] = ???

  def getBlock(height: Long): IO[Block] = ???

  def getUnconfirmedTransactions(
      addresses: Set[Address]
  )(implicit cs: ContextShift[IO], t: Timer[IO]): Stream[IO, UnconfirmedTransaction] =
    Stream.emits(addresses.flatMap(mempool.get).flatten.toSeq)

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): fs2.Stream[IO, ConfirmedTransaction] =
    Stream.emits(addresses.flatMap(blockchain.get).flatten)

  def getSmartFees: IO[FeeInfo] = {
    IO(FeeInfo(500, 1000, 1500))
  }

  override def broadcastTransaction(tx: String): IO[String] = ???

}
