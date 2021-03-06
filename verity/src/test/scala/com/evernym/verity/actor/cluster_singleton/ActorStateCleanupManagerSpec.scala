package com.evernym.verity.actor.cluster_singleton

import java.util.UUID

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.cluster.sharding.ClusterSharding
import com.evernym.verity.actor.agent.msghandler.{ActorStateCleanupStatus, CheckActorStateCleanupState, FixActorState}
import com.evernym.verity.actor.agent.msgrouter.{ActorAddressDetail, RoutingAgentUtil, SetRoute}
import com.evernym.verity.actor.cluster_singleton.maintenance.{GetStatus, Status}
import com.evernym.verity.actor.persistence.{ActorDetail, GetActorDetail}
import com.evernym.verity.actor.testkit.checks.UNSAFE_IgnoreLog
import com.evernym.verity.actor.testkit.{CommonSpecUtil, PersistentActorSpec}
import com.evernym.verity.actor.{ForIdentifier, RouteSet, ShardUtil}
import com.evernym.verity.constants.ActorNameConstants.ACTOR_TYPE_USER_AGENT_ACTOR
import com.evernym.verity.protocol.engine.DID
import com.evernym.verity.testkit.BasicSpec
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}


class ActorStateCleanupManagerSpec extends PersistentActorSpec with BasicSpec with ShardUtil with Eventually {

  //number of total route store actors
  val shardSize = 100         //total possible actors
  val totalRouteEntries = 30

  "Platform" - {
    "during launch" - {
      "should start required region and other actors" in {
        val _ = platform.agentRouteStoreRegion
        platform.singletonParentProxy
      }
    }
  }

  "AgentRouteStore" - {
    "when sent several SetRoute commands" - {
      "should store the routes successfully" taggedAs UNSAFE_IgnoreLog in {
        addRandomRoutes()
      }
    }
  }

  "ActorStateCleanupManager" - {

    "when sent GetStatus" - {
      "should respond with correct status" in {
        eventually(timeout(Span(20, Seconds)), interval(Span(4, Seconds))) {
          platform.singletonParentProxy ! ForActorStateCleanupManager(GetStatus)
          val status = expectMsgType[Status]
          status.registeredRouteStoreActorCount shouldBe shardSize
          status.totalCandidateAgentActors shouldBe totalRouteEntries
        }
      }
    }

    "after some time" - {
      "should have processed all state cleanup" taggedAs UNSAFE_IgnoreLog in {
        eventually(timeout(Span(30, Seconds)), interval(Span(4, Seconds))) {
          platform.singletonParentProxy ! ForActorStateCleanupManager(GetStatus)
          val status = expectMsgType[Status]
          status.registeredRouteStoreActorCount shouldBe shardSize
          status.processedRouteStoreActorCount shouldBe shardSize
          status.totalCandidateAgentActors shouldBe totalRouteEntries
          status.totalProcessedAgentActors shouldBe totalRouteEntries
        }
      }
    }
  }


  val entityIdsToRoutes: Map[String, Set[DID]] = (1 to totalRouteEntries).map { i =>
    val routeDID = generateDID(i.toString)
    val entityId = RoutingAgentUtil.getBucketEntityId(routeDID)
    (entityId, routeDID)
  }.groupBy(_._1).mapValues(_.map(_._2).toSet)

  def generateDID(seed: String): DID =
    CommonSpecUtil.generateNewDid(Option(UUID.nameUUIDFromBytes(seed.getBytes()).toString)).DID

  def addRandomRoutes(): Unit = {
    entityIdsToRoutes.values.map(_.size).sum shouldBe totalRouteEntries
    entityIdsToRoutes.foreach { case (entityId, routeDIDs) =>
      routeDIDs.foreach { routeDID =>
        val setRouteMsg = SetRoute(routeDID, ActorAddressDetail(DUMMY_ACTOR_TYPE_ID, routeDID))
        sendMsgToAgentRouteStore(entityId, setRouteMsg)
        expectMsgType[RouteSet]
      }
      //just to make sure previous persist events are successfully recorded
      sendMsgToAgentRouteStore(entityId, GetActorDetail)
      expectMsgType[ActorDetail].totalPersistedEvents shouldBe routeDIDs.size

      //stop the actor
      sendMsgToAgentRouteStore(entityId, PoisonPill)
    }
  }

  def sendMsgToAgentRouteStore(entityId: String, msg: Any): Unit = {
    routeStoreRegion ! ForIdentifier(entityId, msg)
  }

  lazy val routeStoreRegion: ActorRef = platform.agentRouteStoreRegion

  lazy val DUMMY_ACTOR_TYPE_ID: Int = ACTOR_TYPE_USER_AGENT_ACTOR

  override lazy val mockRouteStoreActorTypeToRegions = Map(
    ACTOR_TYPE_USER_AGENT_ACTOR -> {
      createRegion("DummyActor", DummyAgentActor.props)
      ClusterSharding(system).shardRegion("DummyActor")
    }
  )

  override def overrideConfig: Option[Config] = Option {
    ConfigFactory parseString {
      s"verity.agent.actor-state-cleanup.enabled = true"
    }
  }
}

object DummyAgentActor {
  def props: Props = Props(new DummyAgentActor)
}

class DummyAgentActor extends Actor {
  override def receive: Receive = {
    case FixActorState                      => //nothing
    case CheckActorStateCleanupState(did)   =>
      sender ! ActorStateCleanupStatus(did, routeFixed = true, threadContextMigrated = true)
  }
}