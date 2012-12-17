package controllers

import play.api._
import play.api.mvc._

import models._

object Application extends Controller {

  def index = Action {
    import BuildKind._
    val versions =
      for (version <- BuildKind.kinds;
	   content <- Database.latestVersionFor(version);
	   url = version.url;
	   users = Counters.users(version))
	yield (content, url, users)
    Ok(views.html.index(versions, Database.getMessage))
  }

}
