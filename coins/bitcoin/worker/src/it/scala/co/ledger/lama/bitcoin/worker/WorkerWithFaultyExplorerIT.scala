package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.services.mocks.{
  InterpreterClientServiceMock,
  KeychainClientServiceMock
}
import co.ledger.lama.bitcoin.worker.faultymocks.{FaultyBase, FaultyExplorerClientServiceMock}
import co.ledger.lama.bitcoin.worker.models.PayloadData
import co.ledger.lama.bitcoin.worker.services.CursorStateService
import co.ledger.lama.common.models.Status.SyncFailed
import co.ledger.lama.common.utils.IOAssertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class WorkerWithFaultyExplorerIT
    extends WorkerResources
    with AnyFlatSpecLike
    with Matchers
    with FaultyBase {

  IOAssertion {
    val keychainClient     = new KeychainClientServiceMock
    val explorerClient     = new FaultyExplorerClientServiceMock
    val interpreterClient  = new InterpreterClientServiceMock
    val cursorStateService = new CursorStateService(explorerClient, interpreterClient)

    runWorkerWorkflow(keychainClient, explorerClient, interpreterClient, cursorStateService)
      .map { reportableEvent =>
        it should "report a failed synchronization due to faulty explorer" in {
          reportableEvent.map(_.status) shouldBe Some(SyncFailed)
          reportableEvent
            .flatMap(_.payload.data.as[PayloadData].toOption)
            .flatMap(_.errorMessage)
            .getOrElse("") should include(
            "Explorer service - Failed to get confirmed transactions for this addresses"
          )
        }
      }
  }
}
