package controllers

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Terminated}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.google.inject.Inject
import com.google.inject.name.Named
import models.{BuildKind, Status, User}
import play.api.{Configuration, Logger}

import scala.concurrent.duration._

class CounterActor @Inject() (config: Configuration, status: Status, @Named("geoip-actor") geoIPActor: ActorRef) extends Actor {

  import CounterActor._

  private[this] implicit val dispatcher = context.system.dispatcher
  private[this] val geoIPTimeout: Timeout = Duration(config.getMilliseconds("geoip.resolution-timeout").get, TimeUnit.MILLISECONDS)
  private[this] val maxWebSockets = config.getInt("max-websockets").get
  private[this] var users: List[User] = Nil
  private[this] var clients: Set[ActorRef] = Set()
  private[this] var usersWithCoordinates: Long = 0
  private[this] var resetGeneration: Int = 0

  private[this] def trimOld() = {
    val trimTimestamp = System.currentTimeMillis() - updatePeriodMs
    val trimmed = users.takeWhile(_.timestamp < trimTimestamp)
    usersWithCoordinates -= trimmed.count(_.coords.isDefined)
    users = users.drop(trimmed.size)
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

  private[this] def refreshKind(user: User): User =
    user.copy(kind = status.status(user.versionCode, user.versionName)._1)

  def receive = {

    case user: User =>
      // Try to add GeoIP information before adding this user to the users count.
      // If the GeoIP information is not available in a reasonable time, register
      // the user with an unknown location.
      val generation = resetGeneration
      pipe(geoIPActor.ask(user)(geoIPTimeout).mapTo[User].map(WithGeoIP(_, generation)).recover {
        case t: Throwable =>
          Logger.error(s"cannot resolve geoip for ${user.ip}", t)
          WithGeoIP(user, generation)
      }).to(self)

    case WithGeoIP(user, generation) =>
      // GeoIP received (possibly empty), send it to registered websocket clients
      // if we have some.
      trimOld()
      // If the generation is different from the current one, recompute the kind
      // of version the client is using.
      val currentUser = if (generation == resetGeneration) user else refreshKind(user)
      users :+= currentUser
      if (currentUser.coords.isDefined) {
        usersWithCoordinates += 1
        clients.foreach(_ ! currentUser)
      }

    case Reset =>
      // Recompute the build kind used by the users, as the database has just been
      // updated. We increment the reset generation counter so that clients whose
      // geoip is being resolved will be requalified when they are added.
      trimOld()
      resetGeneration += 1
      users = users.map(refreshKind)

    case Register(actorRef) =>
      // Register a websocket client and watch it to remove it when the websocket is closed.
      clients += actorRef
      context.watch(actorRef)
      // If we have too many websockets alive, close the oldest one gracefully in order to
      // cycle through the requests.
      if (clients.size > maxWebSockets) {
        val toClose = clients.head
        context.unwatch(toClose)
        context.stop(toClose)
        clients = clients.tail
      }

    case Terminated(actorRef) =>
      // A websocket has been closed
      clients -= actorRef

    case GetAllUsers(withCoordinates, limit, timestamp) =>
      // Retrieve all users
      trimOld()
      val filtered = if (withCoordinates) users.filter(_.coords.isDefined) else users
      sender ! filtered.takeRight(limit).dropWhile(_.timestamp <= timestamp)

    case GetUserCount =>
      // Count users
      trimOld()
      sender ! ((users.size * factor).round, (usersWithCoordinates * factor).round)

    case GetUserCountByKind =>
      // Sort users by version kind. This could be enhanced to include more statistics,
      // for example the language used in the application.
      trimOld()
      var userCount: Map[BuildKind, Long] = BuildKind.kinds.map(_ -> 0L).toMap
      for (user <- users)
        userCount += user.kind -> (userCount(user.kind) + 1)
      val adjusted = adjust(userCount)
      sender ! BuildKind.kinds.map(k => k -> adjusted(k))

  }

}

object CounterActor {

  case class Register(actorRef: ActorRef)
  case class GetAllUsers(withCoordinates: Boolean, limit: Int, timestamp: Long)
  case object GetUserCount
  case object GetUserCountByKind
  case object Reset
  private case class WithGeoIP(user: User, generation: Int)

  // Updates from users are done every 30 minutes
  private val updatePeriodMs = 30 * 60 * 1000
}
