package com.evernym.verity.testkit.agentmsg.indy_pack.v_0_6

import com.evernym.verity.agentmsg.msgfamily.pairwise.{ConnReqRedirectResp_MFV_0_6, ConnReqRespMsg_MFV_0_6, RemoteMsgSent_MFV_0_6}
import com.evernym.verity.agentmsg.msgpacker.{PackedMsg, ParseParam, UnpackParam}
import com.evernym.verity.protocol.engine.DID
import com.evernym.verity.testkit.Matchers
import com.evernym.verity.testkit.agentmsg.AgentMsgHelper
import com.evernym.verity.testkit.mock.HasCloudAgent
import com.evernym.verity.testkit.mock.agent.MockAgent
import com.evernym.verity.testkit.util.{AgentCreated_MFV_0_6, ComMethodUpdated_MFV_0_6, ConnReqAccepted_MFV_0_6, KeyCreated_MFV_0_6, MsgsByConns_MFV_0_6}
import com.evernym.verity.util.Util.logger

/**
 * this will handle received/incoming/response agent messages
 */
trait AgentMsgHandler {
  this: AgentMsgHelper with MockAgent with HasCloudAgent with Matchers =>

  object v_0_6_resp {
    def handleAgentCreatedResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): AgentCreated_MFV_0_6 = {
      logger.debug("Unpacking agent created response message (MFV 0.6)")
      val acm = unpackAgentCreatedRespMsg(rmw, getDIDToUnsealAgentRespMsg)
      logger.debug("Set cloud agent detail")
      setCloudAgentDetail(acm.withPairwiseDID, acm.withPairwiseDIDVerKey)
      acm
    }

    private def unpackAgentCreatedRespMsg(pmw: PackedMsg, unsealFromDID: DID)
    : AgentCreated_MFV_0_6 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[AgentCreated_MFV_0_6]
      logApiCallProgressMsg("agent-created: " + cm)
      cm
    }

    def handleComMethodUpdatedResp(rmw: PackedMsg): ComMethodUpdated_MFV_0_6 = {
      unpackComMethodUpdatedRespMsg(rmw, getDIDToUnsealAgentRespMsg)
    }

    def handlePairwiseKeyCreatedResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): KeyCreated_MFV_0_6 = {
      val pcd = pairwiseConnDetail(otherData(CONN_ID).toString)
      val kc = unpackKeyCreatedRespMsg(rmw, getDIDToUnsealAgentRespMsg)
      pcd.setMyCloudAgentPairwiseDidPair(kc.withPairwiseDID, kc.withPairwiseDIDVerKey)
      kc
    }

    def handleConnectKeyCreatedResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): KeyCreated_MFV_0_6 = {
      val kc = unpackKeyCreatedRespMsg(rmw, getDIDToUnsealAgentRespMsg)
      handleSetAgencyPairwiseAgentKey(kc.withPairwiseDID, kc.withPairwiseDIDVerKey)
      kc
    }

    def handleInviteCreatedResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): ConnReqRespMsg_MFV_0_6 = {
      val pcd = pairwiseConnDetail(otherData(CONN_ID).toString)
      val um = unpackResp_MPV_1_0(rmw, getDIDToUnsealAgentRespMsg)
      um.size shouldBe 1
      val mc = um.head.convertTo[ConnReqRespMsg_MFV_0_6]
      setLastSentInvite(pcd, mc.inviteDetail)
      mc
    }

    def handleConnReqAcceptedResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): ConnReqAccepted_MFV_0_6 = {
      unpackConReqAnswerRespMsg(rmw, getDIDToUnsealAgentRespMsg)
    }

    private def unpackConReqRedirectedRespMsg(pmw: PackedMsg, unsealFromDID: DID)
    : ConnReqRedirectResp_MFV_0_6 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[ConnReqRedirectResp_MFV_0_6]
      logApiCallProgressMsg("redirected: " + cm)
      cm
    }

    def handleConnReqRedirectedResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): ConnReqRedirectResp_MFV_0_6 = {
      unpackConReqRedirectedRespMsg(rmw, getDIDToUnsealAgentRespMsg)
    }

    private def unpackRemoteMsgSentRespMsg(pmw: PackedMsg, unsealFromDID: DID)
    : RemoteMsgSent_MFV_0_6 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[RemoteMsgSent_MFV_0_6]
      logApiCallProgressMsg("remote msg sent: " + cm)
      cm
    }

    private def unpackGetMsgsByConnsRespMsg(pmw: PackedMsg, unsealFromDID: String)
    :  MsgsByConns_MFV_0_6 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[MsgsByConns_MFV_0_6]
      logApiCallProgressMsg("get msgs by connections: " + cm)
      cm
    }

    def handleSendRemoteMsgResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): RemoteMsgSent_MFV_0_6 = {
      unpackRemoteMsgSentRespMsg(rmw, getDIDToUnsealAgentRespMsg)
    }

    def handleGetMsgsByConnsResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): MsgsByConns_MFV_0_6 = {
      val parseParam = ParseParam(parseBundledMsgs=false)
      unpackAgentMsg[MsgsByConns_MFV_0_6](rmw.msg, up=UnpackParam(parseParam=parseParam))
    }

    private def unpackComMethodUpdatedRespMsg(pmw: PackedMsg, unsealFromDID: DID)
    : ComMethodUpdated_MFV_0_6 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[ComMethodUpdated_MFV_0_6]
      logApiCallProgressMsg("connected: " + cm)
      cm
    }

    private def unpackKeyCreatedRespMsg(pmw: PackedMsg, unsealFromDID: DID)
    : KeyCreated_MFV_0_6 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[KeyCreated_MFV_0_6]
      logApiCallProgressMsg("connected: " + cm)
      cm
    }

    private def unpackConReqAnswerRespMsg(pmw: PackedMsg, unsealFromDID: DID)
    : ConnReqAccepted_MFV_0_6 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[ConnReqAccepted_MFV_0_6]
      logApiCallProgressMsg("connected: " + cm)
      cm
    }

  }

}
