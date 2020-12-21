package co.ledger.lama.bitcoin.common.models.interpreter

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class GetBalanceHistoryResult(
    balances: List[BalanceHistory]
)

object GetBalanceHistoryResult {
  implicit val encoder: Encoder[GetBalanceHistoryResult] =
    deriveConfiguredEncoder[GetBalanceHistoryResult]
  implicit val decoder: Decoder[GetBalanceHistoryResult] =
    deriveConfiguredDecoder[GetBalanceHistoryResult]

  def fromProto(proto: protobuf.GetBalanceHistoryResult): GetBalanceHistoryResult =
    GetBalanceHistoryResult(
      proto.balances.map(BalanceHistory.fromProto).toList
    )
}
