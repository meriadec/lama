package co.ledger.lama.bitcoin.transactor.services

import java.time.Instant

import co.ledger.lama.bitcoin.common.models.interpreter.{ChangeType, Utxo}
import co.ledger.lama.bitcoin.common.models.transactor.CoinSelectionStrategy
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class CoinSelectionServiceTest extends AnyFlatSpecLike with Matchers {

  "A depthsFirstStrategy" should "return oldest utxos first" in {

    val utxo = Utxo(
      "hash",
      0,
      100000,
      "address",
      "script",
      true,
      Some(ChangeType.Internal),
      Instant.now
    )

    val utxos = List(
      utxo,
      utxo.copy(value = 10000)
    )

    CoinSelectionService
      .pickUtxos(CoinSelectionStrategy.DepthFirst, utxos, 10000)
      .unsafeRunSync() should have size 1

    CoinSelectionService
      .pickUtxos(CoinSelectionStrategy.DepthFirst, utxos, 100001)
      .unsafeRunSync() should have size 2

  }

}
