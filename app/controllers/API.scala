package controllers

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.google.inject.Inject
import com.google.inject.name.Named
import controllers.CounterActor._
import models._
import play.api.Configuration
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class API @Inject() (components: ControllerComponents, database: Database, status: Status,
    ec: ExecutionContext, @Named("counter-actor") counterActor: ActorRef,
    config: Configuration) extends AbstractController(components) {

  import API._

  implicit val ec_ = ec

  private[this] val API_KEY = Option(System.getenv("API_KEY")) getOrElse "apikey"
  private[this] val counterTimeout = Duration(config.getMillis("count-request-timeout"), TimeUnit.MILLISECONDS)
  private[this] val maxBatchInterval = Duration(config.getMillis("geoip.client.max-batch-interval"), TimeUnit.MILLISECONDS)
  private[this] val maxBatchSize = config.get[Int]("geoip.client.max-batch-size")

  def getStatus(version_code: Int, version_name: String, gc_membership: Option[String]) = Action { request ⇒
    val gcMembership = gc_membership.fold(GCUnknownMembership: GCMembership)(GCMembership.parse)
    val (kind, stat) = status.status(version_code, version_name, gcMembership)
    val locale = request.getQueryString("locale").getOrElse("")
    val ip = request.headers.get("X-Forwarded-For").fold(request.remoteAddress)(_.split(", ").last)
    counterActor ! User(kind, locale, None, version_name, version_code, ip, gcMembership)
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

  def countByLocaleOrLang(langOnly: Boolean) = Action.async {
    counterActor.ask(GetUserCountByLocale(langOnly))(counterTimeout).mapTo[Map[String, Long]].map(m ⇒ Ok(Json.toJson(m)))
  }

  def countByLocale = countByLocaleOrLang(false)

  def countByLang = countByLocaleOrLang(true)

  def countByGCMembership = Action.async {
    counterActor.ask(GetUserCountByGCMembership)(counterTimeout).mapTo[Map[String, Long]].map(m ⇒ Ok(Json.toJson(m)))
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
    Source.actorRef[User](50, OverflowStrategy.dropBuffer).mapMaterializedValue { actorRef ⇒
      counterActor ! CounterActor.Register(actorRef)
      actorRef
    }.groupedWithin(maxBatchSize, maxBatchInterval)
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
