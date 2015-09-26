package models

import play.api.libs.json.{Writes, Json}

case class Message(message: String, message_id: Option[String], icon: Option[String], url: Option[String])

object Message {
  implicit val writesMessage: Writes[Message] = Json.writes[Message]
}
