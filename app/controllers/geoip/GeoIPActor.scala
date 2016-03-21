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

  private[this] var geoIPFile: Option[File] = None
  private[this] var geoIP: Option[MaxMindIpGeo] = None

  def receive = {

    case UseGeoIPData(file) =>
      try {
        val newGeoIP = MaxMindIpGeo(file.getAbsolutePath, config.getInt("geoip.cache-size").get)
        geoIPFile.foreach(sender ! RemoveGeoIPData(_))
        geoIP = Some(newGeoIP)
        geoIPFile = Some(file)
      } catch {
        case t: Throwable => Logger.error("cannot use geoip file", t)
      }

    case user: User =>
      val coords = geoIP.flatMap(_.getLocation(user.ip)).flatMap(_.geoPoint)
      sender ! user.copy(coords = coords)

  }

}

object GeoIPActor {
  case class UseGeoIPData(file: File)
  case class RemoveGeoIPData(file: File)
}
