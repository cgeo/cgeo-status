package models

sealed trait BuildKind {
  val name: String
  val url: String
}

object Release extends BuildKind {
  val name = "release"
  val url  = "https://play.google.com/store/apps/details?id=cgeo.geocaching"
}

object Legacy extends BuildKind {
  val name = "legacy"
  val url = "https://play.google.com/store/apps/details?id=cgeo.geocaching"
}

object ReleaseCandidate extends BuildKind {
  val name = "rc"
  val url  = "http://www.cgeo.org/cgeo-RC.apk"
}

object NightlyBuild extends BuildKind {
  val name = "nightly"
  val url  = "http://www.cgeo.org/nightly.html"
}

object DeveloperBuild extends BuildKind {
  val name = "dev"
  val url  = "http://github.com/cgeo/cgeo-opensource"
}

object BuildKind {
  val kinds: Seq[BuildKind] = Seq(Release, ReleaseCandidate, NightlyBuild, DeveloperBuild, Legacy)
  val fromName = kinds.map(kind => (kind.name -> kind)).toMap
}
