package co.ledger.lama.bitcoin.api.routes

import buildinfo.BuildInfo
import cats.effect.IO
import io.circe.Json
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceEntityCodec._

object VersionController extends Http4sDsl[IO] {
  def routes(): HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root =>
    Ok(
      Json.obj(
        "version" -> Json.fromString(BuildInfo.version),
        "sha1"    -> Json.fromString(BuildInfo.gitHeadCommit.getOrElse("n/a"))
      )
    )
  }
}
