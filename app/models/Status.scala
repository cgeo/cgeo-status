package models

import com.google.inject.Inject

class Status @Inject() (database: Database) {

  private val nightlyBuildRegex = """^\d\d\d\d[\.-]\d\d[\.-]\d\d-NB(\d+)?-[0-9a-f]+$""".r
  private val releaseCandidateRegex = """^\d\d\d\d[\.-]\d\d[\.-]\d\d-RC(\d+)?$""".r
  private val releaseRegex = """^\d\d\d\d[\.-]\d\d[\.-]\d\d(-\d+|[a-z])?$""".r

  def kind(versionName: String): UpToDateKind =
    if (versionName.endsWith("-legacy"))
      Legacy
    else if (releaseCandidateRegex.findFirstIn(versionName).isDefined) {
      if (database.latestVersionFor(ReleaseCandidateDeployment).exists(_.name == versionName))
        ReleaseCandidateDeployment
      else
        ReleaseCandidate
    } else if (nightlyBuildRegex.findFirstIn(versionName).isDefined)
      NightlyBuild
    else if (releaseRegex.findFirstIn(versionName).isDefined) {
      if (database.latestVersionFor(Deployment).exists(_.name == versionName))
        Deployment
      else
        Release
    } else
      DeveloperBuild

  private val newRelease = Message(
    "New release available.\nClick to install.",
    Some("status_new_release"), Some("attribute_climbing"),
    Some("https://play.google.com/store/apps/details?id=cgeo.geocaching"),
    None
  )

  private val newRC = Message(
    "New release candidate available.\nClick to install.",
    Some("status_new_rc"), Some("attribute_climbing"), Some("https://play.google.com/store/apps/details?id=cgeo.geocaching"),
    None
  )

  private val newNightly = Message(
    "New nightly build available.\nClick to install.",
    Some("status_new_nightly"), Some("attribute_climbing"), Some("http://download.cgeo.org/cgeo-nightly.apk"),
    None
  )

  def defaultMessage(versionCode: Int, versionName: String, kind: BuildKind): Option[Message] = {
    val msg = database.getMessage
    msg.flatMap { m => if (m.conditionExpr.interpret(versionCode, versionName, kind)) msg else None }
  }

  private def checkMoreRecent(versionCode: Int, versionName: String, reference: Option[Version]) =
    reference exists { ref ⇒
      versionCode < ref.code ||
        (versionCode == ref.code && versionName < ref.name)
    }

  def status(versionCode: Int, versionName: String): (BuildKind, Option[Message]) = {
    val buildKind = kind(versionName)
    lazy val defaultMessageForVersion = defaultMessage(versionCode, versionName, buildKind)
    def moreRecent(kind: UpToDateKind) =
      checkMoreRecent(versionCode, versionName, database.latestVersionFor(kind))
     buildKind match {
      case Release ⇒
        if (moreRecent(Release))
          (OldRelease, Some(newRelease))
        else
          (Release, defaultMessageForVersion)
      case Deployment ⇒
        (Deployment, defaultMessageForVersion)
      case ReleaseCandidate ⇒
        if (moreRecent(Release))
          (OldReleaseCandidate, Some(newRelease))
        else if (moreRecent(ReleaseCandidate))
          (OldReleaseCandidate, Some(newRC))
        else
          (ReleaseCandidate, defaultMessageForVersion)
      case NightlyBuild ⇒
        if (moreRecent(NightlyBuild))
          (OldNightlyBuild, Some(newNightly))
        else
          (NightlyBuild, defaultMessageForVersion)
      case Legacy ⇒
        if (moreRecent(Legacy))
          (OldLegacy, Some(newRelease))
        else
          (Legacy, defaultMessageForVersion)
      case DeveloperBuild ⇒
        (DeveloperBuild, defaultMessageForVersion)
    }
  }

}
