package co.ledger.lama.bitcoin.api.utils

import java.time.Instant
import java.time.format.DateTimeFormatter

import co.ledger.lama.common.models.Sort
import co.ledger.protobuf.bitcoin.keychain
import org.http4s.{ParseFailure, QueryParamCodec, QueryParamDecoder}
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

  object OptionalFromIndexQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("from")

  object OptionalToIndexQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("to")

  implicit val changeQueryParamDecoder: QueryParamDecoder[keychain.Change] =
    QueryParamDecoder[String].emap {
      case "external" => Right(keychain.Change.CHANGE_EXTERNAL)
      case "internal" => Right(keychain.Change.CHANGE_INTERNAL)
      case unknown    => Left(ParseFailure("Unknown change type", unknown))
    }

  object OptionalKeychainChangeParamMatcher
      extends OptionalQueryParamDecoderMatcher[keychain.Change]("change")

}
