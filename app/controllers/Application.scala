package controllers

import play.api.mvc._

class Application extends Controller {

  def index = Action {
    MovedPermanently("//www.cgeo.org/status.html")
  }

}
