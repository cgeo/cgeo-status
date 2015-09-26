package models

import com.google.inject.Singleton
import com.mongodb.casbah.Imports._

@Singleton
class CasbahDB extends Database {

  private val mongoUri =
    Option(System.getenv("MONGOLAB_URI")) getOrElse "mongodb://localhost:27017/cgeo-status"
  private val mongoClientURI = MongoClientURI(mongoUri)
  private val mongoDB = MongoClient(mongoClientURI).getDB(mongoClientURI.database.get)
  private val statusColl = mongoDB("status")

  private def toVersion(obj: DBObject): Version =
    Version(BuildKind.fromName(obj.getAs[String]("kind").get), obj.getAs[String]("name").get, obj.getAs[Int]("code").get)

  private def toMessage(obj: DBObject): Message =
    Message(obj.getAs[String]("message").get, obj.getAs[String]("message_id"), obj.getAs[String]("icon"), obj.getAs[String]("url"))

  private var buildVersions: Map[BuildKind, Version] =
    BuildKind.kinds.flatMap(key => statusColl.findOne(MongoDBObject("kind" -> key.name)).map(key -> toVersion(_))).toMap

  private var message: Option[Message] = statusColl.findOne(MongoDBObject("kind" -> "message")).map(toMessage)

  private def versionFor(kind: BuildKind) = {
    val obj = MongoDBObject("kind" -> kind.name)
    statusColl.findOne(obj) getOrElse obj
  }

  def updateVersionFor(version: Version) {
    buildVersions += version.kind -> version
    statusColl += versionFor(version.kind) + ("code" -> version.code) + ("name" -> version.name)
    version.kind match {
      case Release =>
        // When we setup a new release, the release candidate and deployment should be cleared
        deleteKind(ReleaseCandidate)
        deleteKind(Deployment)
      case Deployment =>
        // When we setup a deployment version, the release candidate should be cleared
        deleteKind(ReleaseCandidate)
      case ReleaseCandidate =>
        // If we are creating a new release candidate because of an issue in deployment, we should clear the deployment version
        deleteKind(Deployment)
      case _ =>
      // Nothing more to do
    }
  }

  def updateMessage(newMessage : Message): Unit = {
    val builder = DBObject.newBuilder
    builder += "kind" -> "message"
    builder += "message" -> newMessage.message
    newMessage.message_id foreach { builder += "message_id" -> _ }
    newMessage.icon foreach { builder += "icon" -> _ }
    newMessage.url foreach { builder += "url" -> _ }
    statusColl.findOne(MongoDBObject("kind" -> "message")) match {
      case Some(obj) => builder += "_id" -> obj("_id")
      case None      =>
    }
    statusColl += builder.result()
    message = Some(newMessage)
  }

  def getMessage: Option[Message] = message

  def deleteKind(kind: BuildKind): Unit = {
    buildVersions -= kind
    statusColl -= MongoDBObject("kind" -> kind.name)
  }

  def deleteMessage(): Unit = {
    message = None
    statusColl -= MongoDBObject("kind" -> "message")
  }

  private def unnamedVersion(kind: BuildKind): Version = Version(kind, "", 0)

  def latestVersionFor(kind: BuildKind): Option[Version] =
    if (BuildKind.upToDateKinds contains kind) buildVersions.get(kind) else Some(unnamedVersion(kind))

}
