package co.ledger.lama.bitcoin.worker.mock.faulty

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.models.worker
import co.ledger.lama.bitcoin.common.services.ExplorerClient

class FaultyExplorerClientService extends ExplorerClient {
  def getCurrentBlock: IO[worker.Block] = IO.raiseError(new Exception)

  def getBlock(hash: String): IO[Option[worker.Block]] = ???

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): fs2.Stream[IO, worker.ConfirmedTransaction] = ???
}
