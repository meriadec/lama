package co.ledger.lama.common.logging

import cats.effect.IO
import com.typesafe.scalalogging.Logger
import org.slf4j.Marker

case class IOLogger(logger: Logger) {

  // TRACE

  def trace(message: String): IO[Unit] =
    IO(logger.trace(message))

  def trace(marker: Marker, message: String): IO[Unit] =
    IO(logger.trace(marker, message))

  def trace(message: String, cause: Throwable): IO[Unit] =
    IO(logger.trace(message, cause))

  def trace(marker: Marker, message: String, cause: Throwable): IO[Unit] =
    IO(logger.trace(marker, message, cause))

  // DEBUG

  def debug(message: => String): IO[Unit] =
    IO(logger.debug(message))

  def debug(marker: Marker, message: => String): IO[Unit] =
    IO(logger.debug(marker, message))

  def debug(message: => String, cause: Throwable): IO[Unit] =
    IO(logger.debug(message, cause))

  def debug(marker: Marker, message: => String, cause: Throwable): IO[Unit] =
    IO(logger.debug(marker, message, cause))

  // INFO

  def info(message: String): IO[Unit] =
    IO(logger.info(message))

  def info(marker: Marker, message: String): IO[Unit] =
    IO(logger.info(marker, message))

  def info(message: String, cause: Throwable): IO[Unit] =
    IO(logger.info(message, cause))

  def info(marker: Marker, message: String, cause: Throwable): IO[Unit] =
    IO(logger.info(marker, message, cause))

  // WARN

  def warn(message: String): IO[Unit] =
    IO(logger.warn(message))

  def warn(marker: Marker, message: String): IO[Unit] =
    IO(logger.warn(marker, message))

  def warn(message: String, cause: Throwable): IO[Unit] =
    IO(logger.warn(message, cause))

  def warn(marker: Marker, message: String, cause: Throwable): IO[Unit] =
    IO(logger.warn(marker, message, cause))

  // ERROR

  def error(message: String): IO[Unit] =
    IO(logger.error(message))

  def error(marker: Marker, message: String): IO[Unit] =
    IO(logger.error(marker, message))

  def error(message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(message, cause))

  def error(marker: Marker, message: String, cause: Throwable): IO[Unit] =
    IO(logger.error(marker, message, cause))
}
