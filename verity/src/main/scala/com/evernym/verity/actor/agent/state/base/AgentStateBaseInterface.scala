package com.evernym.verity.actor.agent.state.base

import com.evernym.verity.actor.State
import com.evernym.verity.actor.agent.agency.SponsorRel
import com.evernym.verity.actor.agent.relationship.Tags.AGENT_KEY_TAG
import com.evernym.verity.actor.agent.relationship.{AuthorizedKeyLike, DidDoc, KeyId, Relationship}
import com.evernym.verity.actor.agent.{ConnectionStatus, ThreadContextDetail}
import com.evernym.verity.protocol.engine._

/**
 * interface for agent's common state update (for self relationship actors)
 * agent common/base classes will call below function to update agent's state
 */
trait AgentStateUpdateInterface {
  def setAgentWalletSeed(seed: String): Unit
  def setAgencyDID(did: DID): Unit
  def setSponsorRel(rel: SponsorRel): Unit
  def addThreadContextDetail(pinstId: PinstId, threadContextDetail: ThreadContextDetail): Unit
  def addPinst(protoRef: ProtoRef, pinstId: PinstId): Unit
  def addPinst(inst: (ProtoRef, PinstId)): Unit
}

/**
 * interface for agent's common state update (for pairwise actors)
 * agent common/base classes will call below function to update agent's state
 */
trait AgentPairwiseStateUpdateInterface extends AgentStateUpdateInterface {
  def setConnectionStatus(cs: ConnectionStatus): Unit
  def setConnectionStatus(cso: Option[ConnectionStatus]): Unit
  def updateRelationship(rel: Relationship): Unit
}

/**
 * interface for agent's common state
 * agent common/base classes may/will use below members for reading/querying purposes.
 *
 */
trait AgentStateInterface extends State {

  def relationshipOpt: Option[Relationship]
  def relationshipReq: Relationship = relationshipOpt.getOrElse(throw new RuntimeException("relationship not found"))

  def sponsorRel: Option[SponsorRel] = None
  def agentWalletSeed: Option[String]
  def agencyDID: Option[DID]
  def agencyDIDReq: DID = agencyDID.getOrElse(throw new RuntimeException("agency DID not available"))

  def thisAgentKeyId: Option[KeyId]

  def threadContextDetail(pinstId: PinstId): ThreadContextDetail
  def threadContextsContains(pinstId: PinstId): Boolean

  def getPinstId(protoDef: ProtoDef): Option[PinstId]

  def myDidDoc: Option[DidDoc] = relationshipOpt.flatMap(_.myDidDoc)
  def myDidDoc_! : DidDoc = myDidDoc.getOrElse(throw new RuntimeException("myDidDoc is not set yet"))
  def myDid: Option[DID] = myDidDoc.map(_.did)
  def myDid_! : DID = myDid.getOrElse(throw new RuntimeException("myDid is not set yet"))

  def theirDidDoc: Option[DidDoc] = relationshipOpt.flatMap(_.theirDidDoc)
  def theirDidDoc_! : DidDoc = theirDidDoc.getOrElse(throw new RuntimeException("theirDidDoc is not set yet"))
  def theirDid: Option[DID] = theirDidDoc.map(_.did)
  def theirDid_! : DID = theirDid.getOrElse(throw new RuntimeException("theirDid is not set yet"))

  def thisAgentAuthKey: Option[AuthorizedKeyLike] = thisAgentKeyId.flatMap(keyId =>
    relationshipOpt.flatMap(_.myDidDocAuthKeyById(keyId)))
  def thisAgentKeyDID: Option[KeyId] = thisAgentAuthKey.map(_.keyId)
  def thisAgentKeyDIDReq: DID = thisAgentKeyDID.getOrElse(throw new RuntimeException("this agent key id not found"))
  def thisAgentVerKey: Option[VerKey] = thisAgentAuthKey.filter(_.verKeyOpt.isDefined).map(_.verKey)
  def thisAgentVerKeyReq: VerKey = thisAgentVerKey.getOrElse(throw new RuntimeException("this agent ver key not found"))

  def theirAgentAuthKey: Option[AuthorizedKeyLike] = relationshipOpt.flatMap(_.theirDidDocAuthKeyByTag(AGENT_KEY_TAG))
  def theirAgentAuthKeyReq: AuthorizedKeyLike = theirAgentAuthKey.getOrElse(
    throw new RuntimeException("their agent auth key not yet set"))
  def theirAgentKeyDID: Option[DID] = theirAgentAuthKey.map(_.keyId)
  def theirAgentKeyDIDReq: DID = theirAgentKeyDID.getOrElse(throw new RuntimeException("their agent auth key not yet set"))
  def theirAgentVerKey: Option[VerKey] = theirAgentAuthKey.flatMap(_.verKeyOpt)
  def theirAgentVerKeyReq: VerKey = theirAgentVerKey.getOrElse(throw new RuntimeException("their agent ver key not yet set"))

  def myAuthVerKeys: Set[VerKey] =
    relationshipOpt.flatMap(_.myDidDoc.flatMap(_.authorizedKeys.map(_.safeVerKeys))).getOrElse(Set.empty)
  def theirAuthVerKeys: Set[VerKey] =
    relationshipOpt.flatMap(_.theirDidDoc.flatMap(_.authorizedKeys.map(_.safeVerKeys))).getOrElse(Set.empty)
  def allAuthedVerKeys: Set[VerKey] = myAuthVerKeys ++ theirAuthVerKeys

  def serializedSize: Int
}

trait AgentStatePairwiseInterface extends AgentStateInterface {
  def connectionStatus: Option[ConnectionStatus]
  def isConnectionStatusEqualTo(status: String): Boolean = connectionStatus.exists(_.answerStatusCode == status)
}