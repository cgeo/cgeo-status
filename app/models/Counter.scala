package models

import akka.agent.Agent
import play.api.libs.concurrent.Execution.Implicits._

class Counter(sampleSize: Int) {

  private[this] val times = Agent(List[Long]())

  def reset() {
    times send (_ => List())
  }

  def count() {
    times send { l =>
      (if (l.size == sampleSize) l.drop(1) else l) :+ System.currentTimeMillis
    }
  }

  def users: Option[Long] =
    times() match {
      case t if t.size == sampleSize =>
        val interClients = 1800000 * (sampleSize - 1) / (t.last - t.head).max(1)
        val withDecay = 1800000 * sampleSize / (System.currentTimeMillis - t.head).max(1)
        interClients.min(withDecay) match {
          case 0 => None
          case v => Some(v)
        }
      case _ =>
        None
    }

}
