package controllers

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.google.inject.Inject
import com.google.inject.name.Named
import controllers.geoip.GeoIPActor.UserInfo
import controllers.geoip.GeoIPWebSocket
import CounterActor.{GetAllUsers, GetUserCountByKind}
import models._
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.duration._

class API @Inject() (database: Database, status: Status,
                     @Named("geoip-actor") geoIPActor: ActorRef,
                     @Named("counter-actor") counterActor: ActorRef,
                     config: Configuration) extends Controller {

  private[this] val API_KEY = Option(System.getenv("API_KEY")) getOrElse "apikey"
  private[this] val counterTimeout = Duration(config.getMilliseconds("count-request-timeout").get, TimeUnit.MILLISECONDS)

  private[this] def requestIP(request: Request[AnyContent]): String =
    request.headers.get("X-Forwarded-For").fold(request.remoteAddress)(_.split(", ").last)

  def getStatus(version_code: Int, version_name: String) = Action { request =>
    val (kind, stat) = status.status(version_code, version_name)
    val locale = request.getQueryString("locale").getOrElse("")
    geoIPActor ! UserInfo(requestIP(request), locale, kind)
    Counters.count(kind)
    stat map { data =>
      Ok(toJson(data))
    } getOrElse Ok(toJson(Map("status" -> "up-to-date")))
  }

  private def checkKey(params: Map[String, Seq[String]])(body: Map[String, String] => Result) =
    if (params.get("key").contains(Seq(API_KEY)))
      body(params.collect { case (k, Seq(v)) => k -> v })
    else
      Forbidden("wrong or missing key")

  def update(kind: String) = Action { request =>
    val params = request.body.asFormUrlEncoded.getOrElse[Map[String, Seq[String]]](Map.empty)
    checkKey(params) { params =>
      BuildKind.fromName.get(kind) match {
        case Some(k) =>
          (for (versionCode <- params.get("version_code");
                versionName <- params.get("version_name"))
          yield {
            database.updateVersionFor(Version(k, versionName, versionCode.toInt))
            Counters.reset(k)
            Ok("updated")
          }) getOrElse BadRequest("invalid parameters")
        case None =>
          BadRequest("unknown kind")
      }
    }
  }

  def delete(kind: String, key: String) = Action {
    if (key == API_KEY)
      BuildKind.fromName.get(kind) match {
        case Some(k) =>
          database.deleteKind(k)
          Ok("deleted")
        case None    =>
          BadRequest("unknown kind")
      }
    else
      Forbidden("wrong key")
  }

  def updateMessage() = Action { request =>
    val params = request.body.asFormUrlEncoded.getOrElse[Map[String, Seq[String]]](Map.empty)
    checkKey(params) { params =>
      params.get("message") match {
        case Some(message) =>
          database.updateMessage(Message(message, params.get("message_id"), params.get("icon"), params.get("url")))
          Ok("updated")
        case None =>
          BadRequest("invalid parameters")
      }
    }
  }

  def deleteMessage(key: String) = Action {
    if (key == API_KEY) {
      database.deleteMessage()
      Ok("deleted")
    } else
      Forbidden
  }

  def countByKind = Action.async {
    counterActor.ask(GetUserCountByKind)(counterTimeout).mapTo[Map[BuildKind, Long]].map { counters =>
      Ok(JsObject(counters.map { case (kind, count) => kind.name -> JsNumber(count) }))
    } recover {
      case t: Throwable =>
        Logger.error("cannot retrieve count by kind", t)
        InternalServerError("unable to retrieve count by kind")
    }
  }

  def recentLocations = Action.async {
    counterActor.ask(GetAllUsers)(counterTimeout).mapTo[List[User]].map { users =>
      Ok(JsArray(users.collect { case user if user.coords.isDefined => user.toJson }))
    } recover {
      case t: Throwable =>
        Logger.error("cannot retrieve recent locations", t)
        InternalServerError("unable to retrieve recent locations")
    }
  }

  private[this] val maxBatchInterval = Duration(config.getMilliseconds("geoip.client.max-batch-interval").get, TimeUnit.MILLISECONDS)
  private[this] val maxBatchSize = config.getInt("geoip.client.max-batch-size").get

  def locations = WebSocket.accept[JsValue, JsValue] { request =>
    // Group positions together, in 5 batches if backpressured, then drop the whole buffer if the client cannot accommodate the rate
    val source = Source.actorPublisher[JsValue](Props(new GeoIPWebSocket(geoIPActor)))
      .groupedWithin(maxBatchSize, maxBatchInterval)
      .map(g => Json.obj("clients" -> g))
      .buffer(5, OverflowStrategy.dropBuffer)
    Flow.fromSinkAndSource(Sink.ignore, source)
  }

}
