package com.evernym.verity.http.base.restricted

import akka.http.scaladsl.model.StatusCodes._
import com.evernym.verity.actor.cluster_singleton.maintenance.Status
import com.evernym.verity.http.base.EndpointHandlerBaseSpec

trait ActorStateCleanupHealthCheckSpec { this : EndpointHandlerBaseSpec =>

  def testAgentRouteFixStatus(): Unit = {
    "when sent check agent route fix status GET api" - {
      "should respond with ok" in {
        buildGetReq("/agency/internal/maintenance/actor-state-cleanup/status") ~> epRoutes ~> check {
          status shouldBe OK
          responseTo[Status]
        }
      }
    }
  }
}
