package controllers.geoip

import akka.actor.{ActorRef, Props}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.google.inject.Inject
import com.google.inject.name.Named
import controllers.CounterActor
import play.api.libs.json.JsValue
import play.api.mvc._

class GeoIP @Inject() (components: ControllerComponents, @Named("geoip-actor") geoIPActor: ActorRef) extends AbstractController(components) {

  def locations = WebSocket.accept[JsValue, JsValue] { request ⇒
    // Accumulate up to 50 late positions, then drop the whole buffer if the client cannot accomodate the rate
    val source = Source.actorRef[JsValue](50, OverflowStrategy.dropBuffer).mapMaterializedValue { actorRef ⇒
      geoIPActor ! CounterActor.Register(actorRef)
    }
    Flow.fromSinkAndSource(Sink.ignore, source)
  }
}
