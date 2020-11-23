package co.ledger.lama.bitcoin.common.utils

import co.ledger.lama.bitcoin.interpreter.protobuf
import co.ledger.lama.common.models

object BtcProtoUtils {

  implicit class SortProtoUtils(sort: models.Sort) {
    def toProto: protobuf.SortingOrder = {
      sort match {
        case models.Sort.Ascending => protobuf.SortingOrder.ASC
        case _                     => protobuf.SortingOrder.DESC
      }
    }
  }

  object Sort {
    def fromProto(proto: protobuf.SortingOrder): models.Sort =
      proto match {
        case protobuf.SortingOrder.ASC => models.Sort.Ascending
        case _                         => models.Sort.Descending
      }
  }

}
