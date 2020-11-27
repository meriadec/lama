package co.ledger.lama.bitcoin.worker

import java.time.Instant

import co.ledger.lama.bitcoin.common.models.worker.Block
import co.ledger.lama.bitcoin.common.services.ExplorerClientService
import co.ledger.lama.bitcoin.worker.mock.{InterpreterClientServiceMock, KeychainClientServiceMock}
import co.ledger.lama.bitcoin.worker.models.PayloadData
import co.ledger.lama.bitcoin.worker.services.CursorStateService
import co.ledger.lama.common.utils.IOAssertion
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class WorkerIT extends WorkerResources with AnyFlatSpecLike with Matchers {

  IOAssertion {
    resources.use { case (_, httpClient) =>
      val keychainClient     = new KeychainClientServiceMock
      val explorerClient     = new ExplorerClientService(httpClient, conf.explorer)
      val interpreterClient  = new InterpreterClientServiceMock
      val cursorStateService = new CursorStateService(explorerClient, interpreterClient)

      getLastExecution(keychainClient, explorerClient, interpreterClient, cursorStateService)
        .map { reportableEvent =>
          it should "have 35 used addresses for the account" in {
            keychainClient.usedAddresses.size shouldBe 35
          }

          val expectedTxsSize         = 73
          val expectedLastBlockHeight = 644553L

          it should s"have synchronized $expectedTxsSize txs with last blockHeight=$expectedLastBlockHeight" in {
            interpreterClient.savedTransactions
              .getOrElse(
                account.id,
                List.empty
              )
              .distinctBy(_.hash) should have size expectedTxsSize

            reportableEvent shouldBe Some(
              registeredEvent.reportSuccess(
                PayloadData(
                  lastBlock = Some(
                    Block(
                      "0000000000000000000c44bf26af3b5b3c97e5aed67407fd551a90bc175de5a0",
                      expectedLastBlockHeight,
                      Instant.parse("2020-08-20T13:01:16Z")
                    )
                  )
                ).asJson
              )
            )
          }
        }
    }
  }
}
