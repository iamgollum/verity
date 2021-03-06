package com.evernym.verity.http.route_handlers.open

import akka.cluster.sharding.ClusterSharding
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, extractClientIP, extractRequest, get, handleExceptions, logRequestResult, parameters, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import com.evernym.verity.Exceptions.{BadRequestErrorException, NotImplementedErrorException}
import com.evernym.verity.Status.{AGENT_NOT_YET_CREATED, DATA_NOT_FOUND, VALIDATION_FAILED}
import com.evernym.verity.actor.agent.msghandler.outgoing.ProtocolSyncRespMsg
import com.evernym.verity.actor.agent.msgrouter.{ActorAddressDetail, GetRoute}
import com.evernym.verity.actor.resourceusagethrottling.tracking.ResourceUsageCommon
import com.evernym.verity.actor.{ActorItemDetail, ForIdentifier, GetDetail}
import com.evernym.verity.agentmsg.msgfamily.MsgFamilyUtil
import com.evernym.verity.constants.Constants.{RESOURCE_TYPE_ENDPOINT, UNKNOWN_RECIP_PARTICIPANT_ID, UNKNOWN_SENDER_PARTICIPANT_ID}
import com.evernym.verity.http.common.CustomExceptionHandler._
import com.evernym.verity.http.route_handlers.HttpRouteWithPlatform
import com.evernym.verity.protocol.actor.{ActorProtocol, MsgEnvelope}
import com.evernym.verity.protocol.engine.{DEFAULT_THREAD_ID, DID, MsgId, ProtoDef}
import com.evernym.verity.protocol.protocols.connecting.common.InviteDetail
import com.evernym.verity.protocol.protocols.connecting.v_0_5.{GetInviteDetail_MFV_0_5, ConnectingProtoDef => ConnectingProtoDef_v_0_5}
import com.evernym.verity.protocol.protocols.connecting.v_0_6.{GetInviteDetail_MFV_0_6, ConnectingProtoDef => ConnectingProtoDef_v_0_6}
import com.evernym.verity.util.Base64Util
import org.json.JSONObject

import scala.concurrent.Future

/**
 * rest api routes to get invitation via token
 */

trait GetInviteRestEndpointHandler
  extends ResourceUsageCommon { this: HttpRouteWithPlatform =>

  def getInviteMsgResponseHandler: PartialFunction[Any, ToResponseMarshallable] = {
    case invDetail: InviteDetail  => handleExpectedResponse(invDetail)
    case jsonMsg: JSONObject      => HttpResponse(StatusCodes.OK, entity=HttpEntity(ContentType(MediaTypes.`application/json`), jsonMsg.toString(2)))
    case e                        => handleUnexpectedResponse(e)
  }

  def getTokenFut(implicit token: String): Future[Any] = {
    platform.tokenToActorItemMapper ? GetDetail
  }

  def getInviteDetail(aid: ActorItemDetail): Future[Any] = {
    implicit val actorEntityId: String = aid.actorEntityId
    val protocolDefs: Set[ProtoDef] = Set(ConnectingProtoDef_v_0_5, ConnectingProtoDef_v_0_6)
    val cmd = protocolDefs.find(pd => ActorProtocol.buildTypeName(pd) == aid.regionTypeName) match {
      case Some(ConnectingProtoDef_v_0_5)   => GetInviteDetail_MFV_0_5(aid.uid)
      case Some(ConnectingProtoDef_v_0_6)   => GetInviteDetail_MFV_0_6(aid.uid)
      case _                                =>
        throw new NotImplementedErrorException("get invite detail not supported for given token")
    }
    val regionActor = ClusterSharding(platform.agentActorContext.system).shardRegion(aid.regionTypeName)
    //TODO (msg-extractor): come back here and see if values given in MsgEnvelope are correct or not?
    val pem = MsgEnvelope(cmd.typedMsg.msg, cmd.typedMsg.msgType, UNKNOWN_RECIP_PARTICIPANT_ID, UNKNOWN_SENDER_PARTICIPANT_ID,
      Option(MsgFamilyUtil.getNewMsgUniqueId), Option(DEFAULT_THREAD_ID))
    val getInviteDetailFut = regionActor ? ForIdentifier(actorEntityId, pem)
    handleGetInviteDetailFut(getInviteDetailFut)
  }

  def getInviteDetailByDIDAndUid(DID: DID, uid: MsgId): Future[Any] = {
    val gr = GetRoute(DID)
    val respFut = platform.agentActorContext.agentMsgRouter.execute(gr) flatMap {
      case Some(aa: ActorAddressDetail) =>
        implicit val actorEntityId: String = aa.address
        platform.agentPairwise ? GetInviteDetail_MFV_0_5(uid)
      case None => Future.successful(new BadRequestErrorException(AGENT_NOT_YET_CREATED.statusCode,
        Option(AGENT_NOT_YET_CREATED.statusMsg)))
      case e => Future.successful(e)
    }
    handleGetInviteDetailFut(respFut)
  }

  def handleGetInviteDetailFut(fut: Future[Any]): Future[Any] = {
    fut map {
      case ProtocolSyncRespMsg(msg: Any, _) => msg    //this is when msg is directly sent to connecting region actor
      case id: InviteDetail => id
      case e => e
    }
  }

  def getInviteDetailByToken(token: String): Future[Any] = {
    getTokenFut(token) flatMap {
      case Some(td: ActorItemDetail) => getInviteDetail(td)
      case None => Future.successful(new BadRequestErrorException(DATA_NOT_FOUND.statusCode, Option(DATA_NOT_FOUND.statusMsg)))
    }
  }

  def handleGetInviteByTokenReq(token: String)(implicit remoteAddress: RemoteAddress): Route = {
    addUserResourceUsage(clientIpAddress, RESOURCE_TYPE_ENDPOINT,
      "GET_agency_invite", None)
    complete {
      getInviteDetailByToken(token).map[ToResponseMarshallable] {
        getInviteMsgResponseHandler
      }
    }
  }

  def handleGetInviteByDIDAndUidReq(DID: DID, uid: MsgId)(implicit remoteAddress: RemoteAddress): Route = {
    addUserResourceUsage(clientIpAddress, RESOURCE_TYPE_ENDPOINT,
      "GET_agency_invite_did", None)
    complete {
      getInviteDetailByDIDAndUid(DID, uid).map[ToResponseMarshallable] {
        getInviteMsgResponseHandler
      }
    }
  }

  def handleGetInvitationAries(base64inv: String)(implicit remoteAddress: RemoteAddress): Route = {
    addUserResourceUsage(clientIpAddress, RESOURCE_TYPE_ENDPOINT,
      "GET_agency_invite_aries", None)
    complete {
        getInviteMsgResponseHandler(decodeAriesInvitation(base64inv))
    }
  }

  def decodeAriesInvitation(base64inv: String): JSONObject = {
    try {
      new JSONObject(new String(Base64Util.getBase64UrlDecoded(base64inv)))
    } catch {
      case e: Exception =>
        throw new BadRequestErrorException(VALIDATION_FAILED.statusCode, Option("Invalid payload"))
    }
  }

  protected val getInviteRoute: Route =
    handleExceptions(exceptionHandler) {
      logRequestResult("agency-service") {
        pathPrefix("agency") {
          extractRequest { implicit req: HttpRequest =>
            extractClientIP { implicit remoteAddress =>
              pathPrefix("invite") {
                (get & pathEnd) {
                  parameters('t) { token =>
                    handleGetInviteByTokenReq(token)
                  }
                } ~
                pathPrefix(Segment) { DID =>
                  (get & pathEnd) {
                    parameters('uid) { uid =>
                      handleGetInviteByDIDAndUidReq(DID, uid)
                    }
                  }
                }
              } ~
              pathPrefix("msg") {
                (get & pathEnd) {
                  parameters("c_i") { inv =>
                    handleGetInvitationAries(inv)
                  } ~
                    parameters("oob") { inv =>
                      handleGetInvitationAries(inv)
                    }
                }
              }
            }
          }
        }
      }
    }
}
