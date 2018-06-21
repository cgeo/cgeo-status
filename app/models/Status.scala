package models

import com.google.inject.Inject

class Status @Inject() (database: Database) {

  private val nightlyBuildRegex = """^\d\d\d\d[\.-]\d\d[\.-]\d\d-NB(\d+)?-[0-9a-f]+$""".r
  private val releaseCandidateRegex = """^\d\d\d\d[\.-]\d\d[\.-]\d\d-RC(\d+)?$""".r
  private val releaseRegex = """^\d\d\d\d[\.-]\d\d[\.-]\d\d(-\d+|[a-z])?$""".r

  private def kind(versionCode: Int, versionName: String): UpToDateKind =
    if (versionName.endsWith("-legacy")) {
      if (versionCode < BuildKind.unmaintainedTreshold)
        UnmaintainedLegacy
      else
        Legacy
    } else if (releaseCandidateRegex.findFirstIn(versionName).isDefined) {
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
    None)

  private val newRC = Message(
    "New release candidate available.\nClick to install.",
    Some("status_new_rc"), Some("attribute_climbing"), Some("https://play.google.com/store/apps/details?id=cgeo.geocaching"),
    None)

  private val newNightly = Message(
    "New nightly build available.\nClick to install.",
    Some("status_new_nightly"), Some("attribute_climbing"), Some("http://download.cgeo.org/cgeo-nightly.apk"),
    None)

  /**
   * Get the message to return if no upgrade message is given.
   *
   * @param versionCode the version code of the checking application
   * @param versionName the version name of the checking application
   * @param kind the kind of the checking application
   * @return a pair with the message to give, and a boolean which is true when the message is a conditional
   *         one and matches the checking application
   */
  def defaultMessage(versionCode: Int, versionName: String, kind: BuildKind, gcMembership: GCMembership): (Option[Message], Boolean) = {
    val msg = database.getMessage
    val filtered = msg.flatMap { m ⇒ if (m.conditionExpr.interpret(versionCode, versionName, kind, gcMembership)) msg else None }
    (filtered, filtered.fold(false)(_.hasCondition))
  }

  private def checkMoreRecent(versionCode: Int, versionName: String, reference: Option[Version]) =
    reference exists { ref ⇒
      versionCode < ref.code ||
        (versionCode == ref.code && versionName < ref.name)
    }

  def status(versionCode: Int, versionName: String, gcMembership: GCMembership): (BuildKind, Option[Message]) = {
    val buildKind = kind(versionCode, versionName)
    val (defaultMessageForVersion, isSpecific) = defaultMessage(versionCode, versionName, buildKind, gcMembership)
    val specificMessage = if (isSpecific) defaultMessageForVersion else None
    def moreRecent(kind: UpToDateKind) =
      checkMoreRecent(versionCode, versionName, database.latestVersionFor(kind))
    buildKind match {
      case Release ⇒
        if (moreRecent(Release))
          (OldRelease, specificMessage orElse Some(newRelease))
        else
          (Release, defaultMessageForVersion)
      case Deployment ⇒
        (Deployment, defaultMessageForVersion)
      case ReleaseCandidate ⇒
        if (moreRecent(Release))
          (OldReleaseCandidate, specificMessage orElse Some(newRelease))
        else if (moreRecent(ReleaseCandidate))
          (OldReleaseCandidate, specificMessage orElse Some(newRC))
        else
          (ReleaseCandidate, defaultMessageForVersion)
      case ReleaseCandidateDeployment ⇒
        (ReleaseCandidateDeployment, defaultMessageForVersion)
      case NightlyBuild ⇒
        if (moreRecent(NightlyBuild))
          (OldNightlyBuild, specificMessage orElse Some(newNightly))
        else
          (NightlyBuild, defaultMessageForVersion)
      case Legacy ⇒
        if (moreRecent(Legacy))
          (OldLegacy, specificMessage orElse Some(newRelease))
        else
          (Legacy, defaultMessageForVersion)
      case UnmaintainedLegacy ⇒
        (UnmaintainedLegacy, defaultMessageForVersion)
      case DeveloperBuild ⇒
        (DeveloperBuild, defaultMessageForVersion)
    }
  }

}
