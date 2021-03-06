package com.evernym.verity.testkit.agentmsg.indy_pack.v_0_7

import com.evernym.verity.agentmsg.msgpacker.PackedMsg
import com.evernym.verity.protocol.engine.DID
import com.evernym.verity.testkit.agentmsg.AgentMsgHelper
import com.evernym.verity.testkit.mock.HasCloudAgent
import com.evernym.verity.testkit.mock.agent.MockAgent
import com.evernym.verity.testkit.util.{AgentCreated_MFV_0_7, CreateAgentProblemReport_MFV_0_7}
import com.evernym.verity.util.Util.logger

/**
 * this will handle received/incoming/response agent messages
 */
trait AgentMsgHandler{ this: AgentMsgHelper with MockAgent with HasCloudAgent =>

  object v_0_7_resp {

    def handleCreateAgentProblemReport(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty)
    : CreateAgentProblemReport_MFV_0_7 = {
      unpackCreateAgentProblemReport(rmw, getDIDToUnsealAgentRespMsg)
    }

    private def unpackCreateAgentProblemReport(pmw: PackedMsg, unsealFromDID: DID)
    : CreateAgentProblemReport_MFV_0_7 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[CreateAgentProblemReport_MFV_0_7]
      logApiCallProgressMsg("problemReport: " + cm)
      cm
    }

    def handleAgentCreatedResp(rmw: PackedMsg, otherData: Map[String, Any]=Map.empty): AgentCreated_MFV_0_7 = {
      logger.debug("Unpacking agent created response message (MFV 0.7)")
      val acm = unpackAgentCreatedRespMsg(rmw, getDIDToUnsealAgentRespMsg)
      logger.debug("Set cloud agent detail")
      setCloudAgentDetail(acm.selfDID, acm.agentVerKey)
      acm
    }

    def unpackAgentCreatedRespMsg(pmw: PackedMsg, unsealFromDID: DID)
    : AgentCreated_MFV_0_7 = {
      val cm = unpackResp_MPV_1_0(pmw, unsealFromDID).head.convertTo[AgentCreated_MFV_0_7]
      logApiCallProgressMsg("agent-created: " + cm)
      cm
    }

  }

}
