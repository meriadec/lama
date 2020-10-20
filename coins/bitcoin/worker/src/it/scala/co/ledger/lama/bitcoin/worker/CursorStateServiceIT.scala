package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.lama.bitcoin.common.models.explorer.Block
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.services.{CursorStateService, ExplorerService}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.utils.IOAssertion
import org.http4s.client.Client
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource

import scala.concurrent.ExecutionContext

class CursorStateServiceIT extends AnyFlatSpecLike with Matchers with IOLogging {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf: Config = ConfigSource.default.loadOrThrow[Config]

  val resources: Resource[IO, Client[IO]] = Clients.htt4s

  it should "get the last valid cursor state" in IOAssertion {
    resources.use { httpClient =>
      val explorerService    = new ExplorerService(httpClient, conf.explorer)
      val interpreterService = new InterpreterServiceMock
      val cursorStateService = new CursorStateService(explorerService, interpreterService)

      val accountId = UUID.randomUUID()

      val lastValidHash   = "0x00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608379"
      val lastValidHeight = 559033L
      val invalidHash     = "0x00000000000000000008c76a28e115319fb747eb29a7e0794526d0fe47608376"

      for {
        block <- cursorStateService.getLastValidState(
          accountId,
          Block(invalidHash, 0L, "time")
        )
      } yield {
        block.hash shouldBe lastValidHash
        block.height shouldBe lastValidHeight
      }

    }
  }

}
