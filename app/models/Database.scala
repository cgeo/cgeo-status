package models

import com.mongodb.casbah.Imports._

object Database {

  private val mongoUri =
    Option(System.getenv("MONGOLAB_URI")) getOrElse "mongodb://localhost:27017/cgeo-status"
  private val mongoClientURI = MongoClientURI(mongoUri)
  private val mongoDB = MongoClient(mongoClientURI).getDB(mongoClientURI.database.get)
  private val statusColl = mongoDB("status")

  private var buildVersions: Map[BuildKind, DBObject] =
    BuildKind.kinds.flatMap(key => statusColl.findOne(MongoDBObject("kind" -> key.name)).map(key -> _)).toMap

  private var message: Option[DBObject] = statusColl.findOne(MongoDBObject("kind" -> "message"))

  private def versionFor(kind: BuildKind) = {
    val obj = MongoDBObject("kind" -> kind.name)
    statusColl.findOne(obj) getOrElse obj
  }

  def updateVersionFor(kind: BuildKind, versionCode: Int, versionName: String) {
    val newVersion = versionFor(kind) + ("code" -> versionCode) + ("name" -> versionName)
    buildVersions += kind -> newVersion
    statusColl += newVersion
    // When we setup a new release, the release candidate and deployment should be cleared
    if (kind == Release) {
      deleteKind(ReleaseCandidate)
      deleteKind(Deployment)
    }
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
    message = Some(newMessage)
    statusColl += newMessage
  }

  def getMessage = message

  def deleteKind(kind: BuildKind) {
    buildVersions -= kind
    statusColl -= MongoDBObject("kind" -> kind.name)
  }

  def deleteMessage() {
    message = None
    statusColl -= MongoDBObject("kind" -> "message")
  }

  def latestVersionFor(kind: BuildKind): Option[DBObject] = buildVersions.get(kind)

}
