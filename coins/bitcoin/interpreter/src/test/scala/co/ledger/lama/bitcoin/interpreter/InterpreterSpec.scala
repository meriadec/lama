package co.ledger.lama.bitcoin.interpreter

import java.util.UUID

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.bitcoin.interpreter.protobuf.{
  Block,
  DeleteTransactionsRequest,
  GetOperationsRequest,
  SaveTransactionsRequest,
  SortingOrder,
  Transaction
}
import co.ledger.lama.common.utils.{IOAssertion, UuidUtils}
import io.grpc.Metadata
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

class InterpreterSpec extends AnyFlatSpecLike with Matchers {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  val saveTransactionRequest = new SaveTransactionsRequest(
    accountId = UuidUtils.uuidToBytes(UUID.randomUUID()),
    transactions = Seq(
      new Transaction(
        hash = "7cb3aacc-2aea-4152-8b53-3431f4daece5",
        fees = "1000",
        block = Some(Block(height = 0))
      ),
      new Transaction(
        hash = "4c93a655-437a-495b-9c51-926ad90852ba",
        fees = "2000",
        block = Some(Block(height = 1))
      ),
      new Transaction(
        hash = "958f7014-4cb7-400a-92cf-1a8540a186bc",
        fees = "3000",
        block = Some(Block(height = 2))
      )
    )
  )
  "The interpreter" should "save transactions and fetch those transactions" in IOAssertion {
    val interpreter: Interpreter = new FakeInterpreter()

    for {
      _ <- interpreter.saveTransactions(saveTransactionRequest, new Metadata())
      transactions <-
        interpreter
          .getOperations(
            new GetOperationsRequest(saveTransactionRequest.accountId),
            new Metadata()
          )
          .map(_.operations)
    } yield {
      transactions should have size 3
    }
  }

  it should "be able to delete transactions" in IOAssertion {
    val interpreter: Interpreter = new FakeInterpreter()

    for {
      _ <- interpreter.saveTransactions(saveTransactionRequest, new Metadata())
      transactions <-
        interpreter
          .getOperations(
            new GetOperationsRequest(saveTransactionRequest.accountId),
            new Metadata()
          )
          .map(_.operations)

      _ <- interpreter.deleteTransactions(
        new DeleteTransactionsRequest(saveTransactionRequest.accountId),
        new Metadata()
      )
      deletedTransactions <-
        interpreter
          .getOperations(
            new GetOperationsRequest(saveTransactionRequest.accountId),
            new Metadata()
          )
          .map(_.operations)
    } yield (
      transactions should have size 3,
      deletedTransactions should have size 0
    )
  }

  it should "order the transactions ASC" in IOAssertion {
    val interpreter: Interpreter = new FakeInterpreter()

    for {
      _ <- interpreter.saveTransactions(saveTransactionRequest, new Metadata())
      transactions <-
        interpreter
          .getOperations(
            new GetOperationsRequest(saveTransactionRequest.accountId, sort = SortingOrder.ASC),
            new Metadata()
          )
          .map(_.operations)
    } yield transactions.slice(0, 3).map(_.hash) shouldBe List(
      "4c93a655-437a-495b-9c51-926ad90852ba",
      "7cb3aacc-2aea-4152-8b53-3431f4daece5",
      "958f7014-4cb7-400a-92cf-1a8540a186bc"
    )
  }

  it should "limit the number of transactions" in IOAssertion {
    val interpreter: Interpreter = new FakeInterpreter()

    for {
      _ <- interpreter.saveTransactions(saveTransactionRequest, new Metadata())
      transactions <-
        interpreter
          .getOperations(
            new GetOperationsRequest(saveTransactionRequest.accountId, limit = 1),
            new Metadata()
          )
          .map(_.operations)
    } yield transactions should have size 1
  }

  it should "offset the transactions" in IOAssertion {
    val interpreter: Interpreter = new FakeInterpreter()

    for {
      _ <- interpreter.saveTransactions(saveTransactionRequest, new Metadata())
      transactions <-
        interpreter
          .getOperations(
            new GetOperationsRequest(saveTransactionRequest.accountId, offset = 1),
            new Metadata()
          )
          .map(_.operations)
    } yield transactions should have size 2
  }

  it should "indicate that the transactions are truncated" in IOAssertion {
    val interpreter: Interpreter = new FakeInterpreter()

    for {
      _ <- interpreter.saveTransactions(saveTransactionRequest, new Metadata())
      truncated <-
        interpreter
          .getOperations(
            new GetOperationsRequest(saveTransactionRequest.accountId, limit = 1),
            new Metadata()
          )
          .map(_.truncated)
    } yield truncated shouldBe true
  }

  it should "indicate that the transactions are not truncated" in IOAssertion {
    val interpreter: Interpreter = new FakeInterpreter()

    for {
      _ <- interpreter.saveTransactions(saveTransactionRequest, new Metadata())
      truncated <-
        interpreter
          .getOperations(
            new GetOperationsRequest(saveTransactionRequest.accountId),
            new Metadata()
          )
          .map(_.truncated)
    } yield truncated shouldBe false
  }
}
