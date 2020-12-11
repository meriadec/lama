package co.ledger.lama.bitcoin.common.models.interpreter

import cats.data.NonEmptyList
import cats.implicits._
import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class InputView(
    outputHash: String,
    outputIndex: Int,
    inputIndex: Int,
    value: BigInt,
    address: String,
    scriptSignature: String,
    txinwitness: List[String],
    sequence: Long,
    derivation: Option[NonEmptyList[Int]]
) {
  val belongs: Boolean = derivation.isDefined

  def toProto: protobuf.InputView =
    protobuf.InputView(
      outputHash,
      outputIndex,
      inputIndex,
      value.toString,
      address,
      scriptSignature,
      txinwitness,
      sequence,
      derivation.map(_.toList).getOrElse(Nil)
    )
}

object InputView {
  def fromProto(proto: protobuf.InputView): InputView =
    InputView(
      proto.outputHash,
      proto.outputIndex,
      proto.inputIndex,
      BigInt(proto.value),
      proto.address,
      proto.scriptSignature,
      proto.txinwitness.toList,
      proto.sequence,
      proto.derivation.toList.toNel
    )

  implicit val encoder: Encoder[InputView] = deriveConfiguredEncoder[InputView]
  implicit val decoder: Decoder[InputView] = deriveConfiguredDecoder[InputView]
}
