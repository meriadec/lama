package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.services.ExplorerClientService
import co.ledger.lama.bitcoin.worker.mock.InterpreterClientServiceMock
import co.ledger.lama.bitcoin.worker.mock.faulty.FaultyKeychainClientServiceMock
import co.ledger.lama.bitcoin.worker.services.CursorStateService
import co.ledger.lama.common.models.Status.SyncFailed
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class WorkerWithFaultyKeychainIT extends WorkerResources with AnyFlatSpecLike with Matchers {

  IOAssertion {
    resources.use { case (_, httpClient) =>
      val keychainClient     = new FaultyKeychainClientServiceMock
      val explorerClient     = new ExplorerClientService(httpClient, conf.explorer)
      val interpreterClient  = new InterpreterClientServiceMock
      val cursorStateService = new CursorStateService(explorerClient, interpreterClient)

      getLastExecution(keychainClient, explorerClient, interpreterClient, cursorStateService)
        .map { reportableEvent =>
          it should "report a failed synchronization because of failure to get keychain informations" in {
            reportableEvent.map(_.status) shouldBe Some(SyncFailed)
            reportableEvent.map(_.payload.data.toString).getOrElse("") should include(
              "Failed to get keychain informations for this keychain"
            )
          }
        }
    }
  }
}
