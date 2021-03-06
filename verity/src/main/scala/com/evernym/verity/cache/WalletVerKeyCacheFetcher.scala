package com.evernym.verity.cache


import com.evernym.verity.constants.Constants._
import com.evernym.verity.config.CommonConfig._
import com.evernym.verity.config.AppConfig
import com.evernym.verity.protocol.engine.DID
import com.evernym.verity.vault._


case class GetWalletVerKeyParam(did: DID, getFromPool: Boolean = false, wap: WalletAccessParam) {
  override def toString: String = s"DID: $did, getKeyFromPool: $getFromPool"
}

class WalletVerKeyCacheFetcher(val walletAPI: WalletAPI, config: AppConfig) extends SyncCacheValueFetcher {

  lazy val id: Int = WALLET_VER_KEY_CACHE_FETCHER_ID

  //time to live in seconds, afterwards they will be considered as expired and re-fetched from source
  lazy val ttls: Option[Int] = Option(config.getConfigIntOption(VER_KEY_CACHE_EXPIRATION_TIME_IN_SECONDS).getOrElse(1800))

  override def getKeyDetailMapping(kds: Set[KeyDetail]): Set[KeyMapping] = {
    kds.map { kd =>
      val gvp = kd.key.asInstanceOf[GetWalletVerKeyParam]
      KeyMapping(kd, gvp.did, gvp.did)
    }
  }

  override def getByKeyDetail(kd: KeyDetail): Map[Any, Any] = {
    val gvp = kd.key.asInstanceOf[GetWalletVerKeyParam]
    val verKeyOpt = walletAPI.getVerKeyOption(
      KeyInfo(Right(GetVerKeyByDIDParam(gvp.did, getKeyFromPool = gvp.getFromPool))))(gvp.wap)
    val result: Option[Map[Any, Any]] = verKeyOpt.map(vk => Map(gvp.did -> vk))
    result.getOrElse(Map.empty)
  }

}
