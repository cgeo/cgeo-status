package controllers.geoip

import java.io.File

import akka.actor.{Actor, ActorRef, Terminated}
import com.google.inject.Inject
import com.google.inject.name.Named
import com.sanoma.cda.geoip.MaxMindIpGeo
import models.{BuildKind, User}
import play.api.{Configuration, Logger}

class GeoIPActor @Inject() (config: Configuration, @Named("counter-actor") counterActor: ActorRef) extends Actor {

  import GeoIPActor._

  private[this] var clients: Set[ActorRef] = Set()
  private[this] var geoIPFile: Option[File] = None
  private[this] var geoIP: Option[MaxMindIpGeo] = None

  def receive = {

    case UseGeoIPData(file) =>
      try {
        val newGeoIP = MaxMindIpGeo(file.getAbsolutePath, config.getInt("geoip.cache-size").getOrElse(1000))
        geoIPFile.foreach(sender ! RemoveGeoIPData(_))
        geoIP = Some(newGeoIP)
        geoIPFile = Some(file)
      } catch {
        case t: Throwable => Logger.error("cannot use geoip file", t)
      }

    case UserInfo(ip, locale, kind) =>
      val user = User(kind, locale, geoIP.flatMap(_.getLocation(ip)).flatMap(_.geoPoint))
      counterActor ! user
      if (user.coords.isDefined && clients.nonEmpty)
        clients.foreach(_ ! user)

    case Register(actorRef) =>
      clients += actorRef
      context.watch(actorRef)

    case Terminated(actorRef) =>
      clients -= actorRef
  }

}

object GeoIPActor {
  case class UserInfo(IP: String, locale: String, kind: BuildKind)
  case class Register(actorRef: ActorRef)
  case class UseGeoIPData(file: File)
  case class RemoveGeoIPData(file: File)
}
