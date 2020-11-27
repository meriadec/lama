package co.ledger.lama.bitcoin.worker.mock.faulty

trait FaultyBase {
  val err = new Exception("Oh nooes")
}
