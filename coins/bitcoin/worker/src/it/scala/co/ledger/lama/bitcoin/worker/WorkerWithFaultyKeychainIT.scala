package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.services.ExplorerClientService
import co.ledger.lama.bitcoin.common.services.mocks.InterpreterClientServiceMock
import co.ledger.lama.bitcoin.worker.faultymocks.FaultyKeychainClientServiceMock
import co.ledger.lama.bitcoin.worker.models.PayloadData
import co.ledger.lama.bitcoin.worker.services.CursorStateService
import co.ledger.lama.common.models.Status.SyncFailed
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class WorkerWithFaultyKeychainIT extends WorkerResources with AnyFlatSpecLike with Matchers {

  IOAssertion {
    Clients.htt4s.use { httpClient =>
      val keychainClient     = new FaultyKeychainClientServiceMock
      val explorerClient     = new ExplorerClientService(httpClient, conf.explorer)
      val interpreterClient  = new InterpreterClientServiceMock
      val cursorStateService = new CursorStateService(explorerClient, interpreterClient)

      runWorkerWorkflow(keychainClient, explorerClient, interpreterClient, cursorStateService)
        .map { reportableEvent =>
          it should "report a failed synchronization because of failure to get keychain informations" in {
            reportableEvent.map(_.status) shouldBe Some(SyncFailed)
            reportableEvent
              .flatMap(_.payload.data.as[PayloadData].toOption)
              .flatMap(_.errorMessage)
              .getOrElse("") should include(
              "Keychain service - Failed to get keychain informations for this keychain"
            )
          }
        }
    }
  }
}
