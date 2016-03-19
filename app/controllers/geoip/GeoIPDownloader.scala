package controllers.geoip

import java.io._
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

import akka.actor.{Actor, ActorRef}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import com.google.api.client.util.IOUtils
import com.google.inject.Inject
import com.google.inject.name.Named
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class GeoIPDownloader @Inject() (config: Configuration, ws: WSClient, @Named("geoip-actor") geoIPActor: ActorRef) extends Actor {

  import GeoIPDownloader._

  private[this] val tempDir = new File(config.getString("geoip.temporary-directory").get)
  private[this] val url = config.getString("geoip.geolite2-url").get
  private[this] val refreshDuration = Duration(config.getMilliseconds("geoip.refresh-delay").get, TimeUnit.MILLISECONDS)
  private[this] val retryDuration = Duration(config.getMilliseconds("geoip.retry-delay").get, TimeUnit.MILLISECONDS)

  private[this] implicit val fm = ActorMaterializer()
  private[this] implicit val ec = fm.executionContext

  override def preStart() = {
    val existingFileName = config.getString("geoip.use-existing-file")
    if (existingFileName.isDefined) {
      Logger.warn("geoip.use-existing-file is defined, not running GeoIPDownloader")
      existingFileName.foreach(name => geoIPActor ! GeoIPActor.UseGeoIPData(new File(name)))
      context.stop(self)
    } else
      self ! Download
  }

  def receive = {

    case Download =>
      Logger.info("download fresh GeoIP data")
      val response = ws.url(url).withMethod("GET").stream()
      val downloaded = response.flatMap { r =>
        val file = new File(tempDir, s"geoip-data-${UUID.randomUUID}")
        val compressedFile = new File(file.getAbsolutePath + ".gz")
        val compressedSink = StreamConverters.fromOutputStream(() => new FileOutputStream(compressedFile))
        r.body.runWith(compressedSink).andThen {
          case Success(result) =>
            Logger.debug("geoip download successful, will uncompress file")
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
              geoIPActor ! GeoIPActor.UseGeoIPData(file)
              context.system.scheduler.scheduleOnce(refreshDuration, self, Download)
            } catch {
              case throwable: Throwable =>
                Logger.error("unable to uncompress GeoIP file", throwable)
                self ! GeoIPActor.RemoveGeoIPData(file)
                self ! GeoIPActor.RemoveGeoIPData(compressedFile)
                context.system.scheduler.scheduleOnce(retryDuration, self, Download)
            }
          case Failure(throwable) =>
            Logger.error("unable to download GeoIP data, attempting to remove temporary file", throwable)
            self ! GeoIPActor.RemoveGeoIPData(file)
            context.system.scheduler.scheduleOnce(retryDuration, self, Download)
        }
      }

    case GeoIPActor.RemoveGeoIPData(file) =>
      try {
        file.delete()
        Logger.debug(s"removed obsolete file $file")
      } catch {
        case t: Throwable =>
          Logger.warn(s"cannot remove obsolete file $file", t)
      }

  }
}

object GeoIPDownloader {

  private case object Download

}
