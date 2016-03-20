package controllers

import com.google.inject.AbstractModule
import controllers.geoip.{GeoIPActor, GeoIPDownloader}
import play.api.libs.concurrent.AkkaGuiceSupport

class CGeoStatusModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[CounterActor]("counter-actor")
    bindActor[GeoIPDownloader]("geoip-downloader")
    bindActor[GeoIPActor]("geoip-actor")
  }
}