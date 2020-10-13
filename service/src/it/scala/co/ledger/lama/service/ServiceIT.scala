package co.ledger.lama.service

import java.util.UUID

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.lama.common.utils.{IOAssertion, IOUtils}
import co.ledger.lama.service.ConfigSpec.ConfigSpec
import co.ledger.lama.service.models.{
  AccountRegistered,
  GetAccountManagerInfoResult,
  GetOperationsResult,
  GetUTXOsResult
}
import org.http4s._
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.client.blaze.BlazeClientBuilder
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import pureconfig.ConfigSource
import cats.implicits._
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
      .fromAutoCloseable(
        IO(scala.io.Source.fromFile(getClass.getResource("/test-accounts.json").getFile))
      )
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

  def getUTXOsRequest(accountId: UUID, offset: Int, limit: Int) =
    Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(
        s"$serverUrl/accounts/$accountId/utxos?limit=$limit&offset=$offset"
      )
    )

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

              operations <-
                IOUtils
                  .fetchPaginatedItems[GetOperationsResult](
                    (offset, limit) =>
                      IOUtils.retry[GetOperationsResult](
                        client.expect[GetOperationsResult](
                          getOperationsRequest(accountRegistered.accountId, offset, limit)
                        ),
                        _.operations.nonEmpty
                      ),
                    _.truncated,
                    0,
                    20
                  )
                  .stream
                  .compile
                  .toList
                  .map(_.flatMap(_.operations))

              utxos <-
                IOUtils
                  .fetchPaginatedItems[GetUTXOsResult](
                    (offset, limit) =>
                      client.expect[GetUTXOsResult](
                        getUTXOsRequest(accountRegistered.accountId, offset, limit)
                      ),
                    _.truncated,
                    0,
                    20
                  )
                  .stream
                  .compile
                  .toList
                  .map(_.flatMap(_.utxos))

              accountDeletedStatus <-
                client.status(removeAccountRequest(accountRegistered.accountId))

              deletedAccountResult <- IOUtils.retry[GetAccountManagerInfoResult](
                client.expect[GetAccountManagerInfoResult](
                  getAccountRequest(accountRegistered.accountId)
                ),
                _.status.contains("deleted")
              )
            } yield {
              val accountStr =
                s"Account: ${accountResult.accountId} (${account.registerRequest.scheme})"

              accountStr should "registered" in {
                accountResult.accountId shouldBe accountRegistered.accountId
              }

              // TODO: test account info (balance, ...)

              it should s"have ${account.expected.utxosSize} utxos" in {
                utxos.size shouldBe account.expected.utxosSize
              }

              it should s"have ${account.expected.opsSize} operations" in {
                operations.size shouldBe account.expected.opsSize
              }

              val lastTxHash = operations.maxBy(_.time).hash
              it should s"have fetch operations to last cursor $lastTxHash" in {
                lastTxHash shouldBe account.expected.lastTxHash
              }

              it should "unregistered" in {
                accountDeletedStatus.code shouldBe 200
                deletedAccountResult.status shouldBe Some("deleted")
              }
            }
          }
      }
  }
}
