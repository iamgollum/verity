package com.evernym.verity.protocol.protocols.connecting.v_0_5

import com.evernym.verity.constants.InitParamConstants._
import com.evernym.verity.actor._
import com.evernym.verity.actor.agent.msgsender.AgentMsgSender
import com.evernym.verity.agentmsg.msgfamily.AgentMsgContext
import com.evernym.verity.agentmsg.msgfamily.MsgFamilyUtil._
import com.evernym.verity.agentmsg.msgfamily.pairwise.{ConnReqRedirectedMsgHelper, ConnectingMsgHelper, RedirectConnReqMsgHelper}
import com.evernym.verity.agentmsg.msgpacker.{AgentMsgTransformer, AgentMsgWrapper, PackedMsg}
import com.evernym.verity.cache.Cache
import com.evernym.verity.config.AppConfig
import com.evernym.verity.http.common.RemoteMsgSendingSvc
import com.evernym.verity.protocol.Control
import com.evernym.verity.protocol.actor.{Init, ProtoMsg, UpdateMsgDeliveryStatus}
import com.evernym.verity.protocol.engine._
import com.evernym.verity.protocol.engine.util.?=>
import com.evernym.verity.protocol.protocols._
import com.evernym.verity.protocol.protocols.connecting.common._
import com.evernym.verity.push_notification.PushNotifMsgBuilder


//noinspection ScalaDeprecation
class ConnectingProtocol(val ctx: ProtocolContextApi[ConnectingProtocol,Role,ProtoMsg,Any,ConnectingState,String])
    extends Protocol[ConnectingProtocol,Role,ProtoMsg,Any,ConnectingState,String](ConnectingProtoDef)
      with ConnectingProtocolBase[ConnectingProtocol,Role,ConnectingState,String]
      with HasAppConfig
      with AgentMsgSender
      with MsgDeliveryResultHandler
      with PushNotifMsgBuilder {

  override lazy val appConfig: AppConfig = ctx.SERVICES_DEPRECATED.appConfig
  override lazy val remoteMsgSendingSvc: RemoteMsgSendingSvc = ctx.SERVICES_DEPRECATED.remoteMsgSendingSvc
  override lazy val generalCache: Cache = ctx.SERVICES_DEPRECATED.generalCache
  override lazy val agentMsgTransformer: AgentMsgTransformer = ctx.SERVICES_DEPRECATED.agentMsgTransformer

  lazy val myPairwiseDIDReq : DID = ctx.getState.parameters.paramValueRequired(MY_PAIRWISE_DID)
  lazy val myPairwiseVerKeyReq : VerKey = ctx.getState.parameters.paramValueRequired(MY_PAIRWISE_DID_VER_KEY)

  def initState(params: Seq[ParameterStored]): ConnectingState = {
    val seed = params.find(_.name == THIS_AGENT_WALLET_SEED).get.value
    initWalletDetail(seed)
    ConnectingState(
      ctx.SERVICES_DEPRECATED.appConfig,
      ctx.SERVICES_DEPRECATED.agentMsgTransformer)
  }

  def applyEvent: ApplyEvent = applyEventBase

  override def handleProtoMsg: (ConnectingState, Option[Role], ProtoMsg) ?=> Any = {
    case (_, _, m: Any)                           => handleProtoMsgWithoutEnvelope(m)
  }

  protected def handleProtoMsgWithoutEnvelope: ProtoMsg ?=> Any = {
    case amw: AgentMsgWrapper if amw.headAgentMsgDetail.msgName == CREATE_MSG_TYPE_CONN_REQ
                                                  => handleConnReqMsg(amw)

    case amw: AgentMsgWrapper if amw.headAgentMsgDetail.msgName == CREATE_MSG_TYPE_CONN_REQ_ANSWER
                                                  => handleConnReqAnswerMsg(amw)

    case amw: AgentMsgWrapper if amw.headAgentMsgDetail.msgName == CREATE_MSG_TYPE_REDIRECT_CONN_REQ
                                                  => handleRedirectConnReqMsg(amw)

    case amw: AgentMsgWrapper if amw.headAgentMsgDetail.msgName == CREATE_MSG_TYPE_CONN_REQ_REDIRECTED
                                                  => handleConnReqRedirectedMsg(amw)

    case gid: GetInviteDetail                     => handleGetInviteDetail(gid)

  }

  def handleControl: Control ?=> Any = {
    case ip: Init                                 => handleInitParams(ip)
    case scrm: SendConnReqMsg                     => sendConnReqMsg(scrm.uid)
    case smrca: SendMsgToRemoteCloudAgent         => sendMsgToRemoteCloudAgent(smrca.uid, smrca.msgPackFormat)
    case smea: SendMsgToEdgeAgent                 => sendMsgToEdgeAgent(smea.uid)
    case mss: MsgSentSuccessfully                 => handleMsgSentSuccessfully(mss)
    case msf: MsgSendingFailed                    => handleMsgSendingFailed(msf)
    case umds: UpdateMsgDeliveryStatus            => handleUpdateMsgDeliveryStatus(umds)
    case umet: UpdateMsgExpirationTime_MFV_0_5 if umet.targetMsgName == CREATE_MSG_TYPE_CONN_REQ
                                                  => handleUpdateMsgExpirationTime(umet)
  }

  def handleConnReqMsg(amw: AgentMsgWrapper): PackedMsg = {
    implicit val amc: AgentMsgContext = amw.getAgentMsgContext
    val crm = ConnectingMsgHelper.buildCreateMsgConnReq(amw)
    handleConnReqMsgBase(crm)
  }

  def handleConnReqAnswerMsg(amw: AgentMsgWrapper): PackedMsg = {
    implicit val amc: AgentMsgContext = amw.getAgentMsgContext
    val cram = ConnectingMsgHelper.buildCreateMsgConnReqAnswer(amw)
    handleConnReqAnswerMsgBase(cram)
  }

  def handleRedirectConnReqMsg(amw: AgentMsgWrapper): PackedMsg = {
    implicit val amc: AgentMsgContext = amw.getAgentMsgContext
    val rcrm = RedirectConnReqMsgHelper.buildReqMsg(amw)
    handleRedirectConnReqMsgBase(rcrm)
  }

  def handleConnReqRedirectedMsg(amw: AgentMsgWrapper): PackedMsg = {
    implicit val amc: AgentMsgContext = amw.getAgentMsgContext
    val cram = ConnReqRedirectedMsgHelper.buildReqMsg(amw)
    handleRedirectConnReqMsgBase(cram)
  }

  lazy val inviteDetailVersion: String = "1.0"

  override def getEncryptForDID: DID= ctx.getState.mySelfRelDIDReq
}

/**
 * Used to get invitation detail by given connection request message id
 *
 * @param uid - unique message id
 */

case class GetInviteDetail_MFV_0_5(override val uid: MsgId) extends GetInviteDetail with ActorMessageClass {
  val msgName: MsgName = MSG_TYPE_GET_INVITE_DETAIL
  val msgFamily: MsgFamily = ConnectingMsgFamily
}
