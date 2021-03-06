package com.evernym.verity.testkit.util.http_listener

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.evernym.verity.http.common.HttpBindUtil
import com.evernym.verity.logging.LoggingUtil.getLoggerByName
import com.evernym.verity.UrlDetail
import com.typesafe.scalalogging.Logger

trait BaseHttpListener[T] extends HttpBindUtil {

  private var allMsgs: List[T] = List.empty

  def addToMsgs(msg: T): Unit = allMsgs = allMsgs ++ List(msg)

  def getAndResetReceivedMsgs: List[T] = {
    val lic = allMsgs
    allMsgs = List.empty
    lic
  }

  def msgCount: Int = allMsgs.size

  val logger: Logger = getLoggerByName("edge-http")
  override lazy implicit val system: ActorSystem = ActorSystem("edge-json-msg", appConfig.getLoadedConfig)

  protected def listeningEndpoint: UrlDetail

  def listeningUrl: String

  def edgeRoute: Route

  def init(): Unit = {
    Http().bindAndHandle(corsHandler(edgeRoute), "localhost", listeningEndpoint.port)
  }
}
