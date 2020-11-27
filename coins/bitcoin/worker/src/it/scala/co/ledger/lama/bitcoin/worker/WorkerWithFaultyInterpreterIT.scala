package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.services.ExplorerClientService
import co.ledger.lama.bitcoin.common.services.mocks.KeychainClientServiceMock
import co.ledger.lama.bitcoin.worker.faultymocks.FaultyInterpreterClientServiceMock
import co.ledger.lama.bitcoin.worker.services.CursorStateService
import co.ledger.lama.common.models.Status.SyncFailed
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class WorkerWithFaultyInterpreterIT extends WorkerResources with AnyFlatSpecLike with Matchers {

  IOAssertion {
    Clients.htt4s.use { httpClient =>
      val keychainClient     = new KeychainClientServiceMock
      val explorerClient     = new ExplorerClientService(httpClient, conf.explorer)
      val interpreterClient  = new FaultyInterpreterClientServiceMock
      val cursorStateService = new CursorStateService(explorerClient, interpreterClient)

      runWorkerWorkflow(keychainClient, explorerClient, interpreterClient, cursorStateService)
        .map { reportableEvent =>
          it should "report a failed synchronization because of a save transaction error" in {
            reportableEvent.map(_.status) shouldBe Some(SyncFailed)
            reportableEvent.map(_.payload.data.toString).getOrElse("") should include(
              "Failed to save transactions for this account"
            )
          }
        }
    }
  }
}
