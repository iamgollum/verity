package com.evernym.verity.agentmsg.msgfamily.pairwise

import com.evernym.verity.actor.agent.MsgPackFormat.MPF_INDY_PACK
import com.evernym.verity.actor.testkit.checks.UNSAFE_IgnoreLog
import com.evernym.verity.agentmsg.msgfamily.MsgFamilyUtil._
import com.evernym.verity.agentmsg.msgpacker.PackedMsg
import com.evernym.verity.agentmsg.{AgentMsgSpecBase, DefaultMsgCodec}
import com.evernym.verity.protocol.engine
import com.evernym.verity.protocol.engine.ProtocolRegistry.Entry
import com.evernym.verity.protocol.engine.{PinstIdResolution, ProtocolRegistry}
import com.evernym.verity.protocol.protocols.connecting.v_0_6.{ConnectingProtoDef => ConnectingProtoDef_V_0_6}
import com.evernym.verity.vault.WalletAccessParam
import com.evernym.verity.testkit.BasicSpec


class MsgExtractorSpec extends BasicSpec with AgentMsgSpecBase {

  //TODO GENERAL CONVERSION from PackedMsg to native (case class) and visa versa

  implicit val protoReg: ProtocolRegistry[_] = engine.ProtocolRegistry(Entry(ConnectingProtoDef_V_0_6, PinstIdResolution.V0_1))

  val typ = "both"

  //TODO
  // given a packed message"
  // extract (MsgPackFormat, ThreadId, AgentMsgType, NativeMsg)"
  // for a message in Connecting 0.6"
  // given a native outgoing message"
  // construct a PackedMsg"
  // for a single outgoing message in Connecting 0.6"
  // do this for all Connecting 0.6 messages"


  lazy val createConnectionMsg: CreateConnectionReqMsg_MFV_0_6 =
    CreateConnectionReqMsg_MFV_0_6(MSG_TYPE_DETAIL_CREATE_CONNECTION, sourceId = "test-id-1")

  lazy val aliceCloudMsgExtractor: MsgExtractor = {
    implicit val acWap: WalletAccessParam = aliceCloudAgentWap
    new MsgExtractor(aliceCloudAgentKeyInfo, walletAPI)
  }
  lazy val aliceMsgExtractor: MsgExtractor = {
    implicit val aWap: WalletAccessParam = aliceWap
    new MsgExtractor(aliceKeyInfo, walletAPI)
  }

  def packedMsg: PackedMsg = aliceMsgExtractor.pack(MPF_INDY_PACK, DefaultMsgCodec.toJson(createConnectionMsg), Set(aliceCloudAgentKeyInfo))

  def setup(): Unit = {
    //touch each of these lazy vals
    //this is a bit of a hack... the better solution is don't let tests have shared state
    aliceKey
    aliceCloudAgencyKey
    aliceCloudAgentKey
  }

  "MsgExtractor" - {

    "when asked to pack a message" - {
      "should return packed message" taggedAs (UNSAFE_IgnoreLog) in {
        setup()
        packedMsg
        //TODO need some assertions here; how do we know if it works?
      }
    }

    "when asked to unpack a packed message" - {
      "should return agent message wrapper" in {
        setup()
        val pm = packedMsg
        aliceCloudMsgExtractor.unpack(pm)
        //TODO need some assertions here; how do we know if it works?
      }
    }

    "when asked to extract agent message wrapper" - {
      "should return extracted items" in {
        setup()
        val pm = packedMsg
        val amw = aliceCloudMsgExtractor.unpack(pm)
        val m = aliceCloudMsgExtractor.extract(amw)
        m.meta.threadId shouldBe "0"
        m.msg shouldBe createConnectionMsg
        m.meta.forRelationship shouldBe None
      }
    }

  }

  "Unit Tests" - {
    "computeForRelationship" - {
      def makeJson(forRel: String) = {
        s"""{
           |   "@type":"did:sov:123456789abcdefghi1234;spec/$MSG_FAMILY_QUESTION_ANSWER/1.0/ask-question",
           |   "@id":"c09ba060-7c29-49db-8fa3-1e825c13b1c6",
           |   $forRel
           |   "~thread":{
           |      "thid":"08bd5baa-6ffa-4004-9af7-86837ff0ae86"
           |   }
           |}""".stripMargin
      }

      "can find for_relationship" in {
        val testJson = makeJson(""" "~for_relationship":"abcd12345",""")
        DefaultMsgCodec.extractMetadata(testJson, MPF_INDY_PACK).forRelationship shouldBe Some("abcd12345")
      }

      "none if it don't exist" in {
        val testJson = makeJson("")
        DefaultMsgCodec.extractMetadata(testJson, MPF_INDY_PACK).forRelationship shouldBe None
      }

      "none if empty/blank string" in {
        val testJson = makeJson(""" "~for_relationship":" ",""")
        DefaultMsgCodec.extractMetadata(testJson, MPF_INDY_PACK).forRelationship shouldBe None

        val testJson2 = makeJson(""" "~for_relationship":" ",""")
        DefaultMsgCodec.extractMetadata(testJson2, MPF_INDY_PACK).forRelationship shouldBe None

        val testJson3 = makeJson(""" "~for_relationship":"     ",""")
        DefaultMsgCodec.extractMetadata(testJson3, MPF_INDY_PACK).forRelationship shouldBe None
      }

      "none if object" in {
        val testJson = makeJson(""""~for_relationship":{"for_relationship":"test"},""")
        DefaultMsgCodec.extractMetadata(testJson, MPF_INDY_PACK).forRelationship shouldBe None
      }
    }
  }

}

