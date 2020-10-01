package co.ledger.lama.bitcoin.worker

import co.ledger.lama.bitcoin.common.models.{BlockHash, BlockHeight, Transaction}
import co.ledger.protobuf.bitcoin.AddressInfo
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

package object models {

  case class GetTransactionsResponse(truncated: Boolean, txs: Seq[Transaction])

  object GetTransactionsResponse {
    implicit val encoder: Encoder[GetTransactionsResponse] =
      deriveEncoder[GetTransactionsResponse]
    implicit val decoder: Decoder[GetTransactionsResponse] =
      deriveDecoder[GetTransactionsResponse]
  }

  case class PayloadData(
      blockHeight: Option[BlockHeight] = None,
      blockHash: Option[BlockHash] = None,
      txsSize: Option[Int] = None,
      errorMessage: Option[String] = None
  )

  object PayloadData {
    implicit val encoder: Encoder[PayloadData] = deriveEncoder[PayloadData]
    implicit val decoder: Decoder[PayloadData] = deriveDecoder[PayloadData]
  }

  case class BatchResult(addresses: Seq[AddressInfo], transactions: Seq[Transaction])

}
