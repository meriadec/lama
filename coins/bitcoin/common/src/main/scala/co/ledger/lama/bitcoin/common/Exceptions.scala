package co.ledger.lama.bitcoin.common

import org.http4s.Uri

object Exceptions {
  case class ExplorerClientException(uri: Uri, t: Throwable)
      extends Exception(s"Explorer client - ${uri.renderString} - ${t.getMessage}", t)
}
