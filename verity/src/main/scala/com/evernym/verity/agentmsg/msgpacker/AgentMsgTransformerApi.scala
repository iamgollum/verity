package com.evernym.verity.agentmsg.msgpacker

import com.evernym.verity.actor.agent.MsgPackFormat
import com.evernym.verity.actor.agent.MsgPackFormat.{MPF_INDY_PACK, MPF_MSG_PACK}
import com.evernym.verity.logging.LoggingUtil.getLoggerByName
import com.evernym.verity.protocol.engine.VerKey
import com.evernym.verity.util.JsonUtil.getDeserializedJson
import com.typesafe.scalalogging.Logger
import org.hyperledger.indy.sdk.wallet.Wallet


/**
 * this object contains all supported msg transformers ('msgPackTransformer', 'indyPackTransformer' etc)
 * and takes care of choosing correct one (either based on given input or otherwise)
 */
object AgentMsgTransformerApi {

  val logger: Logger = getLoggerByName("AgentMsgTransformerApi")

  private val msgPackTransformer: MsgTransformer = new MsgPackTransformer(MPF_MSG_PACK)
  private val indyPackTransformer: MsgTransformer = new IndyPackTransformer(MPF_INDY_PACK)

  def msgTransformer(mpf: MsgPackFormat): MsgTransformer = {
    mpf match {
      case MPF_MSG_PACK   => msgPackTransformer
      case MPF_INDY_PACK  => indyPackTransformer
      case _ => throw new RuntimeException("given msg-pack format is not supported: " + mpf)
    }
  }

  def pack(mpf: MsgPackFormat, wallet: Wallet, msg: String,
           recipVerKeys: Set[String], senderVerKey: Option[VerKey], packParam: PackParam = PackParam()): PackedMsg = {
    msgTransformer(mpf).pack(wallet, msg, recipVerKeys, senderVerKey, packParam)
  }

  def unpack(wallet: Wallet, msg: Array[Byte], fromVerKeyOpt: Option[VerKey],
             unpackParam: UnpackParam = UnpackParam()): AgentMsgWrapper = {

    val (transformer, fromVerKey) = if (isIndyPacked(msg)) {
      (indyPackTransformer, None)
    } else {
      (msgPackTransformer, fromVerKeyOpt)
    }

    val unpackedMsg = transformer.unpack(wallet, msg, fromVerKey, unpackParam)
    AgentMsgWrapper(transformer.msgPackFormat, unpackedMsg)
  }

  //set of keys to be present in any indy packed json message
  val indyPackedJsonRequiredKeys = Set("ciphertext")

  def isIndyPacked(msg: Array[Byte]): Boolean = {
    getDeserializedJson(msg).exists { jsObj =>
      val isValidIndyPackedMsg = indyPackedJsonRequiredKeys.forall(k => jsObj.has(k))
      if (! isValidIndyPackedMsg) {
        logger.debug(
          s"given binary msg successfully deserialized as json, " +
            s"but it wasn't a valid indy packed msg either:\n" +
            s"binary msg: ${msg.mkString(",")}, \n" +
            s"jsonMsg: ${jsObj.toString}")
      }
      isValidIndyPackedMsg
    }
  }

}