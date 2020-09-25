package com.evernym.verity.http.route_handlers.restricted

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.http.scaladsl.server.Route
import com.evernym.verity.constants.Constants._
import com.evernym.verity.Status._
import com.evernym.verity.apphealth.AppStateConstants._
import com.evernym.verity.apphealth.{AppStateManager, CauseDetail, ManualUpdate, SuccessEventParam}
import com.evernym.verity.http.common.CustomExceptionHandler._
import com.evernym.verity.http.route_handlers.HttpRouteWithPlatform
import com.typesafe.config.ConfigException.{BadPath, Missing}
import com.typesafe.config.ConfigValueType._
import com.typesafe.config.{Config, ConfigRenderOptions}

import scala.collection.JavaConverters._
import scala.concurrent.Future

trait HealthCheckEndpointHandler { this: HttpRouteWithPlatform =>

  def buildConfigRenderOptions(origComments: String, comments: String,
                               formatted: String, json: String): ConfigRenderOptions = {
    ConfigRenderOptions.defaults().
      setOriginComments(origComments.toUpperCase == YES).
      setComments(comments.toUpperCase == YES).
      setFormatted(formatted.toUpperCase == YES).
      setJson(json.toUpperCase == YES)
  }

  def getProperlyRenderedConfigValue(path: String, config: Config, cro: ConfigRenderOptions): String = {
    try {
      config.getValue(path).valueType() match {
        case LIST => "[" +config.getList(path).asScala.map(e => e.render(cro)).mkString(",")+ "]"
        case OBJECT =>
          config.getConfig(path).root().asScala.map(e => e._1 + " -> " + e._2.render(cro)).mkString("\n\n")
        case NUMBER => config.getNumber(path).toString
        case BOOLEAN => config.getBoolean(path).toString
        case STRING => config.getString(path)
        case NULL => s"no config found at give path: '$path'"
      }
    } catch {
      case _ @ (_:Missing | _:BadPath) =>
        s"no config found at give path: '$path'"
    }
  }

  def buildGetConfigsResp(pathOpt: Option[String], cro: ConfigRenderOptions): String = {
    val config = platform.agentActorContext.appConfig.getLoadedConfig
    pathOpt.map { path =>
      getProperlyRenderedConfigValue(path, config, cro)
    }.getOrElse {
      config.root().asScala.map(e => e._1 + " -> " + e._2.render(cro)).mkString("\n\n")
    }
  }

  def getConfigs(origComments: String, comments: String, formatted: String,
                 json: String, pathOpt: Option[String]): Future[String] = {
    Future {
      val cro = buildConfigRenderOptions(origComments, comments, formatted, json)
      buildGetConfigsResp(pathOpt, cro)
    }
  }

  def updateAppStatus(uas: UpdateAppStatus): Future[Any] = {
    Future {
      val causeDetail = CauseDetail(APP_STATUS_UPDATE_MANUAL.statusCode, uas.reason.getOrElse("manual-update"))
      AppStateManager << SuccessEventParam(ManualUpdate(uas.newStatus), uas.context.getOrElse(CONTEXT_MANUAL_UPDATE), causeDetail)
    }
  }

  protected val healthCheckRoute: Route =
    handleExceptions(exceptionHandler) {
      logRequestResult("agency-service") {
          pathPrefix("agency" / "internal" / "health-check") {
            extractRequest { implicit req: HttpRequest =>
              extractClientIP { implicit remoteAddress =>
                checkIfInternalApiCalledFromAllowedIPAddresses(clientIpAddress)
                  path("config") {
                    (get & pathEnd) {
                      parameters('originComments ? "N", 'comments ? "N", 'formatted ? "Y", 'json ? "Y", 'path.?) {
                        (origComments, comments, formatted, json, pathOpt) =>
                          complete {
                            getConfigs(origComments, comments, formatted, json, pathOpt).map[ToResponseMarshallable] { s => s }
                          }
                      }
                    }
                  } ~
                    path("application-state") {
                      (get & pathEnd) {
                        parameters('detail.?) { detailOpt =>
                          complete {
                            if (detailOpt.map(_.toUpperCase).contains(YES)) {
                              handleExpectedResponse(AppStateManager.getDetailedAppState)
                            } else {
                              handleExpectedResponse(AppStateManager.getEvents.map(x => s"${x.state.toString}"))
                            }
                          }
                        }
                      } ~
                        (put & pathEnd & optionalEntityAs[UpdateAppStatus]) { uasOpt =>
                          complete {
                            updateAppStatus(uasOpt.getOrElse(UpdateAppStatus())).map[ToResponseMarshallable] {
                              _ => OK
                            }
                          }
                        }
                    }
              }
            }
          }
      }
    }
}



case class UpdateAppStatus(newStatus: String=STATUS_LISTENING, context: Option[String]=None, reason: Option[String]=None)
