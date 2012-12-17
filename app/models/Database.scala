package models

import akka.agent.Agent
import com.mongodb.casbah.Imports._
import play.api.libs.concurrent.Akka
import play.api.Play.current

object Database {

  // The host and database fragments cannot be empty. A malformed URI will not do
  // pretty things.
  private def uriToDb(uri: String) = {
    val m = """^mongodb://(([^/:@]+):([^:/@]+)@)?([^/:]+)(:(\d+))?/(.+)$""".r.pattern.matcher(uri)
    m.find
    val mongoConn = MongoConnection(m.group(4),
				    (if (m.group(6) != null) m.group(6).toInt else 27017))
    val mongoDB = mongoConn(m.group(7))
    if (m.group(1) != null)
      mongoDB.authenticate(m.group(2), m.group(3))
    mongoDB
  }

  private val mongoUri =
    Option(System.getenv("MONGOLAB_URI")) getOrElse "mongodb://localhost:27017/cgeo-status"
  private val mongoDB = uriToDb(mongoUri)
  private val statusColl = mongoDB("status")

  private val buildAgents: Map[BuildKind, Agent[Option[DBObject]]] =
    BuildKind.kinds map { key =>
      val value = statusColl.findOne(MongoDBObject("kind" -> key.name))
      (key -> Agent(value)(Akka.system))
    } toMap

  private val messageAgent: Agent[Option[DBObject]] = {
    val value = statusColl.findOne(MongoDBObject("kind" -> "message"))
    Agent(value)(Akka.system)
  }

  private def versionFor(kind: BuildKind) = {
    val obj = MongoDBObject("kind" -> kind.name)
    statusColl.findOne(obj) getOrElse obj
  }

  def updateVersionFor(kind: BuildKind, versionCode: Int, versionName: String) {
    val newVersion = versionFor(kind) + ("code" -> versionCode) + ("name" -> versionName)
    buildAgents(kind) send (_ => Some(newVersion))
    statusColl += newVersion
  }

  def updateMessage(data: Map[String, String]) {
    val builder = MongoDBObject.newBuilder
    builder += "kind" -> "message"
    builder ++= data
    statusColl.findOne(MongoDBObject("kind" -> "message")) match {
	case Some(obj) => builder += "_id" -> obj("_id")
	case None      =>
    }
    val newMessage = builder.result
    messageAgent send (_ => Some(newMessage))
    statusColl += newMessage
  }

  def getMessage = messageAgent()

  def deleteKind(kind: BuildKind) {
    buildAgents(kind) send (_ => None)
    statusColl -= MongoDBObject("kind" -> kind.name)
  }

  def deleteMessage {
    messageAgent send (_ => None)
    statusColl -= MongoDBObject("kind" -> "message")
  }

  def latestVersionFor(kind: BuildKind): Option[DBObject] = buildAgents(kind)()

}
