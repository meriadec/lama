package co.ledger.lama.service

import java.util.UUID

import cats.effect.{ContextShift, IO, Resource, Timer}
import co.ledger.lama.common.utils.{IOAssertion, IOUtils}
import co.ledger.lama.service.ConfigSpec.ConfigSpec
import co.ledger.lama.service.models.{
  AccountInfo,
  AccountRegistered,
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
import co.ledger.lama.common.models.Status.{Deleted, Published, Registered, Synchronized}
import co.ledger.lama.common.models.Sort
import co.ledger.lama.service.routes.AccountController.UpdateRequest
import io.circe.parser._

import scala.concurrent.ExecutionContext

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

  def accountUpdateRequest(accountId: UUID) =
    Request[IO](method = Method.PUT, uri = Uri.unsafeFromString(s"$serverUrl/accounts/$accountId"))

  def getAccountRequest(accountId: UUID) =
    Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(s"$serverUrl/accounts/$accountId")
    )

  def getOperationsRequest(accountId: UUID, offset: Int, limit: Int, sort: Sort = Sort.Descending) =
    Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(
        s"$serverUrl/accounts/$accountId/operations?limit=$limit&offset=$offset&sort=$sort"
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
      .parZip(BlazeClientBuilder[IO](ExecutionContext.global).resource)
      .use {
        case (accounts, client) =>
          accounts.traverse { account =>
            for {
              // This is retried because sometimes, the keychain service isn't ready when the tests start
              accountRegistered <- IOUtils.retry[AccountRegistered](
                client.expect[AccountRegistered](
                  accountRegisteringRequest.withEntity(account.registerRequest)
                )
              )

              accountInfoAfterRegister <- client.expect[AccountInfo](
                getAccountRequest(accountRegistered.accountId)
              )

              operations <-
                IOUtils
                  .fetchPaginatedItems[GetOperationsResult](
                    (offset, limit) =>
                      IOUtils.retryIf[GetOperationsResult](
                        client.expect[GetOperationsResult](
                          getOperationsRequest(
                            accountRegistered.accountId,
                            offset,
                            limit,
                            Sort.Descending
                          )
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

              accountInfoAfterSync <- client.expect[AccountInfo](
                getAccountRequest(accountRegistered.accountId)
              )

              accountUpdateStatus <- client.status(
                accountUpdateRequest(accountRegistered.accountId).withEntity(UpdateRequest(60))
              )

              accountInfoAfterUpdate <- client.expect[AccountInfo](
                getAccountRequest(accountRegistered.accountId)
              )

              accountDeletedStatus <-
                client.status(removeAccountRequest(accountRegistered.accountId))

              deletedAccountResult <- IOUtils.retryIf[AccountInfo](
                client.expect[AccountInfo](
                  getAccountRequest(accountRegistered.accountId)
                ),
                _.lastSyncEvent.exists(_.status == Deleted)
              )
            } yield {
              val accountStr =
                s"Account: ${accountInfoAfterRegister.accountId} (${account.registerRequest.scheme})"

              accountStr should "be registered" in {
                accountInfoAfterRegister.accountId shouldBe accountRegistered.accountId
                accountInfoAfterRegister.lastSyncEvent
                  .map(_.status) should (contain(Registered) or contain(Published))
              }

              it should s"have a balance of ${account.expected.balance}" in {
                accountInfoAfterSync.balance shouldBe BigInt(account.expected.balance)
                accountInfoAfterSync.lastSyncEvent.map(_.status) should contain(Synchronized)
              }

              it should s"have ${account.expected.utxosSize} utxos in AccountInfo API" in {
                accountInfoAfterSync.utxosCount shouldBe account.expected.utxosSize
              }

              it should s"have ${account.expected.amountReceived} amount received" in {
                accountInfoAfterSync.amountReceived shouldBe account.expected.amountReceived
              }

              it should s"have ${account.expected.amountSpent} amount spent" in {
                accountInfoAfterSync.amountSpent shouldBe account.expected.amountSpent
              }

              it should s"have ${account.expected.utxosSize} utxos in UTXO API" in {
                utxos.size shouldBe account.expected.utxosSize
              }

              it should s"have ${account.expected.opsSize} operations" in {
                operations.size shouldBe account.expected.opsSize
              }

              val lastTxHash = operations.head.hash
              it should s"have fetch operations to last cursor $lastTxHash" in {
                lastTxHash shouldBe account.expected.lastTxHash
              }

              it should "be updated" in {
                accountUpdateStatus.code shouldBe 200
                accountInfoAfterUpdate.syncFrequency shouldBe 60
              }

              it should "be unregistered" in {
                accountDeletedStatus.code shouldBe 200
                deletedAccountResult.lastSyncEvent.map(_.status) should contain(Deleted)
              }
            }
          }
      }
  }
}
