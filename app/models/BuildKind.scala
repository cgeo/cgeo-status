package models

sealed trait BuildKind {
  val name: String
  val url: Option[String]
}

object Release extends BuildKind {
  val name = "release"
  val url  = Some("https://play.google.com/store/apps/details?id=cgeo.geocaching")
}

object Deployment extends BuildKind {
  val name = "deployment"
  val url = Some("https://cgeo.org/cgeo-release.apk")
}

object Legacy extends BuildKind {
  val name = "legacy"
  val url = Some("https://play.google.com/store/apps/details?id=cgeo.geocaching")
}

object ReleaseCandidate extends BuildKind {
  val name = "rc"
  val url  = Some("https://cgeo.org/cgeo-RC.apk")
}

object NightlyBuild extends BuildKind {
  val name = "nightly"
  val url  = Some("https://cgeo.org/nightly.html")
}

object DeveloperBuild extends BuildKind {
  val name = "developer"
  val url  = Some("https://github.com/cgeo/cgeo")
}

object Other extends BuildKind {
  val name = "other"
  val url  = None
}

object BuildKind {
  val kinds: Seq[BuildKind] = Seq(Deployment, Release, Legacy, ReleaseCandidate, NightlyBuild, DeveloperBuild, Other)
  val fromName = kinds.map(kind => kind.name -> kind).toMap
}
