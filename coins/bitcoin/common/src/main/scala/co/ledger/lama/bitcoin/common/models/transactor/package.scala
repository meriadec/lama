package co.ledger.lama.bitcoin.common.models

import io.circe.{Decoder, Encoder}
import co.ledger.lama.bitcoin.transactor.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

package object transactor {

  abstract class CoinSelectionStrategy(val name: String) {
    def toProto: protobuf.CoinSelector
  }

  object CoinSelectionStrategy {

    case object DepthFirst extends CoinSelectionStrategy(name = "DepthFirst") {
      def toProto: protobuf.CoinSelector = protobuf.CoinSelector.DEPTH_FIRST
    }

    val all: Map[String, CoinSelectionStrategy] = Map(
      DepthFirst.name -> DepthFirst
    )

    def fromKey(key: String): Option[CoinSelectionStrategy] = all.get(key)

    implicit val encoder: Encoder[CoinSelectionStrategy] = Encoder.encodeString.contramap(_.name)

    implicit val decoder: Decoder[CoinSelectionStrategy] =
      Decoder.decodeString.emap(fromKey(_).toRight("unable to decode sort"))

    def fromProto(proto: protobuf.CoinSelector): CoinSelectionStrategy =
      proto match {
        case _ => DepthFirst
      }
  }

  case class PrepareTxOutput(
      address: String,
      value: BigInt
  ) {
    def toProto: protobuf.PrepareTxOutput =
      protobuf.PrepareTxOutput(
        address,
        value.toString
      )
  }

  object PrepareTxOutput {
    implicit val encoder: Encoder[PrepareTxOutput] = deriveConfiguredEncoder[PrepareTxOutput]
    implicit val decoder: Decoder[PrepareTxOutput] = deriveConfiguredDecoder[PrepareTxOutput]

    def fromProto(proto: protobuf.PrepareTxOutput): PrepareTxOutput =
      PrepareTxOutput(
        proto.address,
        BigInt(proto.value)
      )
  }

  case class FeeInfo(slow: Long, normal: Long, fast: Long)

}
