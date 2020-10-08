package co.ledger.lama.service

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.common.utils.IOAssertion
import co.ledger.lama.service.ConfigSpec.ConfigSpec
import co.ledger.lama.service.models.{
  AccountRegistered,
  GetAccountManagerInfoResult,
  GetOperationsResult
}
import org.http4s._
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource
import cats.implicits._
import co.ledger.lama.common.utils.ResourceUtils.RetryPolicy
import fs2.{Chunk, Pull, Stream}
import org.http4s.client.Client

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

class ServiceSpec extends AnyFlatSpecLike with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf      = ConfigSource.default.loadOrThrow[ConfigSpec]
  val serverUrl = s"http://${conf.server.host}:${conf.server.port}"

  "Lama service" should "handle all kind of operations" in IOAssertion {
    val accountRegisteringRequest =
      Request[IO](method = Method.POST, uri = Uri.unsafeFromString(s"$serverUrl/accounts"))

    def getAccountRequest(accountId: UUID) =
      Request[IO](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"$serverUrl/accounts/$accountId")
      )

    def getOperationsRequest(accountId: UUID, offset: Int, limit: Int) =
      Request[IO](
        method = Method.GET,
        uri =
          Uri.unsafeFromString(s"$serverUrl/accounts/$accountId/utxo?limit=$limit&offset=$offset")
      )

    def removeAccountRequest(accountId: UUID) =
      Request[IO](
        method = Method.DELETE,
        uri = Uri.unsafeFromString(s"$serverUrl/accounts/$accountId")
      )

    def fetchPaginatedOperations(
        client: Client[IO],
        accountId: UUID,
        offset: Int = 0,
        limit: Int = 20
    ): Pull[IO, GetOperationsResult, Unit] =
      Pull
        .eval(
          getOperations(
            client.expect[GetOperationsResult](
              getOperationsRequest(accountId, offset, limit)
            )
          )
        )
        .flatMap { res =>
          if (res.truncated) {
            Pull.output(Chunk(res)) >>
              fetchPaginatedOperations(client, accountId, offset + limit, limit)
          } else {
            Pull.output(Chunk(res))
          }
        }

    def getOperations(io: IO[GetOperationsResult]): IO[GetOperationsResult] = {
      Stream
        .eval(io.flatMap { res =>
          if (res.operations.nonEmpty)
            IO.raiseError(new Exception())
          else IO.pure(res)
        })
        .attempts(RetryPolicy.linear())
        .collectFirst {
          case Right(res) => res
        }
        .compile
        .lastOrError
    }

    BlazeClientBuilder[IO](global).resource
      .use { client =>
        conf.accounts.traverse { account =>
          for {
            accountRegistered <-
              client.expect[AccountRegistered](accountRegisteringRequest.withEntity(account))

            account <- client.expect[GetAccountManagerInfoResult](
              getAccountRequest(accountRegistered.accountId)
            )

            operations <- fetchPaginatedOperations(
              client,
              accountRegistered.accountId
            ).stream.compile.lastOrError

            accountDeletedStatus <- client.status(removeAccountRequest(accountRegistered.accountId))
          } yield {
            accountDeletedStatus.code shouldBe 200
            operations.operations.nonEmpty shouldBe true
            account.accountId shouldBe accountRegistered.accountId
          }
        }
      }
  }
}
