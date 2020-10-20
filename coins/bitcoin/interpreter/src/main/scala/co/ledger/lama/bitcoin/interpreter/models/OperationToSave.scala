package co.ledger.lama.bitcoin.interpreter.models

import java.util.UUID

import co.ledger.lama.bitcoin.common.models.service.OperationType

case class OperationToSave(
    accountId: UUID,
    hash: String,
    operationType: OperationType,
    value: BigInt,
    time: String,
    blockHash: String,
    blockHeight: Long
)
