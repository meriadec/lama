package co.ledger.lama.bitcoin.transactor.models

import co.ledger.lama.bitcoin.common.models.BitcoinNetwork
import co.ledger.lama.bitcoin.transactor.models.implicits._
import co.ledger.protobuf.bitcoin.libgrpc

object bitcoinLib {

  case class CreateTransactionRequest(
      lockTime: Long,
      inputs: Seq[Input],
      outputs: Seq[Output],
      network: BitcoinNetwork
  ) {
    def toProto: libgrpc.CreateTransactionRequest =
      libgrpc.CreateTransactionRequest(
        lockTime.toInt, // We use toInt because, even though we have Long (for
        inputs.map(_.toProto),
        outputs.map(_.toProto),
        network.toLibGrpcProto
      )
  }

  case class CreateTransactionResponse(
      hex: String,
      hash: String,
      witnessHash: String
  ) {
    def toProto: libgrpc.CreateTransactionResponse =
      libgrpc.CreateTransactionResponse(
        hex,
        hash,
        witnessHash
      )
  }

  object CreateTransactionResponse {
    def fromProto(proto: libgrpc.CreateTransactionResponse): CreateTransactionResponse =
      CreateTransactionResponse(
        proto.hex,
        proto.hash,
        proto.witnessHash
      )
  }

  case class Input(
      outputHash: String,
      outputIndex: Int,
      script: String,
      sequence: Long
  ) {
    def toProto: libgrpc.Input =
      libgrpc.Input(
        outputHash,
        outputIndex,
        script,
        sequence.toInt
      )
  }

  object Input {
    def fromProto(proto: libgrpc.Input): Input =
      Input(
        proto.outputHash,
        proto.outputIndex,
        proto.script,
        proto.sequence
      )
  }

  case class Output(
      address: String,
      value: BigInt
  ) {
    def toProto: libgrpc.Output =
      libgrpc.Output(
        address,
        value.toString
      )
  }

  object Output {
    def fromProto(proto: libgrpc.Output): Output =
      Output(
        proto.address,
        BigInt(proto.value)
      )
  }

}
