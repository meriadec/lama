package co.ledger.lama.bitcoin.transactor.models

import co.ledger.lama.bitcoin.common.models.BitcoinNetwork
import co.ledger.lama.bitcoin.common.models.BitcoinNetwork.{MainNet, RegTest, TestNet3, Unspecified}
import co.ledger.protobuf.bitcoin.libgrpc

object implicits {

  implicit class BitcoinNetworkLibGrpcProtoImplicit(network: BitcoinNetwork) {

    def fromLibGrpcProto(proto: libgrpc.BitcoinNetwork): BitcoinNetwork =
      proto match {
        case libgrpc.BitcoinNetwork.BITCOIN_NETWORK_MAINNET  => MainNet
        case libgrpc.BitcoinNetwork.BITCOIN_NETWORK_TESTNET3 => TestNet3
        case libgrpc.BitcoinNetwork.BITCOIN_NETWORK_REGTEST  => RegTest
        case _                                               => Unspecified
      }

    def toLibGrpcProto: libgrpc.BitcoinNetwork =
      network match {
        case TestNet3 => libgrpc.BitcoinNetwork.BITCOIN_NETWORK_TESTNET3
        case RegTest  => libgrpc.BitcoinNetwork.BITCOIN_NETWORK_REGTEST
        case _        => libgrpc.BitcoinNetwork.BITCOIN_NETWORK_MAINNET
      }

  }

}
