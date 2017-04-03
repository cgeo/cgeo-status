package models

import java.util.concurrent.atomic.AtomicReference

import com.google.inject.Singleton
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import slick.jdbc.PostgresProfile.api.{Database ⇒ D, _}
import slick.lifted.{TableQuery, Tag}

@Singleton
class PostgreSQLDB extends Database {

  private[this] val db = D.forConfig("databaseUrl")

  class Versions(tag: Tag) extends Table[Version](tag, "versions") {
    def name: Rep[String] = column[String]("name", O.PrimaryKey)
    def versionName = column[String]("version_name")
    def versionCode = column[Int]("version_code")
    def * = (name, versionName, versionCode).shaped <> ({
      case (name, versionName, versionCode) ⇒
        Version(BuildKind.fromName(name), versionName, versionCode)
    }, { version: Version ⇒
      Some((version.kind.name, version.name, version.code))
    })
  }
  val versions = TableQuery[Versions]

  private[this] var currentVersions: AtomicReference[Map[BuildKind, Version]] = new AtomicReference(Map())

  def loadVersions() =
    db.run(versions.result).foreach(versions ⇒ currentVersions.set(versions.map(v ⇒ v.kind → v).toMap))

  private[this] var currentMessage: AtomicReference[Option[Message]] = new AtomicReference(None)

  def loadMessage() =
    db.run(messages.result.headOption).foreach(message ⇒ currentMessage.set(message))

  class Messages(tag: Tag) extends Table[Message](tag, "messages") {
    def message = column[String]("message", O.PrimaryKey)
    def messageId = column[Option[String]]("message_id")
    def icon = column[Option[String]]("icon")
    def url = column[Option[String]]("url")
    def condition = column[Option[String]]("condition")
    def * = (message, messageId, icon, url, condition) <> ((Message.apply _).tupled, Message.unapply)
  }
  val messages = TableQuery[Messages]

  override def updateVersionFor(version: Version) = {
    db.run(versions.insertOrUpdate(version))
    currentVersions.set(currentVersions.get + (version.kind → version))
  }

  override def latestVersionFor(kind: BuildKind) = currentVersions.get.get(kind)

  override def deleteMessage() = {
    db.run(messages.delete)
    currentMessage.set(None)
  }

  override def getMessage = currentMessage.get

  override def updateMessage(message: Message) = {
    db.run(messages.delete).foreach(_ ⇒ db.run(messages += message))
    currentMessage.set(Some(message))
  }

  override def deleteKind(kind: BuildKind) = {
    db.run(versions.filter(_.name === kind.name).delete)
    currentVersions.set(currentVersions.get() - kind)
  }

  // At startup, create databases if needed and load data
  db.run(DBIO.seq(messages.schema.create, versions.schema.create)).andThen {
    case result ⇒
      Logger.info(s"Result of DB creation: $result")
      loadMessage()
      loadVersions()
  }

}
