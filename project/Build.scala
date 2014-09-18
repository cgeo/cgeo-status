import sbt._
import Keys._

object ApplicationBuild extends Build {

    val appName         = "cgeo-status"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      "org.mongodb" %% "casbah" % "2.7.3"
    )

    lazy val main = (project in file(".")).enablePlugins(play.PlayScala).settings(
      name := appName,
      scalaVersion := "2.11.2",
      version := appVersion,
      resolvers ++= Seq("Sonatype OSS releases" at "https://oss.sonatype.org/content/repositories/releases/",
			"Sonatype OSS snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"),
      libraryDependencies ++= appDependencies
    )

}
