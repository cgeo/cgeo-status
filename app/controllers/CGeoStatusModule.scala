package controllers

import com.google.inject.AbstractModule
import controllers.geoip.GeoIPActor
import play.api.libs.concurrent.AkkaGuiceSupport

class CGeoStatusModule extends AbstractModule with AkkaGuiceSupport {
  def configure() = {
    bindActor[CounterActor]("counter-actor")
    bindActor[GeoIPActor]("geoip-actor")
  }
}