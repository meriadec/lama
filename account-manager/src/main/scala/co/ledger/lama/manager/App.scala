package co.ledger.lama.manager

import cats.effect.{ExitCode, IO, IOApp, Resource}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.utils.{RabbitUtils, ResourceUtils}
import co.ledger.lama.common.utils.ResourceUtils.{grpcServer, postgresTransactor}
import co.ledger.lama.manager.config.{Config, OrchestratorConfig}
import com.redis.RedisClient
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.ExchangeType
import pureconfig.ConfigSource

object App extends IOApp with IOLogging {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    log.info("Instanciating resources")

    val resources = for {
      db <- postgresTransactor(conf.postgres)
      _ = log.info("DB instantiated")

      // rabbitmq client
      rabbitClient <- RabbitUtils.createClient(conf.rabbit)

      _ = log.info("RabbitMQ instantiated")

      // redis client
      redisClient <- ResourceUtils.retriableResource(
        Resource.fromAutoCloseable(IO(new RedisClient(conf.redis.host, conf.redis.port)))
      )

      _ = log.info("Redis instantiated")

      // define rpc service definitions
      serviceDefinitions = List(
        new Service(db, conf.orchestrator.coins).definition
      )

      // create the grpc server
      grpcServer <- grpcServer(conf.grpcServer, serviceDefinitions)

      _ = log.info("GRPC server instantiated")

    } yield (db, rabbitClient, redisClient, grpcServer)

    // start the grpc server and run the orchestrator stream
    resources
      .use {
        case (db, rabbitClient, redisClient, server) =>
          // create the orchestrator
          val orchestrator = new CoinOrchestrator(
            conf.orchestrator,
            db,
            rabbitClient,
            redisClient
          )

          log.info("Instantiating server")

          declareExchangesAndBindings(rabbitClient, conf.orchestrator) *>
            IO(server.start()) *>
            orchestrator.run().compile.drain
      }
      .as(ExitCode.Success)
  }

  // Declare rabbitmq exchanges and bindings used by workers and the orchestrator.
  private def declareExchangesAndBindings(
      rabbit: RabbitClient[IO],
      conf: OrchestratorConfig
  ): IO[Unit] = {
    val workerExchangeName = conf.workerEventsExchangeName
    val eventsExchangeName = conf.lamaEventsExchangeName

    val exchanges = List(
      (workerExchangeName, ExchangeType.Topic),
      (eventsExchangeName, ExchangeType.Topic)
    )

    val bindings = conf.coins
      .flatMap { coinConf =>
        List(
          (eventsExchangeName, coinConf.routingKey, coinConf.queueName(eventsExchangeName)),
          (workerExchangeName, coinConf.routingKey, coinConf.queueName(workerExchangeName))
        )
      }

    RabbitUtils.declareExchanges(rabbit, exchanges) *>
      RabbitUtils.declareBindings(rabbit, bindings)
  }

}
