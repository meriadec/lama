package co.ledger.lama.bitcoin.worker

import cats.effect.IO
import co.ledger.lama.common.models.{ReportableEvent, WorkableEvent}
import co.ledger.lama.common.utils.RabbitUtils
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import fs2.Stream

class SimpleAccountManager(
    rabbit: RabbitClient[IO],
    lamaEventsQueueName: QueueName,
    workerEventsExchangeName: ExchangeName,
    routingKey: RoutingKey
) {

  private lazy val consumer: Stream[IO, ReportableEvent] =
    RabbitUtils.createAutoAckConsumer[ReportableEvent](rabbit, lamaEventsQueueName)

  private lazy val publisher: Stream[IO, WorkableEvent => IO[Unit]] =
    RabbitUtils.createPublisher[WorkableEvent](rabbit, workerEventsExchangeName, routingKey)

  def consumeReportableEvent: IO[ReportableEvent] =
    consumer.take(1).compile.last.map(_.get)

  def publishWorkableEvent(e: WorkableEvent): IO[Unit] =
    publisher.evalMap(p => p(e)).compile.drain

}
