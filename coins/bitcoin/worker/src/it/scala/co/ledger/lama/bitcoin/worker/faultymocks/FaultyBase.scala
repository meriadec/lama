package co.ledger.lama.bitcoin.worker.faultymocks

trait FaultyBase {
  val err = new Exception("Oh nooes")
}
