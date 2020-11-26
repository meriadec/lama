package co.ledger.lama.bitcoin.worker.mock.faulty

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.models.worker
import co.ledger.lama.bitcoin.common.services.ExplorerClient

class ExplorerClientServiceNoBlockMock extends ExplorerClient {
  def getCurrentBlock: IO[worker.Block] = IO.pure(
    worker.Block(
      hash = "0000000000000000001376cb2f6f64d523ff7018ea70a107671bce82794ca41d",
      height = 611120,
      time = java.time.Instant.now()
    )
  )

  def getBlock(hash: String): IO[Option[worker.Block]] = IO.raiseError(new Exception)

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): fs2.Stream[IO, worker.ConfirmedTransaction] = ???
}

class ExplorerClientServiceNoConfirmedTransaction extends ExplorerClient {
  def getCurrentBlock: IO[worker.Block] = IO.pure(
    worker.Block(
      hash = "0000000000000000001376cb2f6f64d523ff7018ea70a107671bce82794ca41d",
      height = 611120,
      time = java.time.Instant.now()
    )
  )

  def getBlock(hash: String): IO[Option[worker.Block]] = IO.pure(
    Some(
      worker.Block(
        hash = "0000000000000000001376cb2f6f64d523ff7018ea70a107671bce82794ca41d",
        height = 611120,
        time = java.time.Instant.now()
      )
    )
  )

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): fs2.Stream[IO, worker.ConfirmedTransaction] =
    fs2.Stream.raiseError[IO](new Exception("oh noooes"))
}
