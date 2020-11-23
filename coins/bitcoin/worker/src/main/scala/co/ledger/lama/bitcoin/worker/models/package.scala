package co.ledger.lama.bitcoin.worker

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.bitcoin.common.models.worker.{Block, ConfirmedTransaction}
import co.ledger.protobuf.bitcoin.keychain.AddressInfo
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}

package object models {

  case class PayloadData(
      lastBlock: Option[Block] = None,
      errorMessage: Option[String] = None
  )

  object PayloadData {
    implicit val encoder: Encoder[PayloadData] = deriveConfiguredEncoder[PayloadData]
    implicit val decoder: Decoder[PayloadData] = deriveConfiguredDecoder[PayloadData]
  }

  case class BatchResult(
      addresses: Seq[AddressInfo],
      transactions: Seq[ConfirmedTransaction],
      continue: Boolean
  )

}
