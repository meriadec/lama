package co.ledger.lama.bitcoin.transactor.services
import cats.effect.IO
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib

class BitcoinLibGrpcClientServiceMock extends BitcoinLibGrpcService {

  def createTransaction(
      transaction: bitcoinLib.CreateTransactionRequest,
      changeAddress: String,
      estimatedFeeSatPerKb: Long
  ): IO[bitcoinLib.RawTransactionResponse] =
    IO(
      bitcoinLib.RawTransactionResponse(
        "hex",
        "hash",
        "witnessHash"
      )
    )

}
