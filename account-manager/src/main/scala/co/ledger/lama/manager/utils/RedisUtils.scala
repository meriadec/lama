package co.ledger.lama.manager.utils

import cats.effect.{IO, Resource, Timer}
import co.ledger.lama.common.utils.ResourceUtils
import co.ledger.lama.manager.config.RedisConfig
import com.redis.RedisClient

object RedisUtils {

  def createClient(conf: RedisConfig)(implicit t: Timer[IO]): Resource[IO, RedisClient] =
    ResourceUtils.retriableResource(
      "Create redis client",
      Resource.fromAutoCloseable(
        for {
          client <- IO.delay {
            new RedisClient(
              conf.host,
              conf.port,
              conf.db,
              if (conf.password.nonEmpty) Some(conf.password) else None
            )
          }

          _ <- {
            if (client.ping.isDefined) IO.unit
            else IO.raiseError(new Exception("Pinging redis failed"))
          }

        } yield client
      )
    )

}
