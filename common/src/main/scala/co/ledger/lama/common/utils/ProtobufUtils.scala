package co.ledger.lama.common.utils

import java.time.Instant

object ProtobufUtils {

  def fromInstant(instant: Instant): com.google.protobuf.timestamp.Timestamp =
    com.google.protobuf.timestamp.Timestamp(
      seconds = instant.getEpochSecond,
      nanos = instant.getNano
    )

  def toInstant(timestamp: com.google.protobuf.timestamp.Timestamp): Instant =
    Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos)

}
