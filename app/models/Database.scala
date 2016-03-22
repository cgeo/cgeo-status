package models

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[PostgreSQLDB])
trait Database {

  def updateVersionFor(version: Version): Unit

  def updateMessage(message: Message): Unit

  def getMessage: Option[Message]

  def deleteKind(kind: BuildKind): Unit

  def deleteMessage(): Unit

  def latestVersionFor(kind: BuildKind): Option[Version]

}

