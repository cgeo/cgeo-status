package controllers.geoip

import java.io.File

import akka.actor.{Actor, ActorRef, Terminated}
import com.google.inject.Inject
import com.sanoma.cda.geo.Point
import com.sanoma.cda.geoip.MaxMindIpGeo
import models.BuildKind
import play.api.libs.json.Json
import play.api.{Configuration, Logger}

class GeoIPActor @Inject() (config: Configuration) extends Actor {

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

    case ClientInfo(ip, locale, kind) =>
      if (clients.nonEmpty)
        geoIP match {
          case Some(g) =>
            g.getLocation(ip).flatMap(_.geoPoint) match {
              case Some(Point(latitude, longitude)) =>
                clients.foreach(_ ! Json.obj("latitude" -> latitude, "longitude" -> longitude,
                  "locale" -> locale, "kind" -> kind.name))
              case None =>
            }
          case None =>
        }

    case Register(actorRef) =>
      clients += actorRef
      context.watch(actorRef)

    case Terminated(actorRef) =>
      clients -= actorRef
  }

}

object GeoIPActor {
  case class ClientInfo(IP: String, locale: String, kind: BuildKind)
  case class Register(actorRef: ActorRef)
  case class UseGeoIPData(file: File)
  case class RemoveGeoIPData(file: File)
}
