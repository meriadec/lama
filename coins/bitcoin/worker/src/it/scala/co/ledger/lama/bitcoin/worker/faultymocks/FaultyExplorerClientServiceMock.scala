package co.ledger.lama.bitcoin.worker.faultymocks

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.common.models.worker
import co.ledger.lama.bitcoin.common.models.worker.ExplorerServiceError
import co.ledger.lama.bitcoin.common.services.ExplorerClient

class FaultyExplorerClientServiceMock extends ExplorerClient with FaultyBase {

  def getCurrentBlock: IO[worker.Block] = IO.raiseError(
    ExplorerServiceError(
      rootCause = fakeCause,
      message = s"Failed to get current block"
    )
  )

  def getBlock(hash: String): IO[Option[worker.Block]] = IO.raiseError(
    ExplorerServiceError(
      rootCause = fakeCause,
      message = s"Failed to get a block for this hash $hash"
    )
  )

  def getConfirmedTransactions(addresses: Seq[String], blockHash: Option[String])(implicit
      cs: ContextShift[IO],
      t: Timer[IO]
  ): fs2.Stream[IO, worker.ConfirmedTransaction] = fs2.Stream.raiseError[IO](
    ExplorerServiceError(
      rootCause = fakeCause,
      message = s"Failed to get confirmed transactions for this addresses: $addresses"
    )
  )
}
