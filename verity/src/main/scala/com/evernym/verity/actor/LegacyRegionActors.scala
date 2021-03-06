package com.evernym.verity.actor

import akka.actor.Props
import com.evernym.verity.actor.agent.user.{UserAgent, UserAgentPairwise}
import com.evernym.verity.config.AppConfig
import com.evernym.verity.config.CommonConfig.{ACTOR_DISPATCHER_NAME_USER_AGENT, ACTOR_DISPATCHER_NAME_USER_AGENT_PAIRWISE}
import com.evernym.verity.config.CommonConfig.{AKKA_SHARDING_REGION_NAME_USER_AGENT, AKKA_SHARDING_REGION_NAME_USER_AGENT_PAIRWISE}

trait LegacyRegionActors extends LegacyRegionNames { this: Platform =>

  //region actor for legacy user agent actors
  createRegion(
    userAgentRegionName,            //this is the main change compared to corresponding standard region actors
    buildProp(Props(new UserAgent(agentActorContext)), Option(ACTOR_DISPATCHER_NAME_USER_AGENT)))

  //region actor for legacy user agent pairwise actors
  createRegion(
    userAgentPairwiseRegionName,    //this is the main change compared to corresponding standard region actors
    buildProp(Props(new UserAgentPairwise(agentActorContext)), Option(ACTOR_DISPATCHER_NAME_USER_AGENT_PAIRWISE)))

}

trait HasLegacyRegionNames {
  def appConfig: AppConfig
  lazy val LEGACY_USER_AGENT_REGION_ACTOR_NAME: String = appConfig.getConfigStringReq(AKKA_SHARDING_REGION_NAME_USER_AGENT)
  lazy val LEGACY_USER_AGENT_PAIRWISE_REGION_ACTOR_NAME: String = appConfig.getConfigStringReq(AKKA_SHARDING_REGION_NAME_USER_AGENT_PAIRWISE)
}


trait LegacyRegionNames extends HasShardRegionNames with HasLegacyRegionNames{
  def appConfig: AppConfig
  override lazy val userAgentRegionName: String = LEGACY_USER_AGENT_REGION_ACTOR_NAME
  override lazy val userAgentPairwiseRegionName: String = LEGACY_USER_AGENT_PAIRWISE_REGION_ACTOR_NAME
}
