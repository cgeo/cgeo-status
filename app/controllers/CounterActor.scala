package controllers

import akka.actor.Actor
import models.{BuildKind, User}

class CounterActor extends Actor {

  import CounterActor._

  private[this] var users: List[User] = Nil

  private[this] def trimOld() = {
    val trimTimestamp = System.currentTimeMillis() - updatePeriodMs
    users = users.dropWhile(_.timestamp < trimTimestamp)
  }

  private[this] def factor: Double =
    users.headOption.fold(1.0) { oldest =>
      val range = System.currentTimeMillis() - oldest.timestamp
      if (range <= 0) 1.0 else updatePeriodMs.toDouble / range
    }

  private[this] def adjust[T](data: Map[T, Long]): Map[T, Long] = {
    val f = factor
    data.mapValues(count => (count * f).round)
  }

  def receive = {

    case user: User =>
      trimOld()
      users :+= user

    case GetAllUsers(withCoordinates, limit) =>
      trimOld()
      val filtered = if (withCoordinates) users.filter(_.coords.isDefined) else users
      sender ! limit.fold(filtered)(filtered.takeRight)

    case GetUserCount =>
      trimOld()
      sender ! (users.size * factor).round

    case GetUserCountByKind =>
      trimOld()
      var userCount: Map[BuildKind, Long] = BuildKind.kinds.map(_ -> 0L).toMap
      for (user <- users)
        userCount += user.kind -> (userCount(user.kind) + 1)
      val adjusted = adjust(userCount)
      sender ! BuildKind.kinds.map(k => k -> adjusted(k))

  }

}

object CounterActor {

  case class GetAllUsers(withCoordinates: Boolean, limit: Option[Int])
  case object GetUserCount
  case object GetUserCountByKind

  // Updates from users are done every 30 minutes
  private val updatePeriodMs = 30 * 60 * 1000
}
