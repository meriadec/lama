package co.ledger.lama.bitcoin.api.models

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.worker.Block
import co.ledger.lama.bitcoin.common.models.{BitcoinNetwork, Scheme}
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._
import co.ledger.lama.common.models.{Coin, CoinFamily, SyncEvent}

object accountManager {

  case class AccountInfo(
      accountId: UUID,
      coinFamily: CoinFamily,
      coin: Coin,
      syncFrequency: Long,
      lastSyncEvent: Option[SyncEvent[Block]],
      balance: BigInt,
      utxos: Int,
      received: BigInt,
      sent: BigInt,
      label: Option[String]
  )

  object AccountInfo {
    implicit val decoder: Decoder[AccountInfo] =
      deriveConfiguredDecoder[AccountInfo]
    implicit val encoder: Encoder[AccountInfo] =
      deriveConfiguredEncoder[AccountInfo]
  }

  case class AccountRegistered(accountId: UUID, syncId: UUID, syncFrequency: Long)

  object AccountRegistered {
    implicit val decoder: Decoder[AccountRegistered] =
      deriveConfiguredDecoder[AccountRegistered]
    implicit val encoder: Encoder[AccountRegistered] =
      deriveConfiguredEncoder[AccountRegistered]
  }

  case class UpdateRequest(syncFrequency: Long)

  object UpdateRequest {
    implicit val encoder: Encoder[UpdateRequest] = deriveConfiguredEncoder[UpdateRequest]
    implicit val decoder: Decoder[UpdateRequest] = deriveConfiguredDecoder[UpdateRequest]
  }

  case class CreationRequest(
      extendedPublicKey: String,
      label: Option[String],
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinNetwork,
      coinFamily: CoinFamily,
      coin: Coin,
      syncFrequency: Option[Long]
  )

  object CreationRequest {
    implicit val encoder: Encoder[CreationRequest] = deriveConfiguredEncoder[CreationRequest]
    implicit val decoder: Decoder[CreationRequest] = deriveConfiguredDecoder[CreationRequest]
  }

}
