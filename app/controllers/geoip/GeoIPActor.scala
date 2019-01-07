package controllers.geoip

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

import akka.actor.{Actor, ActorRef}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import com.google.api.client.util.IOUtils
import com.google.inject.Inject
import com.google.inject.name.Named
import com.sanoma.cda.geoip.MaxMindIpGeo
import models.User
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

class GeoIPActor @Inject() (
    config: Configuration,
    ws: WSClient,
    @Named("counter-actor") counterActor: ActorRef) extends Actor {

  import GeoIPActor._

  private[this] var geoIPFile: Option[File] = None
  private[this] var geoIP: Option[MaxMindIpGeo] = None
  private[this] val tempDir = new File(config.get[String]("geoip.temporary-directory"))
  private[this] val url = config.get[String]("geoip.geolite2-url")
  private[this] val refreshDuration = Duration(config.getMillis("geoip.refresh-delay"), TimeUnit.MILLISECONDS)
  private[this] val retryDuration = Duration(config.getMillis("geoip.retry-delay"), TimeUnit.MILLISECONDS)

  private[this] implicit val fm = ActorMaterializer()
  private[this] implicit val ec = fm.executionContext

  override def preStart() =
    config.getOptional[String]("geoip.use-existing-file") match {
      case Some(fileName) ⇒
        Logger.warn("geoip.use-existing-file is defined, not downloading fresh database")
        self ! GeoIPActor.UseGeoIPData(new File(fileName))
      case None ⇒
        self ! Download
    }

  def receive = {

    case user: User ⇒
      val coords = geoIP.flatMap(_.getLocation(user.ip)).flatMap(_.geoPoint)
      sender ! user.copy(coords = coords)

    case UseGeoIPData(file) ⇒
      try {
        val newGeoIP = MaxMindIpGeo(file.getAbsolutePath, config.get[Int]("geoip.cache-size"))
        geoIPFile.foreach(removeFile)
        geoIP = Some(newGeoIP)
        geoIPFile = Some(file)
        Logger.info("switch to new GeoIP file complete")
      } catch {
        case t: Throwable ⇒ Logger.error("cannot use geoip file", t)
      }

    case Download ⇒
      Logger.info("download fresh GeoIP data")
      val response = ws.url(url).withMethod("GET").stream()
      val downloaded = response.flatMap { r ⇒
        val file = new File(tempDir, s"geoip-data-${UUID.randomUUID}")
        val compressedFile = new File(file.getAbsolutePath + ".gz")
        val compressedSink = StreamConverters.fromOutputStream(() ⇒ new FileOutputStream(compressedFile))
        r.bodyAsSource.runWith(compressedSink).andThen {
          case Success(result) ⇒
            Logger.debug("GeoIP download successful, will uncompress file")
            try {
              val compressedInput = new FileInputStream(compressedFile)
              val uncompressedInput = new GZIPInputStream(compressedInput)
              val output = new FileOutputStream(file)
              IOUtils.copy(uncompressedInput, output)
              output.close()
              uncompressedInput.close()
              compressedInput.close()
              compressedFile.delete()
              Logger.info("switching to new GeoIP data")
              self ! GeoIPActor.UseGeoIPData(file)
              context.system.scheduler.scheduleOnce(refreshDuration, self, Download)
            } catch {
              case throwable: Throwable ⇒
                Logger.error("unable to uncompress GeoIP file", throwable)
                removeFile(file)
                removeFile(compressedFile)
                context.system.scheduler.scheduleOnce(retryDuration, self, Download)
            }
          case Failure(throwable) ⇒
            Logger.error("unable to download GeoIP data, attempting to remove temporary file", throwable)
            removeFile(file)
            context.system.scheduler.scheduleOnce(retryDuration, self, Download)
        }
      }

  }

}

object GeoIPActor {

  case class UseGeoIPData(file: File)
  private case object Download

  private def removeFile(file: File) = {
    try {
      file.delete()
      Logger.debug(s"removed obsolete file $file")
    } catch {
      case t: Throwable ⇒
        Logger.warn(s"cannot remove obsolete file $file", t)
    }
  }
}
