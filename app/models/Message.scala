package models

import play.api.Logger
import play.api.libs.json.{Json, Writes}

case class Message(message: String, message_id: Option[String], icon: Option[String], url: Option[String], condition: Option[String]) {
  val conditionExpr: Expression[Boolean] = condition.fold[Expression[Boolean]](TrueExpression) { cond =>
    Expression(cond) match {
      case Expression.Success(e, _) => e
      case Expression.NoSuccess(msg, _) =>
        Logger.error(s"failure or error while parsing condition `$cond' ($message), not matching anything")
        FalseExpression
    }
  }
}

object Message {
  implicit val writesMessage: Writes[Message] = Json.writes[Message]
}
