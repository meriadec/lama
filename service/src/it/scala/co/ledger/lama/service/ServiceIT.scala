package co.ledger.lama.service

import java.util.UUID

import cats.effect.{ContextShift, IO, Resource, Timer}
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
import io.circe.parser._

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

class ServiceIT extends AnyFlatSpecLike with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val conf      = ConfigSource.default.loadOrThrow[ConfigSpec]
  val serverUrl = s"http://${conf.server.host}:${conf.server.port}"

  val accountsRes: Resource[IO, List[TestAccount]] =
    Resource
      .fromAutoCloseable(IO(scala.io.Source.fromResource("test-accounts.json")))
      .evalMap { bf =>
        IO.fromEither(decode[List[TestAccount]](bf.getLines().mkString))
      }

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
      uri = Uri.unsafeFromString(
        s"$serverUrl/accounts/$accountId/operations?limit=$limit&offset=$offset"
      )
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
        if (res.operations.isEmpty)
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

  IOAssertion {
    accountsRes
      .parZip(BlazeClientBuilder[IO](global).resource)
      .use {
        case (accounts, client) =>
          accounts.traverse { account =>
            for {
              accountRegistered <- client.expect[AccountRegistered](
                accountRegisteringRequest.withEntity(account.registerRequest)
              )

              accountResult <- client.expect[GetAccountManagerInfoResult](
                getAccountRequest(accountRegistered.accountId)
              )

              operations <- fetchPaginatedOperations(
                client,
                accountRegistered.accountId
              ).stream.compile.toList.map(_.flatMap(_.operations))

              accountDeletedStatus <-
                client.status(removeAccountRequest(accountRegistered.accountId))
            } yield {
              val accountStr =
                s"Account: ${accountResult.accountId} (${account.registerRequest.scheme})"

              accountStr should "registered" in {
                accountResult.accountId shouldBe accountRegistered.accountId
              }

              // TODO: test account info (balance, ...)

              // TODO: test utxos

              it should s"have ${operations.size} operations" in {
                operations.size shouldBe account.expected.opsSize
              }

              val lastTxHash = operations.maxBy(_.time).hash
              it should s"have fetch operations to last cursor $lastTxHash" in {
                lastTxHash shouldBe account.expected.lastTxHash
              }

              it should "unregistered" in {
                // TODO: test unregister (get ops and account info from removed account = 404)
                accountDeletedStatus.code shouldBe 200
              }

            }
          }
      }
  }
}
