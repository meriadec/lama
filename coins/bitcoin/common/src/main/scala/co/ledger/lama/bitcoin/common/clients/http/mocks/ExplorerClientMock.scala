package co.ledger.lama.bitcoin.common.clients.http.mocks

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient
import co.ledger.lama.bitcoin.common.models.explorer._
import co.ledger.lama.bitcoin.common.models.transactor.FeeInfo

class ExplorerClientMock extends ExplorerClient {

  def getCurrentBlock: IO[Block] = ???

  def getBlock(hash: String): IO[Option[Block]] = ???

  def getBlock(height: Long): IO[Block] = ???

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): fs2.Stream[IO, ConfirmedTransaction] = ???

  def getSmartFees: IO[FeeInfo] = {
    IO(FeeInfo(500, 1000, 1500))
  }

  override def broadcastTransaction(tx: String): IO[String] = ???

}
