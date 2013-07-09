import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "cgeo-status"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      "com.typesafe.akka" %% "akka-agent" % "2.1.2",
      "org.mongodb" %% "casbah" % "2.6.2"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      scalaVersion := "2.10.2",
      resolvers ++= Seq("Sonatype OSS releases" at "https://oss.sonatype.org/content/repositories/releases/",
			"Sonatype OSS snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")
    )

}
