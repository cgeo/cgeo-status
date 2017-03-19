package models

sealed abstract class BuildKind(val name: String, val url: Option[String], val synthesized: Boolean)

abstract class UpToDateKind(name: String, url: Option[String]) extends BuildKind(name, url, false)

object Release extends UpToDateKind("release", Some("https://play.google.com/store/apps/details?id=cgeo.geocaching"))

object Deployment extends UpToDateKind("deployment", Some("http://download.cgeo.org/cgeo-release.apk"))

object Legacy extends UpToDateKind("legacy", Some("https://play.google.com/store/apps/details?id=cgeo.geocaching"))

object ReleaseCandidate extends UpToDateKind("rc", Some("https://play.google.com/store/apps/details?id=cgeo.geocaching"))

object ReleaseCandidateDeployment extends UpToDateKind("rc-deployment", Some("http://download.cgeo.org/cgeo-RC.apk"))

object NightlyBuild extends UpToDateKind("nightly", Some("http://download.cgeo.org/cgeo-nightly.apk"))

object DeveloperBuild extends UpToDateKind("developer", Some("https://github.com/cgeo/cgeo"))

abstract class OldKind(name: String, url: Option[String]) extends BuildKind(name, url, true)

object OldRelease extends OldKind("old release", Some("https://github.com/cgeo/cgeo/releases"))

object OldLegacy extends OldKind("old legacy", Some("https://github.com/cgeo/cgeo/releases"))

object OldReleaseCandidate extends OldKind("old rc", None)

object OldNightlyBuild extends OldKind("old nightly", None)

object UnmaintainedLegacy extends UpToDateKind("unmaintained legacy", None)

object BuildKind {
  val kinds: Seq[BuildKind] = Seq(Deployment, Release, Legacy, ReleaseCandidateDeployment, ReleaseCandidate, NightlyBuild, DeveloperBuild,
    OldRelease, OldLegacy, OldReleaseCandidate, OldNightlyBuild, UnmaintainedLegacy)
  val upToDateKinds = kinds.filter(!_.synthesized)
  val fromName = upToDateKinds.map(kind ⇒ kind.name → kind).toMap

  val unmaintainedTreshold = 20150101
}
