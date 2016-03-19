package controllers.geoip

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

class GeoIPModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[GeoIPActor]("geoip-actor")
    bindActor[GeoIPDownloader]("geoip-downloader")
  }
}