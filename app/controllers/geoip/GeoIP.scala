package controllers.geoip

import akka.actor.{ActorRef, Props}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.google.inject.Inject
import com.google.inject.name.Named
import play.api.libs.json.JsValue
import play.api.mvc.{Controller, WebSocket}

class GeoIP @Inject() (@Named("geoip-actor") geoIPActor: ActorRef) extends Controller {

  def locations = WebSocket.accept[JsValue, JsValue] { request =>
    // Accumulate up to 50 late positions, then drop the whole buffer if the client cannot accomodate the rate
    val source = Source.actorPublisher[JsValue](Props(new GeoIPWebSocket(geoIPActor))).buffer(50, OverflowStrategy.dropBuffer)
    Flow.fromSinkAndSource(Sink.ignore, source)
  }
}
