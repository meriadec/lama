package co.ledger.lama.bitcoin.common.grpc.mocks

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.models.transactor.FeeInfo
import co.ledger.lama.bitcoin.common.models.worker
import co.ledger.lama.bitcoin.common.grpc.ExplorerClientService

class ExplorerClientServiceMock extends ExplorerClientService {

  def getCurrentBlock: IO[worker.Block] = ???

  def getBlock(hash: String): IO[Option[worker.Block]] = ???

  def getBlock(height: Long): IO[worker.Block] = ???

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): fs2.Stream[IO, worker.ConfirmedTransaction] = ???

  def getSmartFees: IO[FeeInfo] = {
    IO(FeeInfo(500, 1000, 1500))
  }

  override def broadcastTransaction(tx: String): IO[String] = ???

}
