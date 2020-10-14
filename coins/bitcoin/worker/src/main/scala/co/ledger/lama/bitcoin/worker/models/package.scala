package co.ledger.lama.bitcoin.worker

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.bitcoin.common.models.explorer.{Block, Transaction}
import co.ledger.protobuf.bitcoin.AddressInfo
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}

package object models {

  case class GetTransactionsResponse(truncated: Boolean, txs: Seq[Transaction])

  object GetTransactionsResponse {
    implicit val encoder: Encoder[GetTransactionsResponse] =
      deriveConfiguredEncoder[GetTransactionsResponse]
    implicit val decoder: Decoder[GetTransactionsResponse] =
      deriveConfiguredDecoder[GetTransactionsResponse]
  }

  case class PayloadData(
      lastBlock: Option[Block] = None,
      fetchedTxsSize: Option[Int] = None,
      errorMessage: Option[String] = None
  )

  object PayloadData {
    implicit val encoder: Encoder[PayloadData] = deriveConfiguredEncoder[PayloadData]
    implicit val decoder: Decoder[PayloadData] = deriveConfiguredDecoder[PayloadData]
  }

  case class BatchResult(
      addresses: Seq[AddressInfo],
      transactions: Seq[Transaction],
      continue: Boolean
  )

}
