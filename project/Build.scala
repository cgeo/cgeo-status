import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "cgeo-status"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      "com.typesafe.akka" % "akka-agent" % "2.0.1",
      "com.mongodb.casbah" %% "casbah" % "2.1.5-1"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resolvers += "Sonatype OSS releases" at "https://oss.sonatype.org/content/repositories/releases/"
    )

}
