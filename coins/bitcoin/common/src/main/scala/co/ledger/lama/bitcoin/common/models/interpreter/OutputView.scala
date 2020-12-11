package co.ledger.lama.bitcoin.common.models.interpreter

import cats.data.NonEmptyList
import cats.implicits._
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class OutputView(
    outputIndex: Int,
    value: BigInt,
    address: String,
    scriptHex: String,
    changeType: Option[ChangeType],
    derivation: Option[NonEmptyList[Int]]
) {
  val belongs: Boolean = derivation.isDefined

  def toProto: protobuf.OutputView =
    protobuf.OutputView(
      outputIndex,
      value.toString,
      address,
      scriptHex,
      changeType.getOrElse(ChangeType.External).toProto,
      derivation.map(_.toList).getOrElse(Nil)
    )
}

object OutputView {
  implicit val encoder: Encoder[OutputView] = deriveConfiguredEncoder[OutputView]
  implicit val decoder: Decoder[OutputView] = deriveConfiguredDecoder[OutputView]

  def fromProto(proto: protobuf.OutputView): OutputView =
    OutputView(
      proto.outputIndex,
      BigInt(proto.value),
      proto.address,
      proto.scriptHex,
      Some(ChangeType.fromProto(proto.changeType)),
      proto.derivation.toList.toNel
    )
}
