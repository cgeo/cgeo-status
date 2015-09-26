package controllers

import com.google.inject.Inject
import play.api.mvc._
import play.api.libs.json.Json._

import models._

class API @Inject() (database: Database, status: Status) extends Controller {

  private val API_KEY = Option(System.getenv("API_KEY")) getOrElse "apikey"

  def getStatus(version_code: Int, version_name: String) = Action {
    val (kind, stat) = status.status(version_code, version_name)
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

}
