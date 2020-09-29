package co.ledger.lama.bitcoin.common.models

import java.sql.Timestamp

case class Transaction(
    accountId: String,
    hash: String,
    blockHash: String,
    fee: BigInt,
    block: Option[Block] = None,
    inputs: List[Input] = List(),
    outputs: List[Output] = List(),
    coinbase: List[CoinBase] = List()
)

case class Input(
    outputTxHash: String,
    outputIndex: Int,
    address: String,
    amount: BigInt,
    sequence: BigInt
)

case class Output(
    index: Int,
    address: String,
    amount: BigInt
)

case class CoinBase(
    address: String,
    reward: BigInt,
    fee: BigInt
)

case class Block(
    hash: String,
    height: BigInt,
    time: Timestamp,
    deleted: Boolean = false
)

sealed trait OperationType
case object Send     extends OperationType
case object Received extends OperationType

case class Operation(
    accountId: String,
    tx: Transaction,
    operationType: String,
    amount: BigInt,
    time: Timestamp,
    sources: List[String],
    destinations: List[String]
)
