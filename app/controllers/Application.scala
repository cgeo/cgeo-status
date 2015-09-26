package controllers

import com.google.inject.Inject
import play.api.mvc._

import models._

class Application @Inject() (database: Database) extends Controller {

  private[this] def versionsAndTotal() = {
    val versions =
      for (version <- BuildKind.kinds;
           content <- database.latestVersionFor(version);
           url = version.url;
           users = Counters.users(version))
      yield (content, url, users)
    val total = versions.collect { case (_, _, Some(n)) => n } .sum
    (versions, total)
  }

  def index = Action {
    MovedPermanently("//www.cgeo.org/status.html")
  }

  def status = Action {
    val (versions, total) = versionsAndTotal()
    Ok(views.html.status(versions, database.getMessage, total))
  }

}
