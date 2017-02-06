package controllers

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.google.inject.Inject
import com.google.inject.name.Named
import controllers.CounterActor.{GetAllUsers, GetUserCount, GetUserCountByKind, Reset}
import controllers.geoip.GeoIPWebSocket
import models._
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.duration._

class API @Inject() (database: Database, status: Status,
    @Named("counter-actor") counterActor: ActorRef,
    config: Configuration) extends Controller {

  import API._

  private[this] val API_KEY = Option(System.getenv("API_KEY")) getOrElse "apikey"
  private[this] val counterTimeout = Duration(config.getMilliseconds("count-request-timeout").get, TimeUnit.MILLISECONDS)
  private[this] val maxBatchInterval = Duration(config.getMilliseconds("geoip.client.max-batch-interval").get, TimeUnit.MILLISECONDS)
  private[this] val maxBatchSize = config.getInt("geoip.client.max-batch-size").get

  def getStatus(version_code: Int, version_name: String) = Action { request ⇒
    val (kind, stat) = status.status(version_code, version_name)
    val locale = request.getQueryString("locale").getOrElse("")
    val ip = request.headers.get("X-Forwarded-For").fold(request.remoteAddress)(_.split(", ").last)
    counterActor ! User(kind, locale, None, version_name, version_code, ip)
    Ok(stat.fold[JsValue](Json.obj("status" → "up-to-date"))(toJson(_)))
  }

  private def checkKey(params: Map[String, Seq[String]])(body: Map[String, String] ⇒ Result) =
    if (params.get("key").contains(Seq(API_KEY)))
      body(params.collect { case (k, Seq(v)) ⇒ k → v })
    else
      Forbidden("wrong or missing key")

  def update(kind: String) = Action { request ⇒
    val params = request.body.asFormUrlEncoded.getOrElse[Map[String, Seq[String]]](Map.empty)
    checkKey(params) { params ⇒
      BuildKind.fromName.get(kind) match {
        case Some(k) ⇒
          (for (
            versionCode ← params.get("version_code");
            versionName ← params.get("version_name")
          ) yield {
            database.updateVersionFor(Version(k, versionName, versionCode.toInt))
            k match {
              case Release ⇒
                // When we setup a new release, the release candidate and deployments should be cleared
                database.deleteKind(Deployment)
                database.deleteKind(ReleaseCandidate)
                database.deleteKind(ReleaseCandidateDeployment)
              case Deployment ⇒
                // When we setup a deployment version, the release candidate and its deployment should be cleared
                database.deleteKind(ReleaseCandidate)
                database.deleteKind(ReleaseCandidateDeployment)
              case ReleaseCandidate ⇒
                // If we are creating a new release candidate because of an issue in deployment, we should clear the deployments version
                database.deleteKind(Deployment)
                database.deleteKind(ReleaseCandidateDeployment)
              case ReleaseCandidateDeployment ⇒
                // If we are creating a new release candidate deployment, we should remove the deployment version, something must be wrong
                database.deleteKind(Deployment)
              case _ ⇒
              // Nothing more to do
            }
            counterActor ! Reset
            Ok("updated")
          }) getOrElse BadRequest("invalid parameters")
        case None ⇒
          BadRequest("unknown kind")
      }
    }
  }

  def delete(kind: String, key: String) = Action {
    if (key == API_KEY)
      BuildKind.fromName.get(kind) match {
        case Some(k) ⇒
          database.deleteKind(k)
          Ok("deleted")
        case None ⇒
          BadRequest("unknown kind")
      }
    else
      Forbidden("wrong key")
  }

  def updateMessage() = Action { request ⇒
    val params = request.body.asFormUrlEncoded.getOrElse[Map[String, Seq[String]]](Map.empty)
    checkKey(params) { params ⇒
      params.get("message").ifNotEmpty match {
        case Some(message) ⇒
          val condition = params.get("condition").ifNotEmpty
          Expression.parseError(condition) match {
            case Some(error) ⇒
              BadRequest(s"Unable to parse condition (${condition.get}): $error")
            case None ⇒
              database.updateMessage(Message(message, params.get("message_id").ifNotEmpty, params.get("icon").ifNotEmpty,
                params.get("url").ifNotEmpty, condition))
              Ok("updated")
          }
        case None ⇒
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

  def message = Action { request ⇒
    Ok(database.getMessage.fold(Json.obj("status" → "no-message"))(message ⇒ Json.obj("status" → "ok", "message" → message)))
  }

  def countByKind = Action.async {
    counterActor.ask(GetUserCountByKind)(counterTimeout).mapTo[Seq[(BuildKind, Long)]].map { counters ⇒
      val significant = counters.flatMap {
        case (kind, count) ⇒
          val result = database.latestVersionFor(kind) match {
            case Some(version) if version.code != 0 ⇒
              Some(Json.obj("versionCode" → version.code, "versionName" → version.name))
            case _ if count > 0 ⇒
              Some(Json.obj())
            case _ ⇒
              None
          }
          result.map(_ ++ Json.obj("name" → kind.name, "count" → count)).map { obj ⇒
            kind.url match {
              case Some(url) ⇒ obj ++ Json.obj("url" → url)
              case None      ⇒ obj
            }
          }
      }
      Ok(JsArray(significant))
    }
  }

  def recentLocations(limit: Int, timestamp: Long) = Action.async { request ⇒
    counterActor.ask(GetAllUsers(withCoordinates = true, limit, timestamp))(counterTimeout).mapTo[List[User]].map { users ⇒
      Ok(JsArray(users.map(_.toJson)))
    }
  }

  private def locationsSource(initial: Int, timestamp: Long): Source[JsObject, ActorRef] = {
    // Start with the list of current users, then group positions together.
    // The total number of users will also be added with every message.
    // 5 batches are queued if backpressured by the websocket, then the whole
    // buffer is dropped as the client obviously cannot keep up.
    Source.actorPublisher[User](Props(new GeoIPWebSocket(counterActor)))
      .groupedWithin(maxBatchSize, maxBatchInterval)
      .prepend(Source.fromFuture(counterActor.ask(GetAllUsers(withCoordinates = true, initial, timestamp))(counterTimeout)
        .mapTo[List[User]]))
      .mapAsync(1) { g ⇒
        counterActor.ask(GetUserCount)(counterTimeout).mapTo[(Long, Long, Int)].map {
          case (active, withCoordinates, watchers) ⇒
            Json.obj("clients" → g.map(_.toJson), "active" → active, "located" → withCoordinates,
              "watchers" → watchers, "timestamp" → System.currentTimeMillis())
        }
      }
      .buffer(5, OverflowStrategy.dropBuffer)
  }

  def locations(initial: Int, timestamp: Long) = WebSocket.accept[JsValue, JsValue] { request ⇒
    Flow.fromSinkAndSource(Sink.ignore, locationsSource(initial, timestamp))
  }

}

object API {

  implicit class IfNotEmpty(s: Option[String]) {
    def ifNotEmpty: Option[String] = s.flatMap(i ⇒ if (i.nonEmpty) s else None)
  }

}