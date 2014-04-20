package models

import akka.agent.Agent
import com.mongodb.casbah.Imports._
import play.api.libs.concurrent.Akka
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import com.mongodb.casbah

object Database {

  private val mongoUri =
    Option(System.getenv("MONGOLAB_URI")) getOrElse "mongodb://localhost:27017/cgeo-status"
  private val mongoClientURI = MongoClientURI(mongoUri)
  private val mongoDB = MongoClient(mongoClientURI).getDB(mongoClientURI.database.get)
  private val statusColl = mongoDB("status")

  private val buildAgents: Map[BuildKind, Agent[Option[DBObject]]] =
    BuildKind.kinds.map(key => key -> Agent(statusColl.findOne(MongoDBObject("kind" -> key.name))))(collection.breakOut)

  private val messageAgent: Agent[Option[DBObject]] =
    Agent(statusColl.findOne(MongoDBObject("kind" -> "message")))

  private def versionFor(kind: BuildKind) = {
    val obj = MongoDBObject("kind" -> kind.name)
    statusColl.findOne(obj) getOrElse obj
  }

  def updateVersionFor(kind: BuildKind, versionCode: Int, versionName: String) {
    val newVersion = versionFor(kind) + ("code" -> versionCode) + ("name" -> versionName)
    buildAgents(kind) send (_ => Some(newVersion))
    statusColl += newVersion
    // When we setup a new release, the release candidate should be cleared
    if (kind == Release)
      deleteKind(ReleaseCandidate)
  }

  def updateMessage(data: Map[String, String]) {
    val builder = MongoDBObject.newBuilder
    builder += "kind" -> "message"
    builder ++= data
    statusColl.findOne(MongoDBObject("kind" -> "message")) match {
      case Some(obj) => builder += "_id" -> obj("_id")
      case None      =>
    }
    val newMessage = builder.result()
    messageAgent send (_ => Some(newMessage))
    statusColl += newMessage
  }

  def getMessage = messageAgent()

  def deleteKind(kind: BuildKind) {
    buildAgents(kind) send (_ => None)
    statusColl -= MongoDBObject("kind" -> kind.name)
  }

  def deleteMessage() {
    messageAgent send (_ => None)
    statusColl -= MongoDBObject("kind" -> "message")
  }

  def latestVersionFor(kind: BuildKind): Option[DBObject] = buildAgents(kind)()

}
