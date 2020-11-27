package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.services.mocks.{
  InterpreterClientServiceMock,
  KeychainClientServiceMock
}
import co.ledger.lama.bitcoin.worker.faultymocks.FaultyExplorerClientServiceMock
import co.ledger.lama.bitcoin.worker.services.CursorStateService
import co.ledger.lama.common.models.Status.SyncFailed
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class WorkerWithFaultyExplorerIT extends WorkerResources with AnyFlatSpecLike with Matchers {

  IOAssertion {
    val keychainClient     = new KeychainClientServiceMock
    val explorerClient     = new FaultyExplorerClientServiceMock
    val interpreterClient  = new InterpreterClientServiceMock
    val cursorStateService = new CursorStateService(explorerClient, interpreterClient)

    getLastExecution(keychainClient, explorerClient, interpreterClient, cursorStateService)
      .map { reportableEvent =>
        it should "report a failed synchronization because it cannot find confirmed transactions" in {
          reportableEvent.map(_.status) shouldBe Some(SyncFailed)
          reportableEvent.map(_.payload.data.toString).getOrElse("") should include(
            "Failed to get confirmed transactions for this addresses"
          )
        }
      }
  }
}
