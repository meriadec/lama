package co.ledger.lama.bitcoin.interpreter.models

import java.sql.Timestamp
import java.util.UUID

import co.ledger.lama.bitcoin.common.models.explorer.{Block, DefaultInput, Output}
import co.ledger.lama.bitcoin.common.models.service._
import doobie._
import doobie.postgres.implicits._
import doobie.implicits.javasql._

import scala.math.BigDecimal.javaBigDecimal2bigDecimal

object implicits {

  implicit val bigIntType: Meta[BigInt] =
    Meta.BigDecimalMeta.imap[BigInt](_.toBigInt)(BigDecimal(_).bigDecimal)

  implicit lazy val readBalanceHistory: Read[BalanceHistory] =
    Read[(BigDecimal, Int, BigDecimal, BigDecimal, Timestamp)]
      .map { case (balance, utxos, received, sent, time) =>
        BalanceHistory(
          balance = balance.toBigInt,
          utxos = utxos,
          received = received.toBigInt,
          sent = sent.toBigInt,
          time = time.toInstant
        )
      }

  implicit val operationTypeMeta: Meta[OperationType] =
    pgEnumStringOpt("operation_type", OperationType.fromKey, _.toString.toLowerCase())

  implicit val changeTypeMeta: Meta[ChangeType] =
    pgEnumStringOpt("change_type", ChangeType.fromKey, _.toString.toLowerCase())

  implicit val writeInput: Write[DefaultInput] =
    Write[(String, Int, Int, BigInt, String, String, List[String], Long)]
      .contramap { i =>
        (
          i.outputHash,
          i.outputIndex,
          i.inputIndex,
          i.value,
          i.address,
          i.scriptSignature,
          i.txinwitness.toList,
          i.sequence
        )
      }

  implicit val writeOutput: Write[Output] =
    Write[(BigInt, BigInt, String, String)].contramap { o =>
      (o.outputIndex, o.value, o.address, o.scriptHex)
    }

  implicit val readBlock: Read[Block] =
    Read[(String, Long, String)]
      .map { case (hash, height, time) =>
        Block(hash, height, time)
      }

  implicit lazy val readTransactionView: Read[TransactionView] =
    Read[(String, String, String, Long, String, String, Long, BigDecimal, Int)]
      .map {
        case (
              id,
              hash,
              blockHash,
              blockHeight,
              blockTime,
              receivedAt,
              lockTime,
              fees,
              confirmations
            ) =>
          TransactionView(
            id = id,
            hash = hash,
            receivedAt = receivedAt,
            lockTime = lockTime,
            fees = fees.toBigInt,
            inputs = Seq(),
            outputs = Seq(),
            block = BlockView(blockHash, blockHeight, blockTime),
            confirmations = confirmations
          )
      }

  implicit lazy val readInputView: Read[InputView] =
    Read[(String, Int, Int, BigDecimal, String, String, Long, Boolean)].map {
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
          value = value.toBigInt,
          address = address,
          scriptSignature = scriptSignature,
          txinwitness = Seq(),
          sequence = sequence,
          belongs = belongs
        )
    }

  implicit lazy val readOutputView: Read[OutputView] =
    Read[(Int, BigDecimal, String, String, Boolean, Option[ChangeType])].map {
      case (outputIndex, value, address, scriptHex, belongs, changeType) =>
        OutputView(
          outputIndex = outputIndex,
          value = value.toBigInt,
          address = address,
          scriptHex = scriptHex,
          belongs = belongs,
          changeType = changeType
        )
    }

  implicit lazy val readOperation: Read[Operation] =
    Read[(UUID, String, OperationType, BigDecimal, String)]
      .map { case (accountId, hash, operationType, value, time) =>
        Operation(accountId, hash, None, operationType, value.toBigInt, time)
      }

  implicit lazy val writeOperation: Write[OperationToSave] =
    Write[(UUID, String, OperationType, BigInt, String, String, Long)]
      .contramap { op =>
        (
          op.accountId,
          op.hash,
          op.operationType,
          op.value,
          op.time,
          op.blockHash,
          op.blockHeight
        )
      }

  implicit lazy val ReadOperationFull: Read[TransactionAmounts] =
    Read[
      (
          UUID,
          String,
          String,
          Long,
          String,
          Option[BigDecimal],
          Option[BigDecimal],
          Option[BigDecimal]
      )
    ]
      .map {
        case (
              accountId,
              hash,
              blockHash,
              blockHeight,
              blockTime,
              input_amount,
              output_amount,
              change_amount
            ) =>
          TransactionAmounts(
            accountId,
            hash,
            blockHash,
            blockHeight,
            blockTime,
            input_amount.map(_.toBigInt).getOrElse(BigInt(0)),
            output_amount.map(_.toBigInt).getOrElse(BigInt(0)),
            change_amount.map(_.toBigInt).getOrElse(BigInt(0))
          )
      }
}
