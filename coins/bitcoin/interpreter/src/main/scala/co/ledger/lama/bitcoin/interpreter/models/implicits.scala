package co.ledger.lama.bitcoin.interpreter.models

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.explorer.{DefaultInput, Output}
import co.ledger.lama.bitcoin.common.models.service._
import doobie._
import doobie.postgres.implicits._

object implicits {

  implicit val bigIntType: Meta[BigInt] = Meta.Advanced.other[BigInt]("bigint")

  implicit val operationTypeMeta: Meta[OperationType] =
    pgEnumStringOpt("operation_type", OperationType.fromKey, _.toString.toLowerCase())

  implicit val changeTypeMeta: Meta[ChangeType] =
    pgEnumStringOpt("change_type", ChangeType.fromKey, _.toString.toLowerCase())

  // For 'BigInt' values, we need to use Long (because that's what a bigint is in psql)
  // If problems arise with the length of the value we'll have to use VARCHAR instead in db.

  implicit val writeInput: Write[DefaultInput] =
    Write[(String, Long, Long, BigInt, String, String, List[String], BigInt)]
      .contramap { i =>
        (
          i.outputHash,
          i.outputIndex,
          i.inputIndex,
          i.value.toLong,
          i.address,
          i.scriptSignature,
          i.txinwitness.toList,
          i.sequence
        )
      }

  implicit val writeOutput: Write[Output] =
    Write[(Long, BigInt, String, String)].contramap { o =>
      (o.outputIndex, o.value.toLong, o.address, o.scriptHex)
    }

  implicit lazy val readTransaction: Read[TransactionView] =
    Read[(String, String, String, Long, Long, String, Int, Long, String)]
      .map {
        case (
              id,
              hash,
              receivedAt,
              lockTime,
              fees,
              blockHash,
              confirmations,
              blockHeight,
              blockTime
            ) =>
          TransactionView(
            id = id,
            hash = hash,
            receivedAt = receivedAt,
            lockTime = lockTime,
            fees = fees,
            inputs = Seq(),
            outputs = Seq(),
            block = BlockView(blockHash, blockHeight, blockTime),
            confirmations = confirmations
          )
      }

  implicit lazy val readInput: Read[InputView] =
    Read[(String, Int, Int, Long, String, String, Long, Boolean)].map {
      case (
            outputHash,
            outputIndex,
            inputIndex,
            value,
            address,
            scriptSignature,
            sequence,
            belongs
          ) =>
        InputView(
          outputHash = outputHash,
          outputIndex = outputIndex,
          inputIndex = inputIndex,
          value = value,
          address = address,
          scriptSignature = scriptSignature,
          txinwitness = Seq(),
          sequence = sequence,
          belongs = belongs
        )
    }

  implicit lazy val readOutput: Read[OutputView] =
    Read[(Int, Long, String, String, Boolean, Option[ChangeType])].map {
      case (outputIndex, value, address, scriptHex, belongs, changeType) =>
        OutputView(
          outputIndex = outputIndex,
          value = value,
          address = address,
          scriptHex = scriptHex,
          belongs = belongs,
          changeType = changeType
        )
    }

  implicit lazy val readOperation: Read[Operation] =
    Read[(UUID, String, OperationType, Long, String)]
      .map {
        case (accountId, hash, operationType, value, time) =>
          Operation(accountId, hash, None, operationType, value, time)
      }

  implicit lazy val writeOperation: Write[Operation] =
    Write[(UUID, String, OperationType, Long, String)]
      .contramap { op =>
        (op.accountId, op.hash, op.operationType, op.value.toLong, op.time)
      }
}
