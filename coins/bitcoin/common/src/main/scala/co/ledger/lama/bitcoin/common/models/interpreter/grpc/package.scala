package co.ledger.lama.bitcoin.common.models.interpreter

import co.ledger.lama.bitcoin.common.models.worker.Block
import co.ledger.lama.bitcoin.interpreter.protobuf
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import co.ledger.lama.common.models.implicits._

package object grpc {

  case class GetOperationsResult(
      truncated: Boolean,
      operations: List[Operation],
      size: Int
  )

  object GetOperationsResult {
    implicit val getOperationsResultDecoder: Decoder[GetOperationsResult] =
      deriveConfiguredDecoder[GetOperationsResult]

    def fromProto(proto: protobuf.GetOperationsResult): GetOperationsResult =
      GetOperationsResult(
        proto.truncated,
        proto.operations.map(Operation.fromProto).toList,
        proto.operations.size
      )
  }

  case class GetUTXOsResult(
      truncated: Boolean,
      utxos: List[Utxo],
      size: Int
  )

  object GetUTXOsResult {
    implicit val getUTXOsResultDecoder: Decoder[GetUTXOsResult] =
      deriveConfiguredDecoder[GetUTXOsResult]

    def fromProto(proto: protobuf.GetUTXOsResult): GetUTXOsResult =
      GetUTXOsResult(
        proto.truncated,
        proto.utxos.map(Utxo.fromProto).toList,
        proto.utxos.size
      )
  }

  case class GetBalanceHistoryResult(
      balances: Seq[BalanceHistory]
  )

  object GetBalanceHistoryResult {
    implicit val encoder: Encoder[GetBalanceHistoryResult] =
      deriveConfiguredEncoder[GetBalanceHistoryResult]
    implicit val decoder: Decoder[GetBalanceHistoryResult] =
      deriveConfiguredDecoder[GetBalanceHistoryResult]

    def fromProto(proto: protobuf.GetBalanceHistoryResult): GetBalanceHistoryResult =
      GetBalanceHistoryResult(
        proto.balances.map(BalanceHistory.fromProto)
      )
  }

  case class GetLastBlocksResult(
      blocks: List[Block]
  )

  object GetLastBlocksResult {
    implicit val encoder: Encoder[GetLastBlocksResult] =
      deriveConfiguredEncoder[GetLastBlocksResult]
    implicit val decoder: Decoder[GetLastBlocksResult] =
      deriveConfiguredDecoder[GetLastBlocksResult]

    def fromProto(proto: protobuf.GetLastBlocksResult): GetLastBlocksResult = {
      GetLastBlocksResult(
        proto.blocks.map(Block.fromProto).toList
      )
    }
  }

}
