package com.evernym.verity.actor.testkit.actor

import akka.actor.{ActorRef, ActorSystem}
import com.evernym.verity.actor.Platform
import com.evernym.verity.actor.agent.AgentActorContext
import com.evernym.verity.config.AppConfig
import com.evernym.verity.testkit.mock.agency_admin.MockAgencyAdmin
import com.evernym.verity.vault.{WalletAPI, WalletConfig}
import com.evernym.verity.UrlDetail


class MockPlatform(system: ActorSystem, appConfig: AppConfig, mockPlatformParam: MockPlatformParam)
  extends Platform(new MockAgentActorContext(system, appConfig, mockPlatformParam.mockAgentActorContextParam))

trait ProvidesMockPlatform extends MockAppConfig { tc =>

  implicit val system: ActorSystem

  def localAgencyEndpoint: String = "localhost:9000"

  lazy val platform: Platform = new MockPlatform(system, appConfig, mockPlatformParam)

  lazy val agentActorContext: AgentActorContext = platform.agentActorContext

  lazy val walletAPI: WalletAPI = platform.agentActorContext.walletAPI
  lazy val walletConfig: WalletConfig = platform.agentActorContext.walletConfig

  lazy val singletonParentProxy: ActorRef = platform.singletonParentProxy

  lazy val agentRouteStoreRegion: ActorRef = platform.agentRouteStoreRegion

  lazy val agencyAgentRegion: ActorRef = platform.agencyAgentRegion
  lazy val agencyAgentPairwiseRegion : ActorRef = platform.agencyAgentPairwiseRegion

  lazy val userAgentRegionActor: ActorRef = platform.userAgentRegion
  lazy val userAgentPairwiseRegionActor: ActorRef = platform.userAgentPairwiseRegion
  lazy val activityTrackerRegionActor: ActorRef = platform.activityTrackerRegion

  lazy val itemManagerRegionActor: ActorRef = platform.itemManagerRegion
  lazy val itemContainerRegionActor: ActorRef = platform.itemContainerRegion

  lazy val mockAgencyAdmin: MockAgencyAdmin =
    new MockAgencyAdmin(system, UrlDetail(localAgencyEndpoint), platform.agentActorContext.appConfig)

  def getTotalAgentMsgsSentByCloudAgentToRemoteAgent: Int = {
    platform.agentActorContext.remoteMsgSendingSvc.asInstanceOf[MockRemoteMsgSendingSvc].totalAgentMsgsSent
  }

  lazy val mockRouteStoreActorTypeToRegions: Map[Int, ActorRef] = Map.empty
  lazy val mockAgentActorContextParam: MockAgentActorContextParam = MockAgentActorContextParam(mockRouteStoreActorTypeToRegions)
  lazy val mockPlatformParam: MockPlatformParam = MockPlatformParam(mockAgentActorContextParam)
}

case class MockPlatformParam(mockAgentActorContextParam: MockAgentActorContextParam=MockAgentActorContextParam())