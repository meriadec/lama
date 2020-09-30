package co.ledger.lama.bitcoin.worker.utils

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.bitcoin.worker.models.explorer._

object ProtobufUtils {

  def serializeOutput(o: Output): protobuf.Output =
    new protobuf.Output(
      address = o.address,
      scriptPubKey = o.scriptHex,
      outputIndex = o.outputIndex,
      value = o.value
    )

  def serializeInput(i: Input): protobuf.Input =
    new protobuf.Input(
      address = i.address,
      outputHash = i.outputHash,
      outputIndex = i.outputIndex,
      value = i.value,
      scriptSig = i.scriptSignature,
      witness = i.txWitness,
      sequence = i.sequence
    )

  def serializeBlock(b: Block): protobuf.Block =
    new protobuf.Block(
      hash = b.hash,
      height = b.height,
      time = b.time.toLong
    )

  def serializeTransaction(tr: Transaction): protobuf.Transaction =
    new protobuf.Transaction(
      id = tr.id,
      hash = tr.hash,
      receivedAt = tr.receivedAt.toLong,
      lockTime = tr.lockTime,
      fees = tr.fees.toLong,
      inputs = tr.inputs.map(serializeInput),
      outputs = tr.outputs.map(serializeOutput),
      block = Some(serializeBlock(tr.block)),
      confirmations = tr.confirmations
    )
}
