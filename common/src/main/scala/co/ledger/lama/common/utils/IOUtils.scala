package co.ledger.lama.common.utils

import cats.effect.{IO, Timer}
import fs2.{Chunk, Pull, Stream}

object IOUtils {

  def fetchPaginatedItems[T](
      evaluate: (Int, Int) => IO[T],
      continue: T => Boolean,
      offset: Int = 0,
      limit: Int = 20
  ): Pull[IO, T, Unit] = {
    Pull
      .eval(
        evaluate(offset, limit)
      )
      .flatMap { res =>
        if (continue(res)) {
          Pull.output(Chunk(res)) >>
            fetchPaginatedItems[T](evaluate, continue, offset + limit, limit)
        } else {
          Pull.output(Chunk(res))
        }
      }
  }

  def retry[T](io: IO[T], success: T => Boolean, policy: RetryPolicy = RetryPolicy.linear())(
      implicit t: Timer[IO]
  ): IO[T] = {
    Stream
      .eval(io.flatMap { res =>
        if (success(res))
          IO.pure(res)
        else
          IO.raiseError(new Exception())
      })
      .attempts(policy)
      .collectFirst {
        case Right(res) => res
      }
      .compile
      .lastOrError
  }
}
