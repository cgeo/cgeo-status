package controllers

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Terminated}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.google.inject.Inject
import com.google.inject.name.Named
import models.{BuildKind, GCMembership, Status, User}
import play.api.{Configuration, Logger}

import scala.collection.mutable.{Map ⇒ MutMap}
import scala.concurrent.duration._

class CounterActor @Inject() (config: Configuration, status: Status, @Named("geoip-actor") geoIPActor: ActorRef) extends Actor {

  import CounterActor._

  private[this] implicit val dispatcher = context.system.dispatcher
  private[this] val geoIPTimeout: Timeout = Duration(config.getMillis("geoip.resolution-timeout"), TimeUnit.MILLISECONDS)
  private[this] val maxWebSockets = config.get[Int]("max-websockets")
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
    users.headOption.fold(1.0) { oldest ⇒
      val range = System.currentTimeMillis() - oldest.timestamp
      if (range <= 0) 1.0 else updatePeriodMs.toDouble / range
    }

  private[this] def adjust[T](data: scala.collection.Map[T, Long]): Map[T, Long] = {
    val f = factor
    data.mapValues(count ⇒ (count * f).round).toMap
  }

  private[this] def refreshKind(user: User): User =
    user.copy(kind = status.status(user.versionCode, user.versionName, user.gCMembership)._1)

  def receive = {

    case user: User ⇒
      // Try to add GeoIP information before adding this user to the users count.
      // If the GeoIP information is not available in a reasonable time, register
      // the user with an unknown location.
      val generation = resetGeneration
      geoIPActor.ask(user)(geoIPTimeout).mapTo[User].map(WithGeoIP(_, generation)).recover {
        case t: Throwable ⇒
          Logger.error(s"cannot resolve geoip for ${user.ip}", t)
          WithGeoIP(user, generation)
      } pipeTo self

    case WithGeoIP(user, generation) ⇒
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

    case Reset ⇒
      // Recompute the build kind used by the users, as the database has just been
      // updated. We increment the reset generation counter so that clients whose
      // geoip is being resolved will be requalified when they are added.
      trimOld()
      resetGeneration += 1
      users = users.map(refreshKind)

    case Register(actorRef) ⇒
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

    case Terminated(actorRef) ⇒
      // A websocket has been closed
      clients -= actorRef

    case GetAllUsers(withCoordinates, limit, timestamp) ⇒
      // Retrieve all users
      trimOld()
      val filtered = if (withCoordinates) users.filter(_.coords.isDefined) else users
      sender ! filtered.takeRight(limit).dropWhile(_.timestamp <= timestamp)

    case GetUserCount ⇒
      // Count users, users with coordinates and websocket clients
      trimOld()
      sender ! ((users.size * factor).round, (usersWithCoordinates * factor).round, clients.size)

    case GetUserCountByKind ⇒
      // Sort users by version kind. This could be enhanced to include more statistics,
      // for example the language used in the application.
      trimOld()
      var userCount: Map[BuildKind, Long] = BuildKind.kinds.map(_ → 0L).toMap
      for (user ← users)
        userCount += user.kind → (userCount(user.kind) + 1)
      val adjusted = adjust(userCount)
      sender ! BuildKind.kinds.map(k ⇒ k → adjusted(k))

    case GetUserCountByLocale(langOnly) ⇒
      var userCount = MutMap[String, Long]()
      for (user ← users) {
        val locale = if (langOnly) user.locale.split("_", 2).head else user.locale
        inc(userCount, locale)
      }
      sender ! adjust(userCount)

    case GetUserCountByGCMembership ⇒
      var userCount: Map[GCMembership, Long] = GCMembership.kinds.map(_ → 0L).toMap
      for (user ← users) {
        userCount += user.gCMembership → (userCount(user.gCMembership) + 1)
      }
      val adjusted = adjust(userCount)
      sender ! GCMembership.kinds.map(k ⇒ k.name → adjusted(k)).toMap

    case GetUserCountByConnector ⇒
      var noInfo = 0L
      var withInfo = 0L
      var userCount = MutMap[String, Long]()
      for (user ← users)
        user.connectorInfo match {
          case User.NoConnectorInfo ⇒ noInfo += 1L
          case User.Connectors(connectors) ⇒
            withInfo += 1L
            if (connectors.isEmpty)
              inc(userCount, "_noconnectors")
            else
              for (connector ← connectors if !connector.startsWith("_"))
                inc(userCount, connector)
        }
      userCount += "_noinfo" → noInfo
      userCount += "_withinfo" → withInfo
      sender ! adjust(userCount)
  }

}

object CounterActor {

  case class Register(actorRef: ActorRef)
  case class GetAllUsers(withCoordinates: Boolean, limit: Int, timestamp: Long)
  case object GetUserCount
  case object GetUserCountByKind
  case class GetUserCountByLocale(langOnly: Boolean)
  case object GetUserCountByGCMembership
  case object GetUserCountByConnector
  case object Reset
  private case class WithGeoIP(user: User, generation: Int)

  def inc[K](counter: MutMap[K, Long], key: K): Unit =
    counter += key -> (counter.getOrElse(key, 0L) + 1L)

  // Updates from users are done every 30 minutes
  private val updatePeriodMs = 30 * 60 * 1000
}
