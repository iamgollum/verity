package com.evernym.verity.actor.agent.agency

import com.evernym.verity.actor.agent.MsgPackVersion.{MPV_INDY_PACK, MPV_MSG_PACK}
import com.evernym.verity.actor.agent.TypeFormat.{LEGACY_TYPE_FORMAT, STANDARD_TYPE_FORMAT}
import com.evernym.verity.actor.agent.relationship.Tags.EDGE_AGENT_KEY
import com.evernym.verity.actor.agent.relationship._
import com.evernym.verity.actor.agent.{ProtoMsgOrderDetail, ProtocolRunningInstances, ThreadContext, ThreadContextDetail}
import com.evernym.verity.actor.testkit.ActorSpec
import com.evernym.verity.protocol.protocols.agentprovisioning.v_0_7.AgentProvisioningDefinition
import com.evernym.verity.protocol.protocols.connecting.v_0_5.ConnectingProtoDef
import com.evernym.verity.testkit.BasicSpec
import com.evernym.verity.transformations.transformers.{IdentityTransformer, _}
import com.evernym.verity.transformations.transformers.v1._

class AgencyAgentStateTransformationSpec extends ActorSpec with BasicSpec {

  lazy val transformer = createPersistenceTransformerV1("enc key", new IdentityTransformer)

  "AgencyAgentState" - {

    "with new persistence transformer" - {
      "should be able to serialize/transform and deserialize/untransform successfully" in {

        val originalState = createAgencyAgentState()
        val serializedState = transformer.execute(originalState)
        val deserializedState = transformer.undo(serializedState).asInstanceOf[AgencyAgentState]

        //asserts that original State and deserialized state are equals
        originalState.agencyDID shouldBe deserializedState.agencyDID
        originalState.isEndpointSet shouldBe deserializedState.isEndpointSet
        originalState.agentWalletSeed shouldBe deserializedState.agentWalletSeed
        originalState.thisAgentKeyId shouldBe deserializedState.thisAgentKeyId

        List("pinst-id-1", "pinst-id-2").foreach { pinstId =>
          val originalStateProtoInstances = originalState.protoInstances.get
          val deserializedStateProtoInstances = deserializedState.protoInstances.get
          val originalStateThreadContext = originalState.threadContext.get
          val deserializedStateThreadContext = deserializedState.threadContext.get

          originalStateProtoInstances.instances.get(ConnectingProtoDef.toString) shouldBe
            deserializedStateProtoInstances.instances.get(ConnectingProtoDef.toString)
          originalStateProtoInstances.instances.get(AgentProvisioningDefinition.toString) shouldBe
            deserializedStateProtoInstances.instances.get(AgentProvisioningDefinition.toString)
          originalStateThreadContext.contexts.get(pinstId) shouldBe
            deserializedStateThreadContext.contexts.get(pinstId)
        }
        originalState.relationship shouldBe deserializedState.relationship
       }
    }
  }

  def createAgencyAgentState(): AgencyAgentState = {

    def relationship: Relationship = AnywiseRelationship(Option(myDidDoc))

    def myDidDoc: DidDoc = {
      DidDoc(
        "did1",
        Option(AuthorizedKeys(Seq(AuthorizedKey("key1", "", Set(EDGE_AGENT_KEY))))),
        //agency agent won't have below type of endpoints, we are just using it to test
        //if it serializes/deserializes endpoints successfully or not
        Option(Endpoints.init(Seq(RoutingServiceEndpoint("1", Seq("key1")))))
      )
    }

    def threadContext: ThreadContext = ThreadContext(
      Map(
        "pinst-id-1" ->
          ThreadContextDetail (
            "thread-id-1",
            MPV_INDY_PACK,
            STANDARD_TYPE_FORMAT,
            usesLegacyGenMsgWrapper = true,
            usesLegacyBundledMsgWrapper = true,
            protoMsgOrderDetail = Option(ProtoMsgOrderDetail(
              senderOrder = 1,
              receivedOrders = Map("participant-1" -> 2))
            )
          ),
        "pinst-id-2" ->
          ThreadContextDetail (
            "thread-id-1",
            MPV_MSG_PACK,
            LEGACY_TYPE_FORMAT,
            protoMsgOrderDetail = Option(ProtoMsgOrderDetail(
              senderOrder = 2,
              receivedOrders = Map("participant-2" -> 3))
            )
          )
      )
    )

    def protoInstances = ProtocolRunningInstances(
      Map(
        ConnectingProtoDef.msgFamily.protoRef.toString -> "pinst-id-1",
        AgentProvisioningDefinition.msgFamily.protoRef.toString -> "pinst-id-2"
      )
    )

    new AgencyAgentState()
      .withIsEndpointSet(true)
      .withAgencyDID("agency-did")
      .withThisAgentKeyId("this-agent-key-1")
      .withAgentWalletSeed("wallet-seed")
      .withThreadContext(threadContext)
      .withProtoInstances(protoInstances)
      .withRelationship(relationship)
  }

}