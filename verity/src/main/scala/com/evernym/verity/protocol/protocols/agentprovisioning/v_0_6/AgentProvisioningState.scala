package com.evernym.verity.protocol.protocols.agentprovisioning.v_0_6

import com.evernym.verity.protocol.engine.{DID, Parameters, VerKey}

sealed trait State

object State {

  case class Uninitialized() extends State
  case class Initialized(parameters: Parameters) extends State
  case class RequesterPartiIdSet() extends State
  case class ProvisionerPartiIdSet() extends State
  case class AgentPairwiseKeyCreated(did: DID, verKey: VerKey) extends State
  case class AgentCreated() extends State
}