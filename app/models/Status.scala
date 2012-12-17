package models

import com.mongodb.casbah.Imports._

object Status {

  private val nightlyBuildRegex = """^\d\d\d\d\.\d\d\.\d\d-NB(\d+)?-[0-9a-f]+$""".r
  private val releaseCandidateRegex = """^\d\d\d\d\.\d\d\.\d\d-RC(\d+)?-[0-9a-f]+$""".r
  private val releaseRegex = """^\d\d\d\d\.\d\d\.\d\d(-\d+)?$""".r

  import BuildKind._

  def kind(versionName: String) =
    if (releaseCandidateRegex.findFirstIn(versionName).isDefined)
      ReleaseCandidate
    else if (nightlyBuildRegex.findFirstIn(versionName).isDefined)
      NightlyBuild
    else if (releaseRegex.findFirstIn(versionName).isDefined)
      Release
    else
      DeveloperBuild

  private val newRelease =
    Some(Map("icon" -> "attribute_climbing",
	     "message" -> "New release available.\nClick to install.",
	     "message_id" -> "status_new_release",
	     "url" ->  "https://play.google.com/store/apps/details?id=cgeo.geocaching"))

  private val newRC =
    Some(Map("icon" -> "attribute_climbing",
	     "message" -> "New release candidate available.\nClick to install.",
	     "message_id" -> "status_new_rc",
	     "url" ->  "http://www.cgeo.org/cgeo-RC.apk"))

  private val newNightly =
    Some(Map("icon" -> "attribute_climbing",
	     "message" -> "New nightly build available.\nClick to install.",
	     "message_id" -> "status_new_nightly",
	     "url" ->  "http://www.cgeo.org/c-geo-nightly.apk"))

  def nothing = Database.getMessage.map(_.mapValues(_.toString).toMap)

  private def checkMoreRecent(versionCode: Int, versionName: String, reference: Option[DBObject]) =
    reference map { ref =>
      versionCode < ref.as[Int]("code") ||
	(versionCode == ref.as[Int]("code") && versionName < ref.as[String]("name"))
    } getOrElse false

  def status(versionCode: Int, versionName: String): Option[Map[String, String]] = {
    def moreRecent(kind: BuildKind) =
      checkMoreRecent(versionCode, versionName, Database.latestVersionFor(kind))
    kind(versionName) match {
	case Release =>
	  if (moreRecent(Release))
	    newRelease
	  else {
	    Counters.count(Release)
	    nothing
	  }
	case ReleaseCandidate =>
	  if (moreRecent(Release))
	    newRelease
	  else if (moreRecent(ReleaseCandidate))
	    newRC
	  else {
	    Counters.count(ReleaseCandidate)
	    nothing
	  }
	case NightlyBuild =>
	  if (moreRecent(NightlyBuild))
	    newNightly
	  else {
	    Counters.count(NightlyBuild)
	    nothing
	  }
	case DeveloperBuild =>
	  nothing
    }
  }

}
