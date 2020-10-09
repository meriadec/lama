package co.ledger.lama.common.logging

import com.typesafe.scalalogging.StrictLogging

trait IOLogging extends StrictLogging {
  val log: IOLogger = IOLogger(logger)
}
