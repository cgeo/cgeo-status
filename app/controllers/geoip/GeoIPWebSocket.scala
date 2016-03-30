package controllers.geoip

import akka.actor.ActorRef
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import controllers.CounterActor
import models.User

class GeoIPWebSocket(counterActor: ActorRef) extends ActorPublisher[User] {

  override def preStart() = counterActor ! CounterActor.Register(self)

  def receive = {

    case Request(_) ⇒

    case Cancel ⇒
      context.stop(self)

    case user: User ⇒
      if (totalDemand > 0)
        onNext(user)
  }

}
