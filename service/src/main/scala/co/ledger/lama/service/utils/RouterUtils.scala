package co.ledger.lama.service.utils

import co.ledger.lama.common.models.Sort
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

object RouterUtils {
  implicit val sortQueryParamDecoder: QueryParamDecoder[Sort] =
    QueryParamDecoder[String].map(Sort.fromKey(_).getOrElse(Sort.Descending))

  object OptionalBlockHeightQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[Long]("blockHeight")
  object OptionalLimitQueryParamMatcher  extends OptionalQueryParamDecoderMatcher[Int]("limit")
  object OptionalOffsetQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("offset")
  object OptionalSortQueryParamMatcher   extends OptionalQueryParamDecoderMatcher[Sort]("sort")
}
