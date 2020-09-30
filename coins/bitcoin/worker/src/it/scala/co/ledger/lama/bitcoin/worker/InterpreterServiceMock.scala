package co.ledger.lama.bitcoin.worker

import java.util.UUID

import cats.effect.IO
import co.ledger.lama.bitcoin.interpreter.protobuf.{AccountAddress, GetTransactionsResult}
import co.ledger.lama.bitcoin.worker.models.explorer.{BlockHeight, Transaction}
import co.ledger.lama.bitcoin.worker.services.{InterpreterService, SortingEnum}
import co.ledger.lama.bitcoin.worker.services.SortingEnum.SortingEnum
import co.ledger.lama.bitcoin.worker.utils.ProtobufUtils.serializeTransaction

import scala.collection.mutable

class InterpreterServiceMock extends InterpreterService {

  var savedTransactions: mutable.Map[UUID, List[Transaction]] = mutable.Map.empty

  def saveTransactions(
      accountId: UUID,
      addresses: List[AccountAddress],
      txs: List[Transaction]
  ): IO[Unit] =
    IO.delay {
      savedTransactions.update(
        accountId,
        savedTransactions.getOrElse(accountId, List.empty) ++ txs
      )
    }

  def removeTransactions(accountId: UUID, blockHeightCursor: Option[BlockHeight]): IO[Unit] =
    blockHeightCursor match {
      case Some(blockHeight) =>
        IO.delay {
          savedTransactions.update(
            accountId,
            savedTransactions.getOrElse(accountId, List.empty).filter(_.block.height < blockHeight)
          )
        }
      case None => IO.delay(savedTransactions.remove(accountId))
    }

  def getTransactions(
      accountId: UUID,
      blockHeight: Option[BlockHeight],
      limit: Option[Int],
      offset: Option[Int],
      sortingOrder: Option[SortingEnum]
  ): IO[GetTransactionsResult] =
    IO.delay {
      val filteredTransactions = savedTransactions
        .getOrElse(accountId, List())
        .filter(_.block.height >= blockHeight.getOrElse(0L))
        .sortWith((t, t2) => {
          sortingOrder.getOrElse(SortingEnum.Descending) match {
            case SortingEnum.Descending => t.hash > t2.hash
            case _                      => t.hash < t2.hash
          }
        })

      val slicedTransactions =
        filteredTransactions.slice(offset.getOrElse(0), offset.getOrElse(0) + limit.getOrElse(0))
      val hasMore = filteredTransactions.drop(offset.getOrElse(0) + limit.getOrElse(0)).nonEmpty

      new GetTransactionsResult(
        transactions = slicedTransactions.map(serializeTransaction),
        truncated = hasMore
      )
    }
}
