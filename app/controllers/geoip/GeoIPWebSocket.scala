package controllers.geoip

import akka.actor.ActorRef
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import play.api.libs.json.{JsValue, Json}

class GeoIPWebSocket(geoIPActor: ActorRef) extends ActorPublisher[JsValue] {

  override def preStart() = geoIPActor ! GeoIPActor.Register(self)

  def receive = {

    case Request(_) =>

    case Cancel =>
      context.stop(self)

    case data: JsValue =>
      if (totalDemand > 0)
        onNext(data)
  }

}
