package controllers

import play.api.mvc._

import models._

object Application extends Controller {

  def index = Action {
    val versions =
      for (version <- BuildKind.kinds;
           content <- Database.latestVersionFor(version);
           url = version.url;
           users = Counters.users(version))
      yield (content, url, users)
    val total = versions.collect { case (_, _, Some(n)) => n } .sum.max(1)
    Ok(views.html.index(versions, Database.getMessage, total))
  }

}
