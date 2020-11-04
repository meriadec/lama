package co.ledger.lama.bitcoin.api.utils

import java.time.Instant
import java.time.format.DateTimeFormatter

import co.ledger.lama.bitcoin.interpreter.protobuf.SortingOrder
import co.ledger.lama.common.models.Sort
import org.http4s.{QueryParamCodec, QueryParamDecoder}
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

object RouterUtils {
  implicit val sortQueryParamDecoder: QueryParamDecoder[Sort] =
    QueryParamDecoder[String].map(Sort.fromKey(_).getOrElse(Sort.Descending))

  object OptionalBlockHeightQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Long]("blockHeight")

  object OptionalLimitQueryParamMatcher  extends OptionalQueryParamDecoderMatcher[Int]("limit")
  object OptionalOffsetQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("offset")
  object OptionalSortQueryParamMatcher   extends OptionalQueryParamDecoderMatcher[Sort]("sort")

  implicit val isoInstantCodec: QueryParamCodec[Instant] =
    QueryParamCodec.instantQueryParamCodec(DateTimeFormatter.ISO_INSTANT)

  object OptionalStartInstantQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Instant]("start")

  object OptionalEndInstantQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Instant]("end")

  def parseSorting(sort: Option[Sort], default: Sort = Sort.Descending) = {
    sort.getOrElse(default) match {
      case Sort.Ascending => SortingOrder.ASC
      case _              => SortingOrder.DESC
    }
  }
}
