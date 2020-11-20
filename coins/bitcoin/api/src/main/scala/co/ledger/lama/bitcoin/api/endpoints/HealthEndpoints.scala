package co.ledger.lama.bitcoin.api.endpoints

import sttp.tapir.{Endpoint, endpoint}

object HealthEndpoints {
  val health: Endpoint[Unit, Unit, Unit, Any] = endpoint.get
}
